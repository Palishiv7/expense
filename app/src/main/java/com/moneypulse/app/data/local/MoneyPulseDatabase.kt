package com.moneypulse.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneypulse.app.data.local.dao.TransactionDao
import com.moneypulse.app.data.local.entity.TransactionEntity
import com.moneypulse.app.data.local.util.DateConverter
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Room database for the MoneyPulse app
 * Uses SQLCipher for encryption
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class MoneyPulseDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao
    
    companion object {
        private const val DATABASE_NAME = "moneypulse_db"
        private const val PASSPHRASE = "moneypulse_secure_key" // In production, this should be securely generated and stored
        
        @Volatile
        private var INSTANCE: MoneyPulseDatabase? = null
        
        fun getInstance(context: Context): MoneyPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = createDatabase(context)
                INSTANCE = instance
                instance
            }
        }
        
        private fun createDatabase(context: Context): MoneyPulseDatabase {
            // Set up encryption (SQLCipher)
            val passphrase = SQLiteDatabase.getBytes(PASSPHRASE.toCharArray())
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
    }
} 