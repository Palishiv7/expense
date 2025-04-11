package com.moneypulse.app.data.repository

import android.util.Log
import com.moneypulse.app.data.local.dao.TransactionDao
import com.moneypulse.app.data.local.entity.TransactionEntity
import com.moneypulse.app.data.local.entity.TransactionType
import com.moneypulse.app.domain.model.TransactionSms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao
) : TransactionRepository {
    
    private val TAG = "TransactionRepository"
    
    override suspend fun processNewTransactionSms(transactionSms: TransactionSms) {
        Log.d(TAG, "Processing transaction: ${transactionSms.merchantName}, ₹${transactionSms.amount}")
        
        // Convert TransactionSms to TransactionEntity
        val transaction = TransactionEntity(
            amount = transactionSms.amount,
            description = "Transaction at ${transactionSms.merchantName}",
            merchantName = transactionSms.merchantName,
            category = getCategoryForMerchant(transactionSms.merchantName),
            date = Date(transactionSms.timestamp),
            type = TransactionType.EXPENSE, // Assuming SMS transactions are expenses
            smsBody = transactionSms.body,
            smsSender = transactionSms.sender
        )
        
        // Insert into database
        val id = transactionDao.insertTransaction(transaction)
        Log.d(TAG, "Transaction saved with ID: $id")
    }
    
    override fun getMonthlySpending(): Flow<Double> {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        // Set to first day of month
        calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // Set to last day of month
        calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return transactionDao.getTotalAmountByTypeAndDateRange(
            TransactionType.EXPENSE,
            startDate,
            endDate
        ).map { it ?: 0.0 }
    }
    
    override fun getRecentTransactions(limit: Int): Flow<List<TransactionSms>> {
        return transactionDao.getRecentTransactions(limit)
            .map { entities ->
                entities.map { entity ->
                    TransactionSms(
                        sender = entity.smsSender ?: "",
                        body = entity.smsBody ?: "",
                        amount = entity.amount,
                        merchantName = entity.merchantName,
                        timestamp = entity.date.time
                    )
                }
            }
    }
    
    /**
     * Simple method to assign a category based on merchant name
     * In a real app, this would use machine learning or a more sophisticated approach
     */
    private fun getCategoryForMerchant(merchantName: String): String {
        return when {
            merchantName.contains("swiggy", ignoreCase = true) ||
            merchantName.contains("zomato", ignoreCase = true) ||
            merchantName.contains("food", ignoreCase = true) -> "Food"
            
            merchantName.contains("uber", ignoreCase = true) ||
            merchantName.contains("ola", ignoreCase = true) ||
            merchantName.contains("train", ignoreCase = true) ||
            merchantName.contains("metro", ignoreCase = true) -> "Transport"
            
            merchantName.contains("netflix", ignoreCase = true) ||
            merchantName.contains("prime", ignoreCase = true) ||
            merchantName.contains("hotstar", ignoreCase = true) -> "Entertainment"
            
            merchantName.contains("amazon", ignoreCase = true) ||
            merchantName.contains("flipkart", ignoreCase = true) ||
            merchantName.contains("myntra", ignoreCase = true) -> "Shopping"
            
            else -> "Other"
        }
    }
} 