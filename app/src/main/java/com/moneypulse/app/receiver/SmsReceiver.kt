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
                        if (transaction.amount == 0.0) {
                            Log.d(TAG, "Skipping transaction with zero amount")
                            captureLog("Skipping transaction with zero amount")
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
        
        // Check if this is a credit transaction (money received) - WE SKIP THESE
        val isCreditTransaction = body.contains("credited", ignoreCase = true) || 
                                 body.contains("received", ignoreCase = true) ||
                                 body.contains("added to", ignoreCase = true) ||
                                 body.contains("deposited", ignoreCase = true) ||
                                 body.contains("credit", ignoreCase = true)
        
        if (isCreditTransaction) {
            Log.d(TAG, "SMS rejected: Credit transaction (money received) - we only track expenses")
            captureLog("SMS rejected: Credit transaction (money received) - we only track expenses")
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
        
        // Properly determine transaction type (expense/debit vs income/credit)
        val isDebitTransaction = body.contains("debited", ignoreCase = true) || 
                               body.contains("spent", ignoreCase = true) || 
                               body.contains("debit", ignoreCase = true) || 
                               body.contains("withdrawn", ignoreCase = true) ||
                               body.contains("sent", ignoreCase = true) ||
                               body.contains("paid", ignoreCase = true) ||
                               body.contains("purchase", ignoreCase = true) ||
                               body.contains("payment", ignoreCase = true)
        
        // Make amount negative ONLY for debit transactions 
        if (isDebitTransaction && amount > 0) {
            amount = -amount
            captureLog("Converting to negative amount for debit transaction: $amount")
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
     * Enhanced merchant name extraction with comprehensive pattern matching
     * Handles various transaction formats including merchants, person-to-person, and edge cases
     */
    private fun extractMerchantName(body: String): String {
        // Convert to lowercase for consistent pattern matching while preserving original for extraction
        val lowerBody = body.toLowerCase()
        var merchantName = ""
        
        // Log original message for debugging
        captureLog("Extracting merchant from: ${body.take(50)}...")
        
        // --------- PRIORITY 1: DIRECT RECIPIENT PATTERNS ---------
        
        // Pattern 1: "to [recipient]" - highest confidence pattern
        val toPattern = Regex("to\\s+([A-Za-z0-9@_.\\s&'-]+)(?:\\s+(?:on|dt|ref|upi|not|bal|thru|\\.))", RegexOption.IGNORE_CASE)
        toPattern.find(body)?.let {
            merchantName = it.groupValues[1].trim()
            captureLog("Extracted using 'to' pattern: $merchantName")
        }
        
        // Pattern 2: "at [merchant]" for card/POS transactions
        if (merchantName.isEmpty() && (lowerBody.contains("card") || lowerBody.contains("pos") || lowerBody.contains("purchase"))) {
            val atPattern = Regex("at\\s+([A-Za-z0-9\\s&'-]+)(?:\\s+(?:on|dt|\\.))", RegexOption.IGNORE_CASE)
            atPattern.find(body)?.let {
                merchantName = it.groupValues[1].trim()
                captureLog("Extracted using 'at' pattern: $merchantName")
            }
        }
        
        // Pattern 3: "for [service/merchant]" for bill payments
        if (merchantName.isEmpty() && (lowerBody.contains("bill") || lowerBody.contains("payment"))) {
            val forPattern = Regex("for\\s+([A-Za-z0-9\\s&'-]+)(?:\\s+(?:on|dt|\\.))", RegexOption.IGNORE_CASE)
            forPattern.find(body)?.let {
                merchantName = it.groupValues[1].trim()
                captureLog("Extracted using 'for' pattern: $merchantName")
            }
        }
        
        // --------- PRIORITY 2: UPI ID EXTRACTION ---------
        
        // Pattern 4: UPI ID extraction (username@provider)
        if (merchantName.isEmpty()) {
            val upiPattern = Regex("to\\s+([a-zA-Z0-9_.]+@[a-zA-Z0-9_.]+)", RegexOption.IGNORE_CASE)
            upiPattern.find(body)?.let {
                val fullUpiId = it.groupValues[1].trim()
                // Extract username part for display
                val userName = fullUpiId.split("@")[0]
                merchantName = if (userName.length > 3) {
                    // Format as "Username (UPI)" if username seems meaningful
                    "${userName.capitalize()} (UPI)"
                } else {
                    // Keep full UPI ID if username is too short or numeric
                    fullUpiId
                }
                captureLog("Extracted from UPI ID: $merchantName")
            }
        }
        
        // --------- PRIORITY 3: TRANSACTION TYPE PATTERNS ---------
        
        // Pattern 5: Find merchant names after payment keywords
        if (merchantName.isEmpty()) {
            val paymentKeywords = listOf("sent", "paid", "debited", "payment", "transfer", "spent")
            for (keyword in paymentKeywords) {
                if (lowerBody.contains(keyword)) {
                    // Look for capitalized words after the keyword
                    val afterKeyword = body.substring(lowerBody.indexOf(keyword) + keyword.length)
                    val capitalizedPattern = Regex("([A-Z][A-Za-z0-9\\s&'-]{2,})")
                    capitalizedPattern.find(afterKeyword)?.let {
                        merchantName = it.groupValues[1].trim()
                        captureLog("Extracted capitalized word after '$keyword': $merchantName")
                        break
                    }
                }
            }
        }
        
        // --------- PRIORITY 4: SPECIAL TRANSACTION FORMATS ---------
        
        // Pattern 6: Extract from "thru UPI:" pattern (common in some banks)
        if (merchantName.isEmpty() && lowerBody.contains("thru upi:")) {
            // Check if there's a name before "thru UPI:"
            val beforeUpi = body.substring(0, lowerBody.indexOf("thru upi:"))
            val capitalizedPattern = Regex("([A-Z][A-Za-z0-9\\s&'-]{2,})")
            val matches = capitalizedPattern.findAll(beforeUpi).toList()
            if (matches.isNotEmpty()) {
                // Take the last capitalized word before "thru UPI:"
                merchantName = matches.last().groupValues[1].trim()
                captureLog("Extracted capitalized word before 'thru UPI:': $merchantName")
            }
        }
        
        // --------- PRIORITY 5: FALLBACK STRATEGIES ---------
        
        // Fallback 1: Reference numbers if they look meaningful
        if (merchantName.isEmpty() && lowerBody.contains("upi:")) {
            val refPattern = Regex("upi:([0-9]{1,10})", RegexOption.IGNORE_CASE)
            refPattern.find(body)?.let {
                // Only use short reference numbers (more likely to be merchant codes)
                val refNumber = it.groupValues[1].trim()
                if (refNumber.length <= 6) {
                    merchantName = "UPI Ref: $refNumber"
                    captureLog("Extracted UPI reference: $merchantName")
                }
            }
        }
        
        // Fallback 2: Generic transaction type
        if (merchantName.isEmpty()) {
            merchantName = when {
                lowerBody.contains("upi") -> "UPI Payment"
                lowerBody.contains("neft") -> "NEFT Transfer"
                lowerBody.contains("imps") -> "IMPS Transfer"
                lowerBody.contains("card") -> "Card Payment"
                lowerBody.contains("atm") || lowerBody.contains("cash") -> "ATM Withdrawal"
                lowerBody.contains("bill") -> "Bill Payment"
                else -> "Bank Transaction"
            }
            captureLog("Using generic transaction type: $merchantName")
        }
        
        // --------- CLEANUP AND FORMATTING ---------
        
        // Clean up extracted merchant name
        merchantName = cleanupMerchantName(merchantName)
        
        return merchantName
    }
    
    /**
     * Cleans up and formats a merchant name for better display
     */
    private fun cleanupMerchantName(name: String): String {
        if (name.isEmpty()) return "Unknown"
        
        // Keep UPI IDs and UPI references as is
        if (name.contains("@") || name.startsWith("UPI")) return name
        
        var cleanName = name
        
        // Remove common noise words and patterns
        val noisePatterns = listOf(
            "ltd", "limited", "pvt", "private", "india", "services", 
            "systems", "bal", "not u", "not you", "fwd", "ref", "upi ref"
        )
        
        for (pattern in noisePatterns) {
            cleanName = cleanName.replace(Regex("\\b$pattern\\b", RegexOption.IGNORE_CASE), "")
        }
        
        // Remove any trailing dots, commas, etc.
        cleanName = cleanName.replace(Regex("[\\.,:;]+$"), "")
        
        // Remove excess whitespace
        cleanName = cleanName.replace(Regex("\\s+"), " ").trim()
        
        // If name became too short after cleanup, return original
        if (cleanName.length < 3 && name.length > 3) {
            return name.trim()
        }
        
        // Capitalize properly if not already
        if (!cleanName.contains(Regex("[A-Z]"))) {
            cleanName = cleanName.split(" ").joinToString(" ") { word ->
                if (word.length > 1) {
                    word[0].uppercaseChar() + word.substring(1)
                } else {
                    word.uppercase()
                }
            }
        }
        
        return cleanName.ifEmpty { "Unknown" }
    }
} 