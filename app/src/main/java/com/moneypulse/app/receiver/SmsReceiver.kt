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
                    
                    // Process the transaction in a background coroutine
                    CoroutineScope(Dispatchers.IO).launch {
                        transactionRepository.processNewTransactionSms(transaction)
                        
                        // Show notification on the main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            // Show notification to user about the detected transaction
                            NotificationHelper.showTransactionNotification(context, transaction)
                        }
                    }
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
        
        // Try to extract merchant name if present (this is a simple approach, will be enhanced)
        val merchantRegex = Regex("(?:at|to|@|in)\\s+([A-Za-z0-9\\s]+)")
        val merchantMatch = merchantRegex.find(body)
        val merchant = merchantMatch?.groupValues?.get(1)?.trim() ?: "Unknown Merchant"
        
        // For now, we'll just use the body as the full SMS
        return TransactionSms(
            sender = sender,
            body = body,
            amount = amount,
            merchantName = merchant,
            timestamp = System.currentTimeMillis()
        )
    }
} 