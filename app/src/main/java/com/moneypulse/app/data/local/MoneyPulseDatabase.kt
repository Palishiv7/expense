package com.moneypulse.app.data.local

import android.content.Context
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
    }
    
    companion object {
        private const val DATABASE_NAME = "moneypulse_db"
    }
}