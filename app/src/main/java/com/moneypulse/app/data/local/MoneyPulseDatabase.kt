package com.moneypulse.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneypulse.app.data.local.dao.TransactionDao
import com.moneypulse.app.data.local.entity.TransactionEntity
import com.moneypulse.app.data.local.util.DateConverter
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SupportFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room database for the MoneyPulse app
 * Uses SQLCipher for encryption with keys from Android Keystore
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class MoneyPulseDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao
    
    @Singleton
    class DatabaseProvider @Inject constructor(
        @ApplicationContext private val context: Context,
        private val securityHelper: SecurityHelper
    ) {
        companion object {
            private const val TAG = "DatabaseProvider"
            private const val DATABASE_NAME = "moneypulse_db"
            private const val OLD_DATABASE_NAME = "moneypulse_db_old"
        }
        
        @Volatile
        private var INSTANCE: MoneyPulseDatabase? = null
        
        fun getDatabase(): MoneyPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = createDatabase()
                INSTANCE = instance
                instance
            }
        }
        
        private fun createDatabase(): MoneyPulseDatabase {
            // First check if there's an existing database using the old key
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val oldDbFile = context.getDatabasePath(OLD_DATABASE_NAME)
            
            // Check if database migration is needed
            if (dbFile.exists() && !oldDbFile.exists()) {
                try {
                    migrateDatabase(dbFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during database migration: ${e.message}")
                    // Continue with normal database creation - we'll lose data but app will still work
                }
            }
            
            // Use SecurityHelper to get secure encryption key
            val passphrase = securityHelper.getDatabaseKey()
            val factory = SupportFactory(passphrase)
            
            return Room.databaseBuilder(
                context.applicationContext,
                MoneyPulseDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration() // For simplicity in the MVP; in production, we'd implement proper migrations
            .openHelperFactory(factory) // Apply encryption
            .build()
        }
        
        /**
         * Migrate the database from old key to new key by:
         * 1. Renaming old database
         * 2. Opening it with legacy key
         * 3. Letting Room create new database with new key
         * 4. Copying data between them
         */
        private fun migrateDatabase(dbFile: File) {
            try {
                Log.d(TAG, "Starting database migration to new secure key")
                
                // Generate old static key for backward compatibility
                val staticKeyString = "MoneyPulse_Static_Key_v1"
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val oldKey = md.digest(staticKeyString.toByteArray())
                
                // Rename the existing database file to temporary name
                val oldDbFile = context.getDatabasePath(OLD_DATABASE_NAME)
                if (dbFile.renameTo(oldDbFile)) {
                    Log.d(TAG, "Successfully renamed old database file")
                    
                    // Open the old database with the legacy key
                    val oldFactory = SupportFactory(oldKey)
                    val oldDb = Room.databaseBuilder(
                        context.applicationContext,
                        MoneyPulseDatabase::class.java,
                        OLD_DATABASE_NAME
                    )
                    .openHelperFactory(oldFactory)
                    .build()
                    
                    // Now let Room create new database with new key
                    val newKey = securityHelper.getDatabaseKey()
                    val newFactory = SupportFactory(newKey)
                    val newDb = Room.databaseBuilder(
                        context.applicationContext,
                        MoneyPulseDatabase::class.java,
                        DATABASE_NAME
                    )
                    .openHelperFactory(newFactory)
                    .build()
                    
                    // Copy all transactions from old to new DB
                    val transactions = oldDb.transactionDao().getAllTransactionsSync()
                    if (transactions.isNotEmpty()) {
                        newDb.transactionDao().insertAllTransactions(transactions)
                        Log.d(TAG, "Migrated ${transactions.size} transactions to new database")
                    }
                    
                    // Close databases
                    oldDb.close()
                    newDb.close()
                    
                    // Delete old database file
                    if (oldDbFile.exists()) {
                        oldDbFile.delete()
                        Log.d(TAG, "Deleted old database file after migration")
                    }
                } else {
                    Log.e(TAG, "Failed to rename database file for migration")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during database migration: ${e.message}")
                throw e
            }
        }
    }
}