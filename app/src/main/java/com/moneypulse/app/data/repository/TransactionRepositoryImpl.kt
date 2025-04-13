package com.moneypulse.app.data.repository

import android.util.Log
import com.moneypulse.app.data.local.dao.TransactionDao
import com.moneypulse.app.data.local.entity.TransactionEntity
import com.moneypulse.app.data.local.entity.TransactionType
import com.moneypulse.app.domain.model.Categories
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
     * Maps common merchants to appropriate categories
     */
    private fun getCategoryForMerchant(merchantName: String): String {
        val normalizedName = merchantName.lowercase()
        
        // Food & Restaurants
        if (normalizedName.containsAny(
            "swiggy", "zomato", "food", "pizza", "burger", "restaurant", 
            "cafe", "dhaba", "kitchen", "catering", "hotel", "eat", "dine", 
            "dominos", "mcdonald", "kfc", "subway", "biryani", "chai", "bakery"
        )) {
            return Categories.FOOD.id
        }
        
        // Transport & Travel
        if (normalizedName.containsAny(
            "uber", "ola", "rapido", "train", "metro", "railway", "irctc", 
            "bus", "transport", "travel", "ticket", "air", "flight", "cab", 
            "taxi", "makemytrip", "yatra", "redbus", "goibibo", "petrol", "diesel", "fuel"
        )) {
            return Categories.TRANSPORT.id
        }
        
        // Shopping
        if (normalizedName.containsAny(
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "tatacliq", "meesho",
            "snapdeal", "shop", "store", "mart", "bazaar", "mall", "market", 
            "clothing", "fashion", "purchase", "retail", "bigbasket", "grofer"
        )) {
            return Categories.SHOPPING.id
        }
        
        // Bills & Utilities
        if (normalizedName.containsAny(
            "bill", "recharge", "electric", "water", "gas", "utility", "phone", 
            "mobile", "broadband", "internet", "wifi", "dth", "airtel", "jio", 
            "vodafone", "vi", "bsnl", "tata", "postpaid", "prepaid", "fastag"
        )) {
            return Categories.BILLS.id
        }
        
        // Entertainment
        if (normalizedName.containsAny(
            "netflix", "prime", "hotstar", "disney", "sony", "zee", "movie", 
            "game", "play", "sport", "subscription", "premium", "theatre", 
            "show", "concert", "event", "ticket", "entertainment", "music", "spotify", "gaana"
        )) {
            return Categories.ENTERTAINMENT.id
        }
        
        // Healthcare
        if (normalizedName.containsAny(
            "hospital", "clinic", "doctor", "medical", "pharma", "medicine", 
            "health", "care", "pharmacy", "apollo", "diagnostic", "lab", 
            "test", "consultation", "dentist", "physician", "therapy", "treatment"
        )) {
            return Categories.HEALTHCARE.id
        }
        
        // Default category if no match
        return Categories.OTHER.id
    }
    
    /**
     * Helper extension function to check if a string contains any of the given keywords
     */
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
} 