package com.moneypulse.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.util.NotificationHelper
import com.moneypulse.app.util.PreferenceHelper
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
        
        // Log capture for debugging in production
        private val debugLogs = mutableListOf<String>()
        private const val MAX_DEBUG_LOGS = 50
        
        // Add method to retrieve logs
        fun getDebugLogs(): List<String> {
            return debugLogs.toList()
        }
        
        // Add method to clear logs
        fun clearDebugLogs() {
            debugLogs.clear()
        }
        
        // Capture log function
        private fun captureLog(message: String) {
            // Add timestamp
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val logEntry = "[$timestamp] $message"
            
            // Add to debug logs with limit
            synchronized(debugLogs) {
                debugLogs.add(logEntry)
                // Keep only the last MAX_DEBUG_LOGS entries
                if (debugLogs.size > MAX_DEBUG_LOGS) {
                    debugLogs.removeAt(0)
                }
            }
        }
        
        // Comprehensive list of official bank sender IDs - focus only on official banks
        private val BANK_SENDERS = listOf(
            // Major Private Banks
            "HDFCBK", "HDFC", "HDFCBN", "HDFCC", "HDFCNB", "HDFCSN", "HDFCSC", "iHDFC",
            "HSBCBK", "ICICIB", "ICICI", "AXISBK", "AXIS", "AXISBK", "AXISC", "KOTAKB", "KOTAK", 
            "KMBL", "KMBLCC", "KMBLAC", "KTKBNK", "KOTAKM", "KMOBILE", "KMBSMS", "KMBINB", 
            "YESBNK", "YESBK", "IDBI", "INDUSB", "RBLBNK", "RBL", "DBSBNK", "DBS", "FEDBNK", 
            "FB", "CITI", "SCBNK", "SCBANK", "KVBANK", "KVBBNK", "TMBANK", "TMBL", "CNRBNK",
            
            // Additional Kotak Bank sender IDs
            "KOTAKB", "KOTAK", "KMBL", "KMBKIN", "KOTAKMB", "KBLBNK", "KBINFO", "KTKUPI",
            "KOTUPI", "KTKSMS", "KTSMS", "KBSMS", "KMSMS", "KMBPAY", "KOTKPAY", "KMBLUPI",
            "KVBSMS", "SMSBKS", "KMB", "KBANKS", "KMBATM", "KBNK", "KBANK", "KTKCTSP",
            
            // Major Public Sector Banks
            "SBIMSG", "SBIINB", "SBI", "BOIIND", "BOI", "BARODM", "BOBIBN", "BOB", "PNBSMS", 
            "PNB", "CNRBNK", "CANBNK", "CBI", "CBSBNK", "UNIONB", "UBI", "IOBINB", "IOB", 
            "SYNBNK", "SYNDBNK", "CORPBNK", "ANDBKK", "ALLBNK", "PSBANK", "PSB", "IDBIBNK", 
            
            // Small Finance & Payment Banks
            "AUSFNB", "EQUBNK", "ESFBNK", "JSFBNK", "UCOBNK", "UCO", "UJVNFD", "PMCBNK", 
            "ABBANK", "AIRBNK", "JIOBNK", "FINOBNK", "NPSTPB", "NSDLPB",
            
            // Regional Rural Banks
            "BGGBBN", "COSBNK", "DCBBNK", "HARYBNK", "KARUBNK", "KJSBNK", "ORICOB", "SAPBNK",
            
            // Common Alert Identifiers
            "ALERTSVR", "ALERT", "BANKALRT", "BANKSMS", "ACTALRT", "TXNALRT", "ATMALERT", 
            "SMSBKNG", "ACCTID", "TRNSMS", "BANKUPD", "NETBNK", "USSDBNK", "IMPSALRT", 
            "AUTPAY", "CARDTRN", "DEBTALRT", "ALRTBK", "BOBTXN", "IMDBNK"
        )
        
        // UPI apps and payment services - expanded to include more services
        private val UPI_SENDERS = listOf(
            // Indian UPI/Payment Services
            "GPAYBN", "NBLSMS", "PAYTMB", "ATMSBI", "PHONPE", "AMAZONP", "ALRTBK", "BOBTXN", 
            "SBMSMS", "SMSIND", "MOBIKW", "SLICEIN", "CRED", "FREECHARGE", "BHIMUP", "YESBNK",
            "LAZYPAY", "SIMPL", "BAJAJF", "MOBKWK", "ICICI", "AIRTL", "JUPITM", "PAYZPP", 
            
            // International Payment Services
            "PAYPAL", "SQUARE", "VENMO", "ZELLE", "CASHAPP", "WMONEY", "STRIPE", "APPLE", 
            "GOOGLEP", "ALIPAY", "WECHAT", "KLARNA", "SHOPIFY", "REVOLUT", "N26", "MONZO",
            "WISE", "LYDIA", "TWINT"
        )
        
        // Comprehensive patterns to detect debit transactions - expanded based on real messages
        private val DEBIT_PATTERNS = listOf(
            // Direct debit terms
            "debited", "debit", "dr", "deducted", "withdrawn", "withdraw",
            
            // Transfer terms
            "sent", "paid", "payment", "transfer to", "transferred", "sent to", "sent from",
            
            // Purchase terms
            "purchased", "spent", "using card", "purchase", "shopping", "spent",
            
            // Bill/Fee terms
            "bill amount", "bill", "charge", "charged", "fee",
            
            // Order terms
            "order", "subscription", "membership",
            
            // Transaction terms
            "transaction", "txn", "thru upi", "via upi", "upi ref", "through", "to vpa",
            
            // From account indicators
            "from a/c", "from ac", "from acct", "from bank a/c", "from account", "from sav ac",
            
            // General terms that appear in transaction SMS
            "ref no", "avl bal", "available balance", "bal", "balance", "info"
        )
        
        // Enhanced OTP patterns to filter out non-transaction messages
        private val OTP_PATTERNS = listOf(
            "otp", "one time password", "verified", "verification", "verify", "code", "password", "passcode",
            "security code", "secure access", "authenticate", "validation", "confirm", "confirmation",
            "login", "login attempt", "sign-in", "signing in", "authorization", "authorize",
            "access code", "pin", "secret code", "token", "secure code", "2fa", "two-factor",
            "two factor", "authentication required", "security alert", "verification required",
            "identity verification", "validate", "validation code", "confirm your"
        )
        
        // Promotional and non-transaction patterns to filter out
        private val PROMOTIONAL_PATTERNS = listOf(
            "offer", "discount", "cashback", "reward", "coupon", "promo", "sale", "% off", "percent off",
            "limited time", "exclusive deal", "special offer", "congratulations", "credit card offer",
            "loan offer", "pre-approved", "pre approved", "upgrade", "invite", "refer", "bonus",
            "save now", "expires soon", "hurry", "last day", "only today", "insurance plan",
            "investment plan", "scheme", "lucky draw", "win", "free", "gift", "get extra", "get flat",
            "get upto", "installment", "emi option", "pay later"
        )
        
        // Balance check and non-transaction account patterns
        private val BALANCE_PATTERNS = listOf(
            "available balance", "avl bal", "avl. bal", "a/c bal", "account balance", "balance in a/c",
            "updated balance", "current balance", "mini statement", "bal statement", "statement generated",
            "summary of acc", "updated info", "balance enquiry", "balance info", "check balance",
            "balance update", "account info", "account update", "a/c statement", "a/c summary"
        )
        
        // Common merchant names for better detection - expanded with many more merchants
        private val KNOWN_MERCHANTS = listOf(
            // E-commerce
            "amazon", "flipkart", "ajio", "myntra", "snapdeal", "shopsy", "tata cliq", "jiomart", 
            "meesho", "nykaa", "firstcry", "lenskart", "1mg", "pharmeasy", "netmeds", "blinkit",
            "zepto", "mamaearth", "boat", "aliexpress", "shein", "urbanic", "bewakoof", "trends",
            
            // Food delivery
            "swiggy", "zomato", "dominos", "pizzahut", "mcdonalds", "kfc", "subway", "burgerking",
            "box8", "faasos", "freshmenu", "eatfit", "starbucks", "dunkindonuts", "wow momo", 
            "behrouz", "ovenstory", "theobroma", "chaayos", "biryani blues", "haldiram",
            
            // Groceries
            "bigbasket", "grofers", "dmart", "d-mart", "reliance fresh", "jiomart", "spencer", 
            "nature basket", "easyday", "more", "big bazaar", "metro cash", "smart bazaar",
            
            // Transportation
            "uber", "ola", "rapido", "meru", "quick ride", "blablacar", "bounce", "yulu", "vogo",
            "namma metro", "delhi metro", "mumbai metro", "irctc", "railway", "redbus", "abhibus",
            "uber auto", "ola auto", "zoom car", "revv", "drivezy", "ixigo",
            
            // Travel & Hospitality
            "makemytrip", "goibibo", "yatra", "cleartrip", "easemytrip", "airbnb", "oyo", "treebo",
            "fabhotels", "taj hotels", "marriott", "hilton", "agoda", "booking.com", "expedia",
            "via.com", "trivago", "holiday inn", "lemontree", "radisson", "airasia", "indigo",
            "spicejet", "vistara", "air india", "emirates", "etihad", "lufthansa", "british airways",
            
            // Entertainment 
            "bookmyshow", "paytm movies", "netflix", "prime video", "amazon prime", "hotstar", 
            "disney+", "sony liv", "zee5", "voot", "alt balaji", "jiocinema", "mxplayer", 
            "gaana", "spotify", "wynk", "youtube premium", "jiosaavn", "apple music", "tinder",
            "bumble", "pubg", "freefire", "ludo king", "rummy circle", "dream11", "mpl", "winzo",
            
            // Payments/Wallets
            "paytm", "phonepe", "gpay", "google pay", "amazon pay", "mobikwik", "freecharge", 
            "cred", "slice", "lazypay", "simpl", "jupiter", "ola money", "airtel payments",
            
            // Utilities & Bills
            "airtel", "jio", "vodafone", "bsnl", "mtnl", "tata power", "adani electricity",
            "mahadiscom", "reliance energy", "bses", "water bill", "gas bill", "indane", "hp gas",
            "bharat gas", "dish tv", "tatasky", "d2h", "sun direct", "airtel dth", "hathway",
            "act fibernet", "jio fiber", "bsnl broadband", "fastway",
            
            // International Services
            "facebook", "instagram", "whatsapp", "twitter", "linkedin", "telegram", "snapchat",
            "tiktok", "clubhouse", "ebay", "apple", "microsoft", "google", "canva", "zoom", 
            "skype", "slack", "adobe", "dropbox", "office 365", "paypal", "steam",
            
            // Edtech
            "byjus", "unacademy", "vedantu", "upgrad", "coursera", "udemy", "great learning",
            "whitehat jr", "skillshare", "simplilearn", "testbook", "gradeup", "toppr", "doubtnut",
            
            // Fitness & Health
            "cult.fit", "cure.fit", "healthify", "fitbit", "practo", "medlife", "apollo", "fortis",
            "max hospital", "manipal", "thyrocare", "lybrate", "fitternity", "healthkart",
            
            // Home Services
            "urbancompany", "urban company", "housejoy", "quikr", "olx", "justdial", "sulekha",
            "rentomojo", "furlenco", "pepperfry", "ikea", "urban ladder"
        )
    }
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (sms in messages) {
                try {
                    val sender = sms.displayOriginatingAddress?.trim() ?: ""
                    val body = sms.messageBody?.trim() ?: ""
                    
                    // Skip empty messages
                    if (sender.isBlank() || body.isBlank()) {
                        Log.d(TAG, "Skipping empty SMS")
                        captureLog("Skipping empty SMS")
                        continue
                    }
                    
                    // Log the message for debugging
                    Log.d(TAG, "Evaluating SMS from: $sender, body: ${body.take(50)}...")
                    captureLog("Evaluating SMS from: $sender, body: ${body.take(50)}...")
                
                    // Check if this is a transaction SMS
                    if (isTransactionSms(sender, body)) {
                        Log.d(TAG, "Transaction SMS detected!")
                        captureLog("Transaction SMS detected!")
                        
                        // Parse transaction details
                        val transaction = parseTransactionDetails(sender, body)
                        
                        // Skip if we couldn't extract a valid amount
                        if (transaction.amount <= 0) {
                            Log.d(TAG, "Skipping transaction with invalid amount: ${transaction.amount}")
                            captureLog("Skipping transaction with invalid amount: ${transaction.amount}")
                            continue
                        }
                        
                        // Log the extracted details
                        Log.d(TAG, "Extracted: ${transaction.merchantName}, Amount: ${transaction.amount}")
                        captureLog("Extracted: ${transaction.merchantName}, Amount: ${transaction.amount}")
                    
                        // Check if automatic transaction mode is enabled
                        if (preferenceHelper.isAutoTransactionEnabled()) {
                            // Automatically add the transaction to database
                            Log.d(TAG, "Auto mode: Adding transaction automatically")
                            captureLog("Auto mode: Adding transaction automatically")
                            CoroutineScope(Dispatchers.IO).launch {
                                transactionRepository.processNewTransactionSms(transaction)
                            }
                        } else {
                            // Show notification for manual review
                            Log.d(TAG, "Manual mode: Showing notification for user review")
                            captureLog("Manual mode: Showing notification for user review")
                            NotificationHelper.showTransactionNotification(context, transaction)
                        }
                    } else {
                        // Log a message indicating this SMS was not detected as a transaction
                        Log.d(TAG, "Not a transaction SMS")
                        captureLog("Not a transaction SMS: $sender - ${body.take(50)}")
                    }
                } catch (e: Exception) {
                    // Catch and log any exceptions during SMS processing to avoid crashes
                    Log.e(TAG, "Error processing SMS: ${e.message}", e)
                    captureLog("Error processing SMS: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Determines if an SMS is a transaction message by checking sender and body patterns
     * Enhanced with multiple filter layers for improved accuracy
     * Only considers official bank messages as the source of truth
     */
    private fun isTransactionSms(sender: String, body: String): Boolean {
        // Log full sender and message for debugging
        captureLog("Full message analysis - Sender: '$sender', Body: '$body'")
        
        // Check if sender is an official bank 
        val isBankSender = BANK_SENDERS.any { 
            sender.contains(it, ignoreCase = true) 
        }
        
        // Only proceed if the message is from an official bank
        if (!isBankSender) {
            Log.d(TAG, "SMS rejected: Not from a known bank sender - $sender")
            captureLog("SMS rejected: Not from a known bank sender - $sender")
            return false
        }
        
        // Check if this is likely an OTP message (which we want to ignore)
        val isOtpMessage = OTP_PATTERNS.any { 
            body.contains(it, ignoreCase = true) 
        }
        
        if (isOtpMessage) {
            Log.d(TAG, "SMS rejected: Appears to be an OTP message")
            captureLog("SMS rejected: Appears to be an OTP message")
            return false
        }
        
        // Check if this is a promotional message (which we want to ignore)
        val isPromotionalMessage = PROMOTIONAL_PATTERNS.any {
            body.contains(it, ignoreCase = true)
        }
        
        if (isPromotionalMessage) {
            Log.d(TAG, "SMS rejected: Appears to be a promotional message")
            captureLog("SMS rejected: Appears to be a promotional message")
            return false
        }
        
        // Additional criteria: Check for amount patterns with enhanced regex
        val amountPatterns = listOf(
            // Common Indian formats
            Regex("(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?"),
            // Amounts without currency symbol
            Regex("(?<=\\s|^)\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?(?=\\s|$)"),
            // "Sent X.XX" pattern
            Regex("(?:sent|paid|debited|deducted|withdrew)\\s+(?:Rs\\.?|INR|₹)?\\s*\\d+(?:\\.\\d{1,2})?"),
            // Amount followed by "from" or "to"
            Regex("\\d+(?:\\.\\d{1,2})?\\s+(?:from|to|by)")
        )
        
        // Relaxed filter: Allow messages that have amount patterns even if they look like balance updates
        val containsAmountPattern = amountPatterns.any { pattern ->
            pattern.find(body) != null
        }
        
        // Check if this contains debit-related keywords (slightly relaxed check)
        val isDebitRelated = DEBIT_PATTERNS.any { 
            body.contains(it, ignoreCase = true) 
        }
        
        // Check if this is a balance update or account info (which we want to ignore)
        val isBalanceUpdate = BALANCE_PATTERNS.any {
            body.contains(it, ignoreCase = true)
        }
        
        // RELAXED FILTER: Still consider balance updates if they contain strong transaction indicators
        if (isBalanceUpdate && !isDebitRelated && !containsAmountPattern) {
            Log.d(TAG, "SMS rejected: Appears to be just a balance update")
            captureLog("SMS rejected: Appears to be just a balance update")
            return false
        }
        
        // RELAXED FILTER: If message contains amount pattern and is from a bank, consider it a transaction
        // even if it doesn't have a clear debit keyword
        if (containsAmountPattern) {
            Log.d(TAG, "SMS accepted: Contains amount pattern from bank sender")
            captureLog("SMS accepted: Contains amount pattern from bank sender")
            return true
        }
        
        if (!isDebitRelated) {
            Log.d(TAG, "SMS rejected: No debit-related keywords found")
            captureLog("SMS rejected: No debit-related keywords found")
            return false
        }
        
        // Message passed all filters - it's a transaction message
        Log.d(TAG, "SMS accepted as transaction: $sender - ${body.take(50)}...")
        captureLog("SMS accepted as transaction: $sender - ${body.take(50)}...")
        return true
    }
    
    /**
     * Extracts transaction details from the SMS body
     * Enhanced with more robust amount detection patterns
     */
    private fun parseTransactionDetails(sender: String, body: String): TransactionSms {
        // Extract amount - improved with more comprehensive pattern matching
        // Handles various currency formats (₹, Rs., INR, USD, $) with proper grouping
        val primaryAmountRegex = Regex(
            "(?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.?\\d*)|" +  // Indian currency formats
            "(?:amount|amt|charge|bill|fee)\\s+(?:of\\s+)?(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.?\\d*)|" +  // "amount of" formats
            "(?:USD|\\$)\\s*([\\d,.]+)\\s*(?:\\((?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.?\\d*)\\))?|" +  // USD with possible INR conversion
            "(?:spent|paid|send|sent|paying|received|credited|debited)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.?\\d*)|" +  // Action + amount
            "(?:for|of|with|worth)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.?\\d*)|" +  // Preposition + amount
            "(?:sent|paid)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.?\\d*)\\s+(?:from|to)"  // Generic format for all banks
        )
        
        var amount = 0.0
        
        // Try primary amount regex first
        val primaryMatch = primaryAmountRegex.find(body)
        if (primaryMatch != null) {
            // Try all captured groups for the amount
            for (i in 1 until primaryMatch.groupValues.size) {
                val value = primaryMatch.groupValues[i]
                if (value.isNotEmpty()) {
                    // Clean the value (remove commas) and convert to double
                    amount = value.replace(",", "").toDoubleOrNull() ?: 0.0
                    if (amount > 0) break
                }
            }
        }
        
        // If amount is still 0, try the secondary regex
        if (amount == 0.0) {
            val secondaryAmountRegex = Regex(
                "(?<=\\s|^)(?:Rs\\.?|INR|₹)?\\s*(\\d[\\d,]+\\.?\\d*)(?=\\s|$)|" +  // Numbers that look like amounts
                "(?<=\\s|^)(\\d+\\.\\d{2})(?=\\s|$)|" +  // Numbers with exactly 2 decimal places
                "(?:Rs|INR|₹)?\\s*([\\d,]+)"  // Any number after currency symbol
            )
            val secondaryMatch = secondaryAmountRegex.find(body)
            if (secondaryMatch != null) {
                for (i in 1 until secondaryMatch.groupValues.size) {
                    val value = secondaryMatch.groupValues[i]
                    if (value.isNotEmpty()) {
                        amount = value.replace(",", "").toDoubleOrNull() ?: 0.0
                        if (amount > 0) break
                    }
                }
            }
        }
        
        // Last resort: find the largest number in the message if it looks like a valid amount
        if (amount == 0.0) {
            val numberPattern = Regex("\\b(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\b")
            val allNumbers = numberPattern.findAll(body)
                .map { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
                .filter { it > 10.0 && it < 1000000.0 } // Reasonable amount range
                .toList()
            
            if (allNumbers.isNotEmpty()) {
                // Choose the most likely amount (typically the largest number in the SMS)
                amount = allNumbers.maxOrNull() ?: 0.0
            }
        }
        
        // Determine if this is likely a debit/expense based on message content
        val isLikelyDebit = DEBIT_PATTERNS.any { body.contains(it, ignoreCase = true) }
        
        // Make amount negative for debits - this handles expense transactions from all banks uniformly
        if (isLikelyDebit && amount > 0) {
            amount = -amount
            captureLog("Converting to negative amount for debit transaction: -$amount")
        }
        
        // Enhanced merchant detection with multiple patterns and context awareness
        val merchant = extractMerchantName(body)
        
        // Create and return the transaction object
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
     * Enhanced with bank-specific formats and better pattern recognition
     */
    private fun extractMerchantName(body: String): String {
        // First check for known merchants - most reliable approach
        for (merchant in KNOWN_MERCHANTS) {
            if (body.contains(merchant, ignoreCase = true)) {
                // Process multi-word merchants correctly
                return merchant.split(" ")
                    .joinToString(" ") { word -> 
                        word.replaceFirstChar { it.uppercase() } 
                    }
            }
        }
        
        // Check for subscription formats
        val subscriptionPatterns = listOf(
            Regex("(?:subscription|recurring payment|membership)\\s+(?:for|to)\\s+([A-Za-z0-9\\s&\\-']+)"),
            Regex("(?:payment to|paid to)\\s+([A-Za-z0-9\\s&\\-']+)\\s+(?:subscription|membership)")
        )
        
        for (pattern in subscriptionPatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                val name = match.groupValues[1].trim()
                if (name.length >= 3 && !isPhoneNumber(name)) {
                    return name.replaceFirstChar { it.uppercase() }
                }
            }
        }
        
        // Bank-specific patterns - helps with consistent extraction
        if (body.contains("hdfc", ignoreCase = true)) {
            // HDFC format: "You've spent Rs.X at MERCHANT_NAME on..."
            val hdfcPattern = Regex("spent\\s+(?:Rs\\.?|INR|₹)?\\s*[\\d,.]+\\s+(?:at|with|using)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+via|\\s+info|\\.|$)")
            val match = hdfcPattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                return cleanupMerchantName(match.groupValues[1], body)
            }
        } else if (body.contains("icici", ignoreCase = true)) {
            // ICICI format: "Transaction of INR X at MERCHANT_NAME on..."
            val iciciPattern = Regex("(?:at|in)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+info|\\.|$)")
            val match = iciciPattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                return cleanupMerchantName(match.groupValues[1], body)
            }
        } else if (body.contains("sbi", ignoreCase = true)) {
            // SBI format: "Purchase of INR X at MERCHANT_NAME on card..."
            val sbiPattern = Regex("(?:at|to|for)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+info|\\s+on\\s+card|\\.|$)")
            val match = sbiPattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                return cleanupMerchantName(match.groupValues[1], body)
            }
        }
        
        // UPI specific patterns
        if (body.contains("upi", ignoreCase = true)) {
            // Look for UPI ID which often contains merchant info: name@upi
            val upiIdPattern = Regex("([a-zA-Z0-9._-]+)@([a-zA-Z0-9]+)")
            val match = upiIdPattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                val merchantPart = match.groupValues[1].replace(".", " ")
                if (merchantPart.length > 3) {
                    return cleanupMerchantName(merchantPart, body)
                }
            }
            
            // Try to find UPI Ref which sometimes contains merchant info
            val upiRefPattern = Regex("(?:UPI|VPA):?\\s*([A-Za-z0-9\\s.\\-_@]+)")
            val refMatch = upiRefPattern.find(body)
            if (refMatch != null && refMatch.groupValues.size > 1) {
                return "UPI: ${refMatch.groupValues[1].trim()}"
            }
        }
        
        // Try the generic patterns (already in original code)
        val patterns = listOf(
            Regex("(?:spent|txn|purchase|payment)\\s+(?:at|in|to|with|via|from)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+for|\\s+info|\\s+[\\d]|\\.|$)"),
            Regex("(?:at|in|to|with|thru)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+for|\\s+info|\\s+[\\d]|\\.|$)"),
            Regex("(?:towards|for|to)\\s+([A-Za-z0-9\\s&\\-']+?)(?:\\s+on|\\s+info|\\s+[\\d]|\\.|$)"),
            Regex("(?:at|in|to)\\s+([A-Za-z0-9\\s&\\-']+?)-([A-Za-z0-9\\s]+?)(?:\\s+on|\\s+info|\\s+[\\d]|\\.|$)")
        )
        
        // Try all patterns in order
        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                // Skip phone number matches
                if (isPhoneNumber(extracted)) {
                    continue
                }
                // Clean up and validate before returning
                val cleaned = cleanupMerchantName(extracted, body)
                if (cleaned != "Unknown Merchant") {
                    return cleaned
                }
            }
        }
        
        // Fallback to trying to extract the most distinctive capitalized words
        val capitalizedWords = Regex("\\b[A-Z][A-Za-z]{2,}\\b")
            .findAll(body)
            .map { it.value }
            .filter { !isCommonWord(it) }
            .toList()
        
        if (capitalizedWords.isNotEmpty()) {
            val candidateWord = capitalizedWords.first()
            return candidateWord
        }
        
        // Last resort - try to find any reference number
        val fallbackPattern = Regex("(?:Ref|ref|REF|txn|Txn|TXN)[\\s:]*([A-Za-z0-9]+)")
        val fallbackMatch = fallbackPattern.find(body)
        if (fallbackMatch != null) {
            return "Txn: ${fallbackMatch.groupValues[1]}"
        }
        
        return "Unknown Merchant"
    }
    
    /**
     * Check if a word is a common non-merchant word that should be ignored
     */
    private fun isCommonWord(word: String): Boolean {
        val commonWords = setOf(
            "INR", "AMOUNT", "PAYMENT", "TRANSACTION", "DEBIT", "CREDIT", "BANK",
            "ACCOUNT", "CARD", "YOUR", "THANK", "THANKS", "INFO", "ALERT", "MESSAGE",
            "REGARDS", "AVAILABLE", "BALANCE", "MONEY", "PAID", "RECEIVED", "FROM",
            "TRANSFER", "DATE", "TIME", "DEBITED", "CREDITED", "SEND", "SENT"
        )
        return commonWords.contains(word.uppercase())
    }
    
    /**
     * Check if a string looks like a phone number
     */
    private fun isPhoneNumber(text: String): Boolean {
        // Remove all non-digit characters
        val digitsOnly = text.replace(Regex("\\D+"), "")
        // If we have 10 or more digits, it's likely a phone number
        return digitsOnly.length >= 10 && digitsOnly.length <= 12
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
            "block", "not", "call", "fwd", "download", "forward", "to block", "block upi",
            "to", "from", "at", "on", "by", "via", "thru", "of", "in", "for", "the", "and", "is", "on",
            "your", "you", "has", "have", "been", "was", "will", "shall", "mobile", "phone", "using",
            "with", "through", "vpa", "a/c", "ac", "no", "ifsc", "dated", "date", "time"
        )
        
        // Check if the merchant is just a phone number
        val isPhoneNumber = merchant.trim().replace(" ", "").matches(Regex("\\d{10,12}"))
        if (isPhoneNumber) {
            // Try harder to find a real merchant name
            return extractBetterMerchantFromBody(originalBody)
        }
        
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
    
    /**
     * Extracts better merchant name from SMS body when primary attempt fails
     */
    private fun extractBetterMerchantFromBody(body: String): String {
        // Look for common keyword-value patterns that might indicate a merchant
        val commonPatterns = listOf(
            Regex("(?:merchant|shop|store|vendor|payee|biller|recipient)\\s*[:-]?\\s*([A-Za-z0-9\\s&\\-']+)"),
            Regex("(?:at|with|to)\\s+([A-Za-z]+)"),
            Regex("\\b([A-Za-z]{4,})\\b") // Look for words that might be merchant names
        )
        
        for (pattern in commonPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                
                // Skip if it looks like a phone number
                if (isPhoneNumber(candidate)) {
                    continue
                }
                
                // Skip common non-merchant words
                if (candidate.equals("info", ignoreCase = true) || 
                    candidate.equals("transaction", ignoreCase = true) ||
                    candidate.equals("credit", ignoreCase = true) ||
                    candidate.equals("debit", ignoreCase = true)) {
                    continue
                }
                
                if (candidate.length >= 3) {
                    return candidate.replaceFirstChar { it.uppercase() }
                }
            }
        }
        
        // Last resort - try to find a UPI ID or reference number
        val upiMatch = Regex("([a-zA-Z0-9.]+@[a-zA-Z0-9.]+)").find(body)
        if (upiMatch != null) {
            return "UPI: ${upiMatch.groupValues[1]}"
        }
        
        return "Unknown Merchant"
    }
} 