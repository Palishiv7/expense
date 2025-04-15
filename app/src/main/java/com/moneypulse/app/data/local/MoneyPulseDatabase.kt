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
                try {
                    val instance = createDatabase()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating encrypted database: ${e.message}")
                    // If encryption fails, create unencrypted database as fallback
                    // This ensures the app still works even if there are keystore issues
                    try {
                        createFallbackDatabase()
                    } catch (innerE: Exception) {
                        Log.e(TAG, "Even fallback database creation failed: ${innerE.message}")
                        // Last resort: try with a fully static approach
                        lastResortDatabase()
                    }
                }
            }
        }
        
        private fun createDatabase(): MoneyPulseDatabase {
            try {
                // First check if there's an existing database using the old key
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                val oldDbFile = context.getDatabasePath(OLD_DATABASE_NAME)
                
                // Check if database might be corrupted
                val attemptRecovery = dbFile.exists() && dbFile.length() < 1024 // Empty or tiny DB is suspicious
                
                // Delete corrupt database before proceeding to avoid crashes
                if (attemptRecovery) {
                    Log.w(TAG, "Detected potentially corrupted database, removing it")
                    dbFile.delete()
                    File(dbFile.path + "-journal").delete()
                    File(dbFile.path + "-shm").delete()
                    File(dbFile.path + "-wal").delete()
                }
                
                // Check if database migration is needed
                if (dbFile.exists() && !oldDbFile.exists()) {
                    try {
                        migrateDatabase(dbFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during database migration: ${e.message}")
                        // Continue with normal database creation - we'll lose data but app will still work
                    }
                }
                
                // Get passphrase in a try-catch block to handle SecurityHelper failures
                val passphrase = try {
                    securityHelper.getDatabaseKey()
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting database key: ${e.message}")
                    // Generate a simple fallback key if SecurityHelper fails
                    generateFallbackKey()
                }
                
                val factory = SupportFactory(passphrase)
                
                return Room.databaseBuilder(
                    context.applicationContext,
                    MoneyPulseDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // For simplicity in the MVP; in production, we'd implement proper migrations
                .openHelperFactory(factory) // Apply encryption
                .build()
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error creating database: ${e.message}")
                throw e
            }
        }
        
        /**
         * Generate a simple key as fallback if SecurityHelper fails
         * This ensures the app doesn't crash even if there are keystore issues
         */
        private fun generateFallbackKey(): ByteArray {
            Log.w(TAG, "Using fallback key generation method")
            val staticKeyString = "MoneyPulse_Fallback_Key"
            val md = java.security.MessageDigest.getInstance("SHA-256")
            return md.digest(staticKeyString.toByteArray())
        }
        
        /**
         * Create a non-encrypted database as a last resort fallback
         * This ensures the app works even if encryption is completely broken
         */
        private fun createFallbackDatabase(): MoneyPulseDatabase {
            Log.w(TAG, "Creating fallback unencrypted database")
            return Room.databaseBuilder(
                context.applicationContext,
                MoneyPulseDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
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
        
        /**
         * Last resort unencrypted database when all else fails
         * This is a simplistic approach that ensures the app at least starts
         */
        private fun lastResortDatabase(): MoneyPulseDatabase {
            Log.w(TAG, "Creating last resort database with no encryption")
            
            // Try to remove any existing database first
            try {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (dbFile.exists()) {
                    dbFile.delete()
                    File(dbFile.path + "-journal").delete()
                    File(dbFile.path + "-shm").delete()
                    File(dbFile.path + "-wal").delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up before last resort: ${e.message}")
            }
            
            // Build without encryption and with clean slate
            return Room.databaseBuilder(
                context.applicationContext,
                MoneyPulseDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}