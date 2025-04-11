package com.moneypulse.app.data.repository

import com.moneypulse.app.domain.model.TransactionSms
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for handling transaction data
 */
interface TransactionRepository {
    
    /**
     * Process a new transaction detected from SMS
     */
    suspend fun processNewTransactionSms(transactionSms: TransactionSms)
    
    /**
     * Get monthly spending summary
     */
    fun getMonthlySpending(): Flow<Double>
    
    /**
     * Get recent transactions
     */
    fun getRecentTransactions(limit: Int): Flow<List<TransactionSms>>
} 