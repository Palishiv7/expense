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
        
        // Check if this is a duplicate transaction before processing
        if (isDuplicateTransaction(transactionSms)) {
            Log.d(TAG, "Skipping duplicate transaction from: ${transactionSms.sender}")
            return
        }
        
        // Determine transaction type based on amount sign
        val transactionType = if (transactionSms.amount < 0) {
            TransactionType.EXPENSE
        } else {
            // Income transaction
            TransactionType.INCOME
        }
        
        Log.d(TAG, "Transaction type: $transactionType based on amount: ${transactionSms.amount}")
        
        // Use absolute amount value for storage
        val absoluteAmount = Math.abs(transactionSms.amount)
        
        // Convert TransactionSms to TransactionEntity
        val transaction = TransactionEntity(
            amount = absoluteAmount,
            description = "Transaction at ${transactionSms.merchantName}",
            merchantName = transactionSms.merchantName,
            category = transactionSms.category.ifEmpty { getCategoryForMerchant(transactionSms.merchantName) },
            date = Date(transactionSms.timestamp),
            type = transactionType,
            smsBody = transactionSms.body,
            smsSender = transactionSms.sender
        )
        
        // Insert into database
        val id = transactionDao.insertTransaction(transaction)
        Log.d(TAG, "Transaction saved with ID: $id, type: ${transaction.type}, amount: ${transaction.amount}")
    }
    
    /**
     * Extracts transaction reference/ID from SMS text if available
     */
    private fun extractTransactionReference(smsBody: String): String {
        // Try to extract transaction reference/ID using regex patterns
        val refPatterns = listOf(
            // Standalone "Ref NUMBER" format (common in HDFC and other banks)
            Regex("\\b(?:Ref|REF|ref)\\s+(\\d+)\\b", RegexOption.IGNORE_CASE),
            
            // Common reference formats in Indian bank SMS with better boundaries
            Regex("\\b(?:ref|reference|txn|transaction|txnid|upi|rrn)\\b(?:\\s+|\\s*[:.]?\\s*)([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            
            // Colon or similar separator followed by digits
            Regex("(?:ref|reference|txn|transaction|id|number|no|trxn)(?:\\s+|\\s*:?\\s*)([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            
            // UPI specific format
            Regex("(?:upi ref no|upi ref|upireference)\\s*(?::|is)?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            
            // Patterns with "/" character common in some banks
            Regex("(?:UPI/P2[A-Z]/)(\\d+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in refPatterns) {
            val match = pattern.find(smsBody)
            if (match != null && match.groupValues.size > 1) {
                val ref = match.groupValues[1].trim()
                // Ensure reference is a reasonable length and not a date
                if (ref.length >= 6 && !ref.matches(Regex("\\d{2}[/\\-]\\d{2}[/\\-]\\d{2,4}"))) {
                    Log.d(TAG, "Extracted reference number: $ref")
                    return ref
                }
            }
        }
        
        Log.d(TAG, "No reference number found in SMS")
        return ""
    }
    
    /**
     * Smart duplicate detection system to prevent counting the same transaction multiple times
     * while still allowing legitimate repeated transactions
     */
    private suspend fun isDuplicateTransaction(newTransaction: TransactionSms): Boolean {
        // Skip duplicate detection for manual entries and approvals
        if (newTransaction.sender == "Manual Entry" || newTransaction.sender == "Manual Approval") {
            return false
        }
        
        // Extract reference number from new transaction
        val newRef = extractTransactionReference(newTransaction.body)
        
        // Calculate a time threshold (2 minutes window for potential duplicates)
        val timeThreshold = 2 * 60 * 1000 // 2 minutes in milliseconds
        val recentTimeWindow = newTransaction.timestamp - timeThreshold
        
        // Get recent transactions from the database
        val recentTransactions = transactionDao.getRecentTransactionsByTimeWindow(recentTimeWindow)
        
        // If no recent transactions, this can't be a duplicate
        if (recentTransactions.isEmpty()) {
            return false
        }
        
        // Look for duplicates using multiple criteria
        for (existingTransaction in recentTransactions) {
            // 1. Check for exact SMS body match (definitely a duplicate)
            if (existingTransaction.smsBody == newTransaction.body) {
                Log.d(TAG, "Duplicate detected: Exact SMS body match")
                return true
            }
            
            // 2. Check for transaction reference numbers in SMS
            // This is the most reliable method for determining unique transactions
            val existingRef = extractTransactionReference(existingTransaction.smsBody ?: "")
            
            // If both transactions have reference numbers and they're different,
            // these are definitely different transactions regardless of other attributes
            if (newRef.isNotEmpty() && existingRef.isNotEmpty()) {
                if (newRef == existingRef) {
                    Log.d(TAG, "Duplicate detected: Same transaction reference number: $newRef")
                    return true
                } else {
                    // Different reference numbers = different transactions, even if other attributes match
                    Log.d(TAG, "Different reference numbers: new=$newRef vs existing=$existingRef")
                    continue
                }
            }
            
            // 3. If reference numbers weren't available or couldn't be extracted,
            // fall back to checking other attributes, but with stricter rules
            
            // Only consider as duplicate if from the SAME bank sender
            val isSameSender = existingTransaction.smsSender?.equals(newTransaction.sender, ignoreCase = true) ?: false
            
            // Check other attributes
            val isVeryRecent = newTransaction.timestamp - existingTransaction.date.time < 30 * 1000 // 30 seconds
            val isSameAmount = Math.abs(existingTransaction.amount - Math.abs(newTransaction.amount)) < 0.01
            val isSameMerchant = existingTransaction.merchantName.equals(
                newTransaction.merchantName, 
                ignoreCase = true
            )
            
            if (isVeryRecent && isSameAmount && isSameMerchant && isSameSender) {
                // This is a fallback rule that's less reliable, so log a warning
                Log.w(TAG, "Potential duplicate detected using fallback rule (no reference numbers): " +
                         "Same merchant, amount and very recent (within 30s) from same sender")
                return true
            }
        }
        
        // Not a duplicate
        return false
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
        
        // Get transactions for this month but only return total expenses (not including income)
        return transactionDao.getAllTransactionsByDateRange(startDate, endDate)
            .map { transactions ->
                var totalExpenses = 0.0
                transactions.forEach { transaction ->
                    // Only count EXPENSE transactions toward the monthly spending total
                    if (transaction.type == TransactionType.EXPENSE) {
                        totalExpenses += transaction.amount
                    }
                }
                totalExpenses
            }
    }
    
    override fun getMonthlyIncomeTransactions(): Flow<List<TransactionSms>> {
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
        
        Log.d(TAG, "Fetching income transactions from $startDate to $endDate")
        
        // Get all income transactions for the month
        return transactionDao.getTransactionsByTypeAndDateRange(TransactionType.INCOME, startDate, endDate)
            .map { entities ->
                Log.d(TAG, "Found ${entities.size} income transactions in database")
                entities.forEach { entity ->
                    Log.d(TAG, "Income entity: ${entity.merchantName}, amount: ${entity.amount}, type: ${entity.type}")
                }
                
                entities.map { entity ->
                    TransactionSms(
                        sender = entity.smsSender ?: "Manual Entry",
                        body = entity.smsBody ?: "Income transaction",
                        amount = entity.amount, // Income is positive
                        merchantName = entity.merchantName,
                        timestamp = entity.date.time,
                        category = entity.category
                    )
                }
            }
    }
    
    override fun getTotalMonthlyIncome(baseIncome: Double): Flow<Double> {
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
        
        Log.d(TAG, "Calculating total income with base income: $baseIncome")
        
        // Use existing DAO method that we know works
        return transactionDao.getTransactionsByTypeAndDateRange(TransactionType.INCOME, startDate, endDate)
            .map { incomeTransactions ->
                var transactionTotal = 0.0
                
                // Calculate sum of all income transactions
                incomeTransactions.forEach { transaction ->
                    transactionTotal += transaction.amount
                }
                
                // Add base income to transaction total
                val totalIncome = baseIncome + transactionTotal
                
                Log.d(TAG, "Income calculation: Base($baseIncome) + Transactions($transactionTotal) = Total($totalIncome)")
                totalIncome
            }
    }
    
    override fun getRecentTransactions(limit: Int): Flow<List<TransactionSms>> {
        return transactionDao.getRecentTransactions(limit)
            .map { entities ->
                entities.map { entity ->
                    // Apply sign based on transaction type
                    val signedAmount = if (entity.type == TransactionType.EXPENSE) {
                        -entity.amount
                    } else {
                        entity.amount
                    }
                    
                    TransactionSms(
                        sender = entity.smsSender ?: "",
                        body = entity.smsBody ?: "",
                        amount = signedAmount,
                        merchantName = entity.merchantName,
                        timestamp = entity.date.time,
                        category = entity.category
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