package com.moneypulse.app.di

import android.content.Context
import com.moneypulse.app.data.local.MoneyPulseDatabase
import com.moneypulse.app.data.local.dao.TransactionDao
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    
    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        transactionRepositoryImpl: TransactionRepositoryImpl
    ): TransactionRepository
    
    companion object {
        
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): MoneyPulseDatabase {
            return MoneyPulseDatabase.getInstance(context)
        }
        
        @Provides
        @Singleton
        fun provideTransactionDao(database: MoneyPulseDatabase): TransactionDao {
            return database.transactionDao()
        }
    }
} 