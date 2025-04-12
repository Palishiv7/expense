package com.moneypulse.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver that listens for incoming SMS messages
 * and processes them to detect financial transactions.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsReceiver"
        
        // Bank and UPI sender patterns to identify transaction messages
        private val BANK_SENDERS = listOf(
            "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "KOTAKB", 
            "PNBSMS", "SCISMS", "BOIIND", "INDBNK", "CANBNK",
            "CENTBK", "UCOBNK", "UNIONB", "SYNBNK", "IDBI", 
            "BOBSMS", "YESBNK", "IDBIBK", "IDFCFB", "IDFCBK"
        )
        
        // UPI apps
        private val UPI_SENDERS = listOf(
            "GPAYBN", "NBLSMS", "PAYTMB", "ATMSBI", "PHONPE", 
            "AMAZIN", "ALRTBK", "BOBTXN", "SBMSMS", "SMSIND"
        )
        
        // Common patterns to detect debit transactions
        private val DEBIT_PATTERNS = listOf(
            "debited", "purchased", "spent", "payment", "paid", "withdraw",
            "withdrawn", "debit", "sent", "transaction", "txn", "using card"
        )
        
        // OTP patterns to filter out non-transaction messages
        private val OTP_PATTERNS = listOf(
            "otp", "verified", "verification", "verify", "code", "password",
            "security code", "secure access", "authenticate", "validation"
        )
    }
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress
                val body = sms.messageBody
                
                Log.d(TAG, "SMS from: $sender, body: $body")
                
                // Check if this is a transaction SMS
                if (isTransactionSms(sender, body)) {
                    val transaction = parseTransactionDetails(sender, body)
                    
                    // Show notification to user about the detected transaction
                    // No longer automatically saving to database
                    NotificationHelper.showTransactionNotification(context, transaction)
                }
            }
        }
    }
    
    /**
     * Determines if an SMS is a transaction message by checking sender and body patterns
     */
    private fun isTransactionSms(sender: String, body: String): Boolean {
        // Check if sender is a bank or UPI service
        val isBankSender = BANK_SENDERS.any { 
            sender.contains(it, ignoreCase = true) 
        }
        
        val isUpiSender = UPI_SENDERS.any { 
            sender.contains(it, ignoreCase = true) 
        }
        
        // Check if this is likely an OTP message (which we want to ignore)
        val isOtpMessage = OTP_PATTERNS.any { 
            body.contains(it, ignoreCase = true) 
        }
        
        // Check if this contains debit-related keywords
        val isDebitMessage = DEBIT_PATTERNS.any { 
            body.contains(it, ignoreCase = true) 
        }
        
        // TESTING MODE: Accept any message with debit patterns that isn't an OTP
        // Temporarily removing the bank sender requirement
        return !isOtpMessage && isDebitMessage
        
        // PRODUCTION MODE (commented out for testing)
        // return (isBankSender || isUpiSender) && !isOtpMessage && isDebitMessage
    }
    
    /**
     * Extracts transaction details from the SMS body
     */
    private fun parseTransactionDetails(sender: String, body: String): TransactionSms {
        // Extract amount - looking for patterns like "Rs. 1,234.56" or "INR 1234.56"
        val amountRegex = Regex("(?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.?\\d*)")
        val amountMatch = amountRegex.find(body)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        
        // Enhanced merchant detection with multiple patterns and context awareness
        val merchant = extractMerchantName(body)
        
        // For now, we'll just use the body as the full SMS
        return TransactionSms(
            sender = sender,
            body = body,
            amount = amount,
            merchantName = merchant,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Advanced merchant name extraction with multiple patterns and cleanup
     */
    private fun extractMerchantName(body: String): String {
        // Common merchant patterns in different bank SMS formats
        val patterns = listOf(
            // "spent at MERCHANT" pattern
            Regex("(?:spent|txn|purchase|payment)\\s+(?:at|in|to|with|via|from)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+for|\\s+info|\\s+[\\d]|\\.|$)"),
            
            // "at MERCHANT on" pattern (common in credit card transactions)
            Regex("(?:at|in|to|with|thru)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+for|\\s+info|\\s+[\\d]|\\.|$)"),
            
            // "towards MERCHANT" pattern (common in UPI)
            Regex("(?:towards|for|to)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+info|\\s+[\\d]|\\.|$)"),
            
            // "MERCHANT-LOCATION" pattern (with info)
            Regex("(?:at|in|to)\\s+([A-Za-z0-9\\s&\\-']+?)-([A-Za-z0-9\\s]+?)(?:\\s+on|\\s+info|\\s+[\\d]|\\.|$)")
        )
        
        // Try all patterns in order
        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                // Clean up and validate before returning
                return cleanupMerchantName(extracted, body)
            }
        }
        
        // Fallback to UPI ID detection if no other patterns match
        val upiPattern = Regex("UPI-([A-Za-z0-9\\-@\\.]+)")
        val upiMatch = upiPattern.find(body)
        if (upiMatch != null) {
            return "UPI: ${upiMatch.groupValues[1]}"
        }
        
        // If all else fails, try to extract any distinctive part
        val fallbackPattern = Regex("(?:txn|Txn|TXN|Ref)[\\s:]*([A-Za-z0-9]+)")
        val fallbackMatch = fallbackPattern.find(body)
        if (fallbackMatch != null) {
            return "Transaction ${fallbackMatch.groupValues[1]}"
        }
        
        return "Unknown Merchant"
    }
    
    /**
     * Clean up and validate merchant names, removing common non-merchant words
     */
    private fun cleanupMerchantName(merchant: String, originalBody: String): String {
        // Words that are commonly part of transaction messages but aren't merchant names
        val blacklistWords = listOf(
            "info", "transaction", "credited", "debited", "paid", "card", "bank",
            "account", "payment", "transfer", "avl bal", "avl", "bal", "balance",
            "reward", "points", "credit", "debit", "upi", "ref", "reference", "txn", 
            "block", "not", "call"
        )
        
        // Transform to proper case and remove trailing punctuation
        var cleaned = merchant.trim().split(" ")
            .filter { it.length > 1 } // Remove single characters
            .joinToString(" ") { 
                it.lowercase().replaceFirstChar { c -> c.uppercase() } 
            }
            .replace(Regex("[,\\.;:\\-_!]*$"), "")
        
        // Remove blacklisted words
        for (word in blacklistWords) {
            cleaned = cleaned.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), "")
        }
        
        // Clean up extra spaces
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        
        // If we've eliminated too much, fall back to a simple approach
        if (cleaned.length < 3) {
            // Try extracting capitalized words as a last resort
            val capitalizedPattern = Regex("\\b[A-Z]{2,}\\b")
            val capitalMatches = capitalizedPattern.findAll(originalBody)
            if (capitalMatches.count() > 0) {
                val capitalWords = capitalMatches.map { it.value }
                    .filter { it !in listOf("UPI", "INR", "SMS", "RS", "VPA", "A/C", "NEFT", "IMPS") }
                    .toList()
                
                if (capitalWords.isNotEmpty()) {
                    return capitalWords.first()
                }
            }
            
            return "Unknown Merchant"
        }
        
        return cleaned
    }
} 