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
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver that listens for incoming SMS messages
 * and processes them to detect financial transactions.
 * Enhanced with security features to protect sensitive data.
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
        
        // Store transaction references for duplicate detection
        // Key: Reference ID, Value: Timestamp when it was processed
        private val processedTransactions = mutableMapOf<String, Long>()
        
        // Cleanup old transaction references (older than 30 minutes)
        private fun cleanupOldTransactions() {
            val cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000) // 30 minutes ago
            val oldKeys = processedTransactions.entries
                .filter { it.value < cutoffTime }
                .map { it.key }
            
            for (key in oldKeys) {
                processedTransactions.remove(key)
            }
        }
        
        // Check if a transaction with the given reference has been processed recently
        fun isRecentDuplicate(referenceId: String): Boolean {
            cleanupOldTransactions() // First cleanup old entries
            return processedTransactions.containsKey(referenceId)
        }
        
        // Mark a transaction as processed
        fun markTransactionProcessed(referenceId: String) {
            processedTransactions[referenceId] = System.currentTimeMillis()
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
            // Marketing terms
            "offer", "discount", "cashback", "reward", "coupon", "promo", "sale", "% off", "percent off",
            "limited time", "exclusive deal", "special offer", "congratulations", "credit card offer",
            "loan offer", "pre-approved", "pre approved", "upgrade", "invite", "refer", "bonus",
            "save now", "expires soon", "hurry", "last day", "only today", "insurance plan",
            "investment plan", "scheme", "lucky draw", "win", "free", "gift", "get extra", "get flat",
            "get upto", "installment", "emi option", "pay later",
            
            // Loan and credit offers
            "personal loan", "home loan", "car loan", "loan of", "loan at", "loan with", "loan up to",
            "exclusive rates", "interest rate", "low rate", "lowest rate", "best rate", "attractive rate",
            "no paperwork", "instant approval", "quick approval", "easy approval", "zero fee", 
            "processing fee", "get loan", "apply loan", "apply now", "lacs", "lakhs", "crore",
            "exclusive offer", "limited period", "limited period offer", "instant loan", "quick loan",
            "get cash", "cash loan", "approved", "pre approved", "pre-qualified", "eligible",
            
            // Banking program promotional terms
            "banking program", "priority banking", "privilege banking", "premium banking",
            "relationship manager", "dedicated manager", "personal banker", "dedicated banker",
            "average monthly balance", "minimum balance", "maintain balance", "maintain a balance",
            "we invite you", "we now invite", "invited to", "discover our", "extend the benefits",
            "benefits to", "family members", "your family", "up to", "upto", "off on", "discounts on",
            "visit your nearest", "visit branch", "nearest branch", "visit our", "to apply",
            "t&c apply", "terms apply", "terms and conditions", "click here", "visit now", "learn more",
            "know more", "find out more", "explore", "introducing", "new service", "new feature",
            
            // Common promotional link patterns
            "https://", "http://", "click", "link", "axbk.io", "bit.ly", "tinyurl", 
            
            // Banking upgrade patterns
            "upgrade to", "upgrade your", "higher benefits", "exclusive benefits", "special privileges",
            "unlock", "fees waived", "zero fee", "fee waiver", "annual fee waiver", "joining fee waiver"
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
    
    @Inject
    lateinit var securityHelper: SecurityHelper
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (sms in messages) {
                try {
                    val sender = sms.displayOriginatingAddress?.trim() ?: ""
                    val body = sms.messageBody?.trim() ?: ""
                    
                    // Sanitize input to prevent any injection attacks
                    val sanitizedSender = securityHelper.sanitizeInput(sender)
                    val sanitizedBody = securityHelper.sanitizeInput(body)
                    
                    // Skip empty messages
                    if (sanitizedSender.isBlank() || sanitizedBody.isBlank()) {
                        Log.d(TAG, "Skipping empty SMS")
                        captureLog("Skipping empty SMS")
                        continue
                    }
                    
                    // Mask sensitive data in logs
                    val maskedBody = securityHelper.maskSensitiveData(sanitizedBody)
                    
                    // Log the message for debugging with masked sensitive information
                    Log.d(TAG, "Evaluating SMS from: $sanitizedSender, body: ${maskedBody.take(50)}...")
                    captureLog("Evaluating SMS from: $sanitizedSender, body: ${maskedBody.take(50)}...")
                
                    // Check if this is a transaction SMS - use original sanitized values for processing
                    if (isTransactionSms(sanitizedSender, sanitizedBody)) {
                        Log.d(TAG, "Transaction SMS detected!")
                        captureLog("Transaction SMS detected!")
                        
                        // Parse transaction details - using sanitized values
                        val transaction = parseTransactionSms(sanitizedSender, sanitizedBody)
                        
                        // Skip if we couldn't extract a valid amount
                        if (transaction.amount == 0.0) {
                            Log.d(TAG, "Skipping transaction with zero amount")
                            captureLog("Skipping transaction with zero amount")
                            continue
                        }
                        
                        // Extract reference ID for duplicate detection
                        val referenceId = extractReferenceNumber(sanitizedBody)
                        
                        // Check if this is a duplicate transaction we've seen recently
                        if (isRecentDuplicate(referenceId)) {
                            Log.d(TAG, "Skipping duplicate transaction with reference: $referenceId")
                            captureLog("Skipping duplicate transaction: ${transaction.merchantName}, amount: [MASKED]")
                            continue
                        }
                        
                        // Mark this transaction as processed to avoid duplicates
                        markTransactionProcessed(referenceId)
                        
                        // Log the extracted details - mask merchant name for privacy
                        Log.d(TAG, "Extracted: ${transaction.merchantName}, Amount: [MASKED]")
                        captureLog("Extracted: ${transaction.merchantName}, Amount: [MASKED]")
                    
                        // Check if automatic transaction mode is enabled
                        if (preferenceHelper.isAutoTransactionEnabled()) {
                            // Automatically add the transaction to database
                            Log.d(TAG, "Auto mode: Adding transaction automatically")
                            captureLog("Auto mode: Adding transaction automatically")
                            
                            // Use launch to create a coroutine and call the suspend function within it
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    // This is now properly called within a coroutine context
                                transactionRepository.processNewTransactionSms(transaction)
                                    Log.d(TAG, "Transaction successfully processed")
                                    captureLog("Transaction successfully processed")
                                } catch (e: Exception) {
                                    // Log any errors that occur during processing
                                    Log.e(TAG, "Error in coroutine when processing transaction: ${e.javaClass.simpleName}")
                                    captureLog("Error in coroutine: ${e.javaClass.simpleName}")
                                }
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
                        captureLog("Not a transaction SMS: $sanitizedSender - ${maskedBody.take(50)}")
                    }
                } catch (e: Exception) {
                    // Catch and log any exceptions during SMS processing to avoid crashes
                    // Don't log the full exception which might contain sensitive data
                    Log.e(TAG, "Error processing SMS: ${e.javaClass.simpleName}")
                    captureLog("Error processing SMS: ${e.javaClass.simpleName}")
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
        
        // Comprehensive sender validation - check if sender is an official financial entity
        // Uses word boundary checking for more accurate matching
        val isBankSender = BANK_SENDERS.any { bankCode -> 
            // Match exact code with word boundaries or at start/end
            Regex("(?:^|\\s)$bankCode(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(sender) ||
            // For very short sender IDs (4 chars or less), allow exact match without boundaries
            (bankCode.length <= 4 && sender.contains(bankCode, ignoreCase = true))
        }
        
        // Also check for payment apps and UPI services
        val isPaymentAppSender = UPI_SENDERS.any { upiCode ->
            Regex("(?:^|\\s)$upiCode(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(sender) ||
            (upiCode.length <= 5 && sender.contains(upiCode, ignoreCase = true))
        }
        
        // Only proceed if the message is from an official bank or payment service
        if (!isBankSender && !isPaymentAppSender) {
            Log.d(TAG, "SMS rejected: Not from a known financial sender - $sender")
            captureLog("SMS rejected: Not from a known financial sender - $sender")
            return false
        }
        
        // Unified pattern detector with prioritized checks
        
        // STEP 1: Quick rejection patterns - filter out obviously non-transaction messages
        
        // Reject OTP messages with high confidence patterns (using word boundaries)
        val isStrongOtpPattern = body.contains(Regex("\\botp\\b|\\bone[ -]?time[ -]?password\\b|\\bverification[ -]?code\\b|\\bsecurity[ -]?code\\b|\\bauth(?:entication)?[ -]?code\\b", RegexOption.IGNORE_CASE))
        
        if (isStrongOtpPattern) {
            Log.d(TAG, "SMS rejected: Strong OTP pattern detected")
            captureLog("SMS rejected: Strong OTP pattern detected")
            return false
        }
        
        // Check for numerical codes in the message - common in OTP texts
        val hasNumericCode = body.contains(Regex("\\b\\d{4,8}\\b(?:(?:\\s+is|\\s+as|:)\\s+(?:your|the)\\s+(?:otp|code|password))|(?:code(?:\\s+is)?:?\\s+\\b\\d{4,8}\\b)"))
        
        if (hasNumericCode) {
            Log.d(TAG, "SMS rejected: Contains numeric verification code pattern")
            captureLog("SMS rejected: Contains numeric verification code pattern")
            return false
        }
        
        // Check if this is likely an OTP message using broader verification terms
        val isOtpMessage = OTP_PATTERNS.any { pattern -> 
            body.contains(pattern, ignoreCase = true) 
        }
        
        if (isOtpMessage) {
            Log.d(TAG, "SMS rejected: Appears to be an OTP/verification message")
            captureLog("SMS rejected: Appears to be an OTP/verification message")
            return false
        }
        
        // Check if this is a promotional message (stronger pattern matching)
        val isPromotionalMessage = PROMOTIONAL_PATTERNS.any { pattern ->
            // Apply word boundary check for more accurate matching
            body.contains(Regex("\\b$pattern\\b|$pattern\\s", RegexOption.IGNORE_CASE))
        }
        
        // Enhanced promotional detection for banking offers - analyze combinations of indicators
        val hasBankingPromotionalContext = 
            // Check for common promotional combinations
            (body.contains(Regex("(?:we|bank)\\s+(?:now\\s+)?invite\\s+you", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:discover|explore|know|learn|find out)\\s+(?:our|about|more)", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:maintain|average|minimum)\\s+(?:monthly|quarterly|yearly)?\\s*balance", RegexOption.IGNORE_CASE)) ||
            
            // Check for promotional URL patterns - strong indicator of promotional content
            body.contains(Regex("(?:https?://|www\\.|bit\\.ly|tinyurl|axbk\\.io)[a-zA-Z0-9/?=&%._-]+", RegexOption.IGNORE_CASE)) ||
            
            // Check for promotional verbs with service nouns
            body.contains(Regex("(?:enjoy|get|avail|discover)\\s+(?:a|the|our|exclusive|special|premium)\\s+(?:service|feature|benefit|program|offer)", RegexOption.IGNORE_CASE))) &&
            
            // Combined with amount patterns that are likely promotional, not transactional
            (body.contains(Regex("(?:upto|up to|as much as)\\s+(?:\\d+%|Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:\\d+%)\\s+(?:off|discount|cashback)", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:maintain)\\s+(?:an?|the)?\\s+(?:average|minimum)\\s+(?:monthly|quarterly)?\\s+balance\\s+of\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)))
        
        // Specific check for loan offers with amount patterns - these are NOT transactions
        val isLoanOfferMessage = 
            (body.contains(Regex("(?:personal|home|car|instant|quick|cash)\\s+loan", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("loan\\s+(?:of|at|with|up to|upto)", RegexOption.IGNORE_CASE))) &&
            // Combined with either amount patterns, promotional terms, or URLs
            (body.contains(Regex("(?:Rs\\.?|INR|₹)\\s*\\d+\\s*(?:lac|lakh|crore)", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("exclusive\\s+rates|no\\s+paperwork|instant\\s+approval|apply\\s+now", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:click|visit)\\s*(?::|->)?\\s*(?:https?://|www\\.|http)", RegexOption.IGNORE_CASE)))
        
        // Additional check for promotional messages with "Get Cash" that aren't ATM withdrawals
        val isGetCashPromotionalMessage =
            body.contains(Regex("get\\s+cash", RegexOption.IGNORE_CASE)) &&
            !body.contains(Regex("\\b(?:withdraw|withdrawal|atm|transaction|debited)\\b", RegexOption.IGNORE_CASE)) &&
            (body.contains(Regex("(?:exclusive|special|offer|instant|apply|click)", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:https?://|www\\.|bit\\.ly|tinyurl)[a-zA-Z0-9/?=&%._-]+", RegexOption.IGNORE_CASE)))
        
        if (isPromotionalMessage || hasBankingPromotionalContext || isLoanOfferMessage || isGetCashPromotionalMessage) {
            Log.d(TAG, "SMS rejected: Appears to be a promotional message")
            captureLog("SMS rejected: Appears to be a promotional or loan offer message")
            return false
        }
        
        // STEP 2: Check for credit-only transactions (if the app only tracks expenses)
        
        // Enhanced credit transaction detection with more accurate pattern matching
        val isCreditOnlyTransaction = 
            // Message contains credit-related terms 
            body.contains(Regex("\\b(?:credited|received|deposited|added to|inward)\\b", RegexOption.IGNORE_CASE)) && 
            // AND does NOT contain debit-related terms (critical for dual-mention transactions)
            !body.contains(Regex("\\b(?:debited|debit|paid|spent|payment|purchase|dr\\.?|withdraw|sent)\\b", RegexOption.IGNORE_CASE))
        
        // For dual-mention transactions (common in ICICI and other banks): 
        // "Account debited ... recipient credited" - these ARE debit transactions
        val isDualTransactionFormat = 
            body.contains(Regex("\\b(?:debited|debit)\\b", RegexOption.IGNORE_CASE)) &&
            body.contains(Regex("\\b(?:credited)\\b", RegexOption.IGNORE_CASE))
            
        // Log specific details for dual-mention transactions for clarity
        if (isDualTransactionFormat) {
            Log.d(TAG, "Dual-mention transaction detected (contains both debit and credit terms)")
            captureLog("Dual-mention transaction detected - this is a valid debit transaction")
        }
        
        if (isCreditOnlyTransaction && !isDualTransactionFormat) {
            Log.d(TAG, "SMS rejected: Credit-only transaction (money received) - we only track expenses")
            captureLog("SMS rejected: Credit-only transaction (money received) - we only track expenses")
            return false
        }
        
        // STEP 3: Strong transaction indicator patterns - check for reliable transaction signals
        
        // Check if this message has balance requirement format (common in banking program promotions)
        val hasBalanceRequirementFormat = body.contains(Regex("(?:maintain|average|minimum)\\s+(?:monthly|quarterly)?\\s+balance\\s+(?:of|:)\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE))
        
        // If message has balance requirement format but not clear transaction indicators, reject it
        if (hasBalanceRequirementFormat && !body.contains(Regex("\\b(?:debited|debit|paid|sent|spent|payment|purchase|transaction|txn)\\b", RegexOption.IGNORE_CASE))) {
            Log.d(TAG, "SMS rejected: Contains balance requirement format, not a transaction")
            captureLog("SMS rejected: Contains balance requirement format, not a transaction")
            return false
        }
        
        // Check for strong debit indicators with word boundary precision
        val hasStrongDebitIndicator = body.contains(Regex("\\b(?:debited|debit\\s+card|debit\\s+from|payment\\s+made|paid|purchase|spent)\\b", RegexOption.IGNORE_CASE))
        
        // Check for amount patterns with currency symbols - highly reliable indicator of transactions
        val hasAmountWithCurrency = body.contains(Regex("(?:Rs\\.?|INR|₹|USD|\\$)\\s*[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE))
        
        // Check for word "amount" near numbers - strong transaction indicator
        val hasAmountKeyword = body.contains(Regex("(?:amount|amt)(?:\\s+(?:of|:))?\\s+(?:Rs\\.?|INR|₹|USD|\\$)?\\s*[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE))
        
        // Check for transaction IDs/reference numbers - reliable indicator of actual transactions
        val hasTransactionReference = body.contains(Regex("(?:transaction|txn|ref)(?:\\s+(?:id|no|number|:))?\\s*[:\\s]?\\s*[a-zA-Z0-9]+", RegexOption.IGNORE_CASE))
        
        // STEP 4: Check for merchant indicators - very reliable for transaction detection
        
        // Look for merchant name indicators
        val hasMerchantIndicator = body.contains(Regex("(?:at|@|to|with|towards|through|merchant|store|shop)\\s+[A-Z][A-Za-z0-9\\s.&'-]+", RegexOption.IGNORE_CASE))
        
        // Check for known merchants in body
        val hasKnownMerchant = KNOWN_MERCHANTS.any { merchant ->
            // Use word boundary for more accurate detection
            body.contains(Regex("\\b$merchant\\b", RegexOption.IGNORE_CASE))
        }
        
        // STEP 5: Check for card usage indicators
        
        // Card usage is a very reliable transaction indicator
        val hasCardUsageIndicator = body.contains(Regex("(?:card\\s+(?:ending|[Xx*]{4,6})\\s+(?:with\\s+)?\\d{4}|card\\s+used|card\\s+transaction|card\\s+purchase|POS\\s+transaction)", RegexOption.IGNORE_CASE))
        
        // STEP 6: Decision matrix - combine indicators for final decision
        
        // Strong positive indicators - these are highly reliable for transaction detection
        if (hasStrongDebitIndicator && (hasAmountWithCurrency || hasAmountKeyword)) {
            Log.d(TAG, "SMS accepted: Strong debit indicator with amount")
            captureLog("SMS accepted: Strong debit indicator with amount")
            return true
        }
        
        if (hasTransactionReference && (hasAmountWithCurrency || hasAmountKeyword)) {
            Log.d(TAG, "SMS accepted: Transaction reference with amount")
            captureLog("SMS accepted: Transaction reference with amount")
            return true
        }
        
        if (hasMerchantIndicator && (hasAmountWithCurrency || hasAmountKeyword)) {
            Log.d(TAG, "SMS accepted: Merchant indicator with amount")
            captureLog("SMS accepted: Merchant indicator with amount")
            return true
        }
        
        if (hasKnownMerchant && (hasAmountWithCurrency || hasAmountKeyword || hasTransactionReference)) {
            Log.d(TAG, "SMS accepted: Known merchant with amount/reference")
            captureLog("SMS accepted: Known merchant with amount/reference")
            return true
        }
        
        if (hasCardUsageIndicator && (hasAmountWithCurrency || hasAmountKeyword)) {
            Log.d(TAG, "SMS accepted: Card usage with amount")
            captureLog("SMS accepted: Card usage with amount")
            return true
        }
        
        // Final check for amounts with debit-related keywords
        // Enhanced amount pattern detection
        val amountPatterns = listOf(
            // Common Indian formats
            Regex("(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?"),
            // Amounts without currency symbol but with word boundaries
            Regex("\\b\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?\\b"),
            // "Sent/paid/debited X.XX" pattern - with word boundaries for more precision
            Regex("\\b(?:sent|paid|debited|deducted|withdrew)\\s+(?:Rs\\.?|INR|₹)?\\s*\\d+(?:\\.\\d{1,2})?\\b"),
            // Amount followed by "from" or "to" - strong transaction indicator
            Regex("\\b\\d+(?:\\.\\d{1,2})?\\s+(?:from|to|by)\\b")
        )
        
        // Check for both amount patterns and debit indicators
        val containsAmountPattern = amountPatterns.any { pattern ->
            pattern.find(body) != null
        }
        
        // Check if this contains debit-related keywords (with stricter boundary checking)
        val isDebitRelated = DEBIT_PATTERNS.any { pattern -> 
            // Use word boundary for more accurate matching of key terms
            body.contains(Regex("\\b$pattern\\b", RegexOption.IGNORE_CASE))
        }
        
        // Combined check for amount + debit indicators
        if (containsAmountPattern && isDebitRelated) {
            Log.d(TAG, "SMS accepted: Contains amount pattern and debit indicators")
            captureLog("SMS accepted: Contains amount pattern and debit indicators")
            return true
        }
        
        // Check if this is a balance update or account info (which we want to ignore)
        // Apply stricter checking with word boundaries
        val isBalanceUpdate = BALANCE_PATTERNS.any { pattern ->
            body.contains(Regex("\\b(?:$pattern)\\b", RegexOption.IGNORE_CASE))
        }
        
        // Balance updates with transaction indicators might still be transactions
        if (isBalanceUpdate && !isDebitRelated && !containsAmountPattern) {
            Log.d(TAG, "SMS rejected: Appears to be just a balance update without transaction indicators")
            captureLog("SMS rejected: Appears to be just a balance update without transaction indicators")
            return false
        }
        
        // Final relaxed check: For bank senders, if it contains an amount pattern, consider it a transaction
        // even if it doesn't have a clear debit keyword (catches edge cases)
        // BUT only if it doesn't have promotional indicators
        if (isBankSender && containsAmountPattern && 
            !isPromotionalMessage && !hasBankingPromotionalContext && !hasBalanceRequirementFormat &&
            !isLoanOfferMessage && !isGetCashPromotionalMessage &&
            // Additional safety check - large amounts with "Lacs/Lakhs/Crores" are likely offers, not transactions
            !body.contains(Regex("(?:Rs\\.?|INR|₹)\\s*\\d+\\s*(?:lac|lakh|crore)s?", RegexOption.IGNORE_CASE))) {
            
            Log.d(TAG, "SMS accepted: Contains amount pattern from official bank sender")
            captureLog("SMS accepted: Contains amount pattern from official bank sender")
            return true
        }
        
        // Log rejection for messages that don't match any patterns
        Log.d(TAG, "SMS rejected: No definitive transaction indicators found")
        captureLog("SMS rejected: No definitive transaction indicators found")
            return false
    }
    
    /**
     * Extracts transaction details from the SMS body
     * Enhanced with more robust amount detection patterns
     */
    private fun parseTransactionSms(sender: String, body: String): TransactionSms {
        // Extract amount - improved with more comprehensive pattern matching
        // Handles various currency formats (₹, Rs., INR, USD, $) with proper grouping
        val primaryAmountRegex = Regex(
            "(?:" +
                // Indian currency formats with multiple variations
                "(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)|" +
                
                // "Amount/AMT/etc of Rs X" format with optional 'of'
                "(?:amount|amt|charge|bill|fee)\\s+(?:of\\s+)?(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)|" +
                
                // USD with possible INR conversion - handles parentheses
                "(?:USD|\\$)\\s*([\\d,.]+)(?:\\s*(?:\\((?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\))?)|" +
                
                // Verb + amount patterns (spent/paid/etc)
                "(?:spent|paid|send|sent|paying|received|credited|debited|deducted|withdrawn)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)|" +
                
                // Preposition + amount patterns
                "(?:for|of|with|worth)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)|" +
                
                // "Sent/Paid X from/to" format (captures direction)
                "(?:sent|paid)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:from|to)|" +
                
                // "INR X.XX is debited" format (common in some banks)
                "(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:is|has been|was|were)\\s+(?:debited|deducted|withdrawn|sent|paid)|" +
                
                // Value X.XX followed by DR (Debit) indicator
                "(?:val|value|amt|amount)?\\s*(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+DR|" +
                
                // X.XX- format (negative amount with trailing minus sign)
                "([\\d,]+(?:\\.\\d{1,2})?)-(?:\\s|$)|" +
                
                // In some messages, the amount is isolated within quotes
                "['\"](?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)['\"]" +
            ")"
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
                    if (amount > 0) {
                        captureLog("Amount extracted (primary): $value → $amount")
                        break
                    }
                }
            }
        }
        
        // If amount is still 0, try the secondary regex
        if (amount == 0.0) {
            val secondaryAmountRegex = Regex(
                // Enhanced patterns for difficult-to-extract amounts
                "(?:" +
                    // Numbers that look like amounts (on boundaries with decimal points)
                    "(?<=\\s|^)(?:Rs\\.?|INR|₹)?\\s*(\\d[\\d,]+(?:\\.\\d{1,2})?)(?=\\s|$)|" +
                    
                    // Numbers with exactly 2 decimal places (classic currency format)
                    "(?<=\\s|^)(\\d+\\.\\d{2})(?=\\s|$)|" +
                    
                    // Any number after currency symbol (even without space)
                    "(?:Rs|INR|₹)(?:\\s*)([\\d,]+(?:\\.\\d{1,2})?)|" +
                    
                    // Tightly-formatted amounts (no space between currency and digits)
                    "(?:Rs|INR|₹)([\\d,]+(?:\\.\\d{1,2})?)|" +
                    
                    // Balance after transaction (extract both deducted amount and new balance)
                    "(?:balance|bal)\\s+(?:is|:)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)" +
                ")"
            )
            
            val secondaryMatch = secondaryAmountRegex.find(body)
            if (secondaryMatch != null) {
                for (i in 1 until secondaryMatch.groupValues.size) {
                    val value = secondaryMatch.groupValues[i]
                    if (value.isNotEmpty()) {
                        amount = value.replace(",", "").toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            captureLog("Amount extracted (secondary): $value → $amount")
                            break
                        }
                    }
                }
            }
        }
        
        // Last resort: extract all plausible amounts and choose most likely one
        if (amount == 0.0) {
            // All possible amounts in the SMS
            val numberPattern = Regex("\\b(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\b")
            val allAmounts = numberPattern.findAll(body)
                .map { 
                    val rawValue = it.groupValues[1]
                    val parsedValue = rawValue.replace(",", "").toDoubleOrNull() ?: 0.0
                    Pair(rawValue, parsedValue)
                }
                .filter { (_, value) -> value > 10.0 && value < 1000000.0 } // Reasonable amount range
                .toList()
            
            if (allAmounts.isNotEmpty()) {
                // Select the most likely amount
                // Strategy: Prefer amounts with decimal points, or the largest amount if no decimals
                val amountsWithDecimals = allAmounts.filter { (raw, _) -> raw.contains(".") }
                
                if (amountsWithDecimals.isNotEmpty()) {
                    // Get the most likely decimal amount (largest)
                    val (rawValue, parsedValue) = amountsWithDecimals.maxByOrNull { (_, value) -> value } ?: allAmounts.first()
                    amount = parsedValue
                    captureLog("Amount extracted (fallback decimal): $rawValue → $amount")
                } else {
                    // If no decimal amounts, take the largest number
                    val (rawValue, parsedValue) = allAmounts.maxByOrNull { (_, value) -> value } ?: allAmounts.first()
                    amount = parsedValue
                    captureLog("Amount extracted (fallback largest): $rawValue → $amount")
                }
            }
        }
        
        // Properly determine transaction type (expense/debit vs income/credit)
        val isDebitTransaction = body.contains("debited", ignoreCase = true) || 
                               body.contains("spent", ignoreCase = true) || 
                               body.contains("debit", ignoreCase = true) || 
                               body.contains("dr", ignoreCase = true) ||
                               body.contains("withdrawn", ignoreCase = true) ||
                               body.contains("sent", ignoreCase = true) ||
                               body.contains("paid", ignoreCase = true) ||
                               body.contains("purchase", ignoreCase = true) ||
                               body.contains("payment", ignoreCase = true) ||
                               body.contains("deducted", ignoreCase = true)
        
        // Make amount negative ONLY for debit transactions 
        if (isDebitTransaction && amount > 0) {
            amount = -amount
            captureLog("Converting to negative amount for debit transaction: $amount")
        }
        
        // Enhanced merchant detection with comprehensive pattern matching
        val merchant = extractMerchantName(sender, body)
        
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
     * Extracts a reference number from the transaction SMS to help identify duplicate transactions
     */
    private fun extractReferenceNumber(body: String): String {
        // Common patterns for transaction reference numbers
        val referencePatterns = listOf(
            // Common reference number formats with labels
            Regex("(?:ref|reference|txn|transaction|utr|rrn|rrn no|ref no|txn id|upi ref|imps|neft|rtgs)[#:.-]?\\s*([a-zA-Z0-9]{6,})", RegexOption.IGNORE_CASE),
            
            // Reference in UPI format (often starts with numbers)
            Regex("(?:upi:)([0-9]{6,})", RegexOption.IGNORE_CASE),
            
            // Standalone alphanumeric code that looks like a reference
            Regex("\\b([A-Z0-9]{6,})\\b")
        )
        
        // Try each pattern in order of priority
        for (pattern in referencePatterns) {
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        // If no reference found, create a hash from the essential transaction details
        // This helps identify duplicates even without an explicit reference number
        val amountMatch = Regex("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)").find(body)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "") ?: ""
        
        // Extract date and time if present (for more precise fingerprinting)
        val dateTimePattern = Regex("\\b(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})(?:\\s*,?\\s*(\\d{1,2}:\\d{1,2}(?::\\d{1,2})?))?\\b")
        val dateTimeMatch = dateTimePattern.find(body)
        val dateTimeStr = if (dateTimeMatch != null) {
            val date = dateTimeMatch.groupValues[1]
            val time = dateTimeMatch.groupValues[2]
            if (time.isNotEmpty()) "$date $time" else date
        } else {
            // Use minute-level precision for timestamps if no date/time in SMS
            // This allows multiple transactions to same merchant in same hour but different minutes
            (System.currentTimeMillis() / (1000 * 60)).toString() // Minute-level granularity
        }
        
        // Include more of the message for better differentiation
        // Hash the entire message instead of just the first 20 chars
        val messageHash = body.hashCode()
        
        return "GEN-${amount}-${dateTimeStr}-${messageHash}"
    }
    
    /**
     * Enhanced merchant name extraction with comprehensive pattern matching
     * Handles various transaction formats including merchants, person-to-person, and edge cases
     */
    private fun extractMerchantName(sender: String, body: String): String {
        var merchantName = ""
        val lowerBody = body.lowercase()
        
        // First check for known merchants - prioritize exact matches, then partial
        // Enhanced with boundary checking for more accurate detection
        for (merchant in KNOWN_MERCHANTS) {
            // First try exact word match with word boundaries
            val exactPattern = Regex("\\b$merchant\\b", RegexOption.IGNORE_CASE)
            if (exactPattern.containsMatchIn(body)) {
                merchantName = merchant
                captureLog("Found exact known merchant: $merchantName")
                break
            }
            
            // Then try general contains for longer merchant names
            if (merchant.length > 4 && body.contains(merchant, ignoreCase = true)) {
                merchantName = merchant
                captureLog("Found known merchant (partial match): $merchantName")
                break
            }
        }
        
        // If known merchant not found, try different extraction patterns
        if (merchantName.isEmpty()) {
            
            // --------- PRIORITY 0: BANK-SPECIFIC PATTERNS ---------
            
            // Map of bank-specific patterns for better targeting
            val bankPatterns = mapOf(
                // HDFC Bank patterns
                "HDFC" to listOf(
                    // HDFC common format with "To NAME" on a separate line
                    Regex("To\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s*\\n+\\s*On|\\s+On|\\s+Info)", RegexOption.IGNORE_CASE),
                    // HDFC UPI format
                    Regex("(?:sent to|paid to|UPI-|UPI:|UPI>)\\s*([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|-|$|\\n)", RegexOption.IGNORE_CASE)
                ),
                
                // SBI Bank patterns
                "SBI" to listOf(
                    // SBI common formats
                    Regex("(?:paid|sent)\\s+to\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE),
                    Regex("(?:to|towards)\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s+(?:on|dt|upi|a/c)|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE),
                    // SBI credit card format
                    Regex("(?:purchase|payment) at\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE)
                ),
                
                // ICICI Bank patterns
                "ICICI" to listOf(
                    // ICICI common format
                    Regex("transferred to\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE),
                    // ICICI new format with "credited" pattern (high priority)
                    Regex("([A-Za-z0-9@_.\\s&'\\-]+?)\\s+credited", RegexOption.IGNORE_CASE),
                    // ICICI UPI format
                    Regex("UPI-([A-Za-z0-9@_.\\s&'\\-]+?)(?:-|\\s|$)", RegexOption.IGNORE_CASE),
                    // ICICI VPA format
                    Regex("VPA-([A-Za-z0-9@_.\\s&'\\-]+?)(?:-|\\s|$)", RegexOption.IGNORE_CASE)
                ),
                
                // Axis Bank patterns
                "AXIS" to listOf(
                    Regex("(?:sent to|paid to|UPI/)\\s*([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE),
                    Regex("(?:purchase|payment) at\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE)
                ),
                
                // Kotak Bank patterns
                "KOTAK" to listOf(
                    Regex("(?:paid|transfer) to\\s+([A-Za-z0-9@_.\\s&'\\-]+?)(?:\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE),
                    Regex("UPI-([A-Za-z0-9@_.\\s&'\\-]+?)(?:-|\\s|$)", RegexOption.IGNORE_CASE)
                )
            )
            
            // Detect which bank's SMS this is
            var detectedBank = ""
            for (bank in listOf("HDFC", "SBI", "ICICI", "AXIS", "KOTAK")) {
                if (sender.contains(bank, ignoreCase = true) || body.contains(bank, ignoreCase = true)) {
                    detectedBank = bank
                    break
                }
            }
            
            // Try bank-specific patterns first if a bank is detected
            if (detectedBank.isNotEmpty()) {
                val patterns = bankPatterns[detectedBank] ?: emptyList()
                for (pattern in patterns) {
                    pattern.find(body)?.let {
                        // Ensure we got a non-empty, reasonably-sized capture
                        val candidate = it.groupValues[1].trim()
                        if (candidate.isNotEmpty() && candidate.length >= 2 && candidate.length <= 50) {
                            merchantName = candidate
                            captureLog("Extracted using $detectedBank bank pattern: $merchantName")
                            return@let
                        }
                    }
                }
            }
            
            // --------- PRIORITY 1: GENERIC NAME PATTERNS ---------
            
            // Only proceed with generic patterns if bank-specific patterns didn't find anything
            if (merchantName.isEmpty()) {
                // Collection of recipient patterns to handle various formats
                val recipientPatterns = listOf(
                    // To NAME (with various boundaries) - with stricter boundaries to avoid footer text
                    Regex("(?:to|towards|credited to|sent to|paid to|transferred to|trf to)\\s+([A-Za-z0-9@_.\\s&'\\-]{2,50})(?:\\s+(?:on|dt|ref|upi|not|bal|thru|\\.)|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Sent/paid/transferred - to/from - NAME pattern with amount context
                    Regex("(?:sent|paid|transferred|debited|credited)\\s+(?:rs\\.?|inr|₹)?\\s*[\\d,.]+\\s+(?:to|from)\\s+([A-Za-z0-9@_.\\s&'\\-]{2,50})(?:\\s+(?:on|dt|\\.)|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // To NAME on a new line or followed by date pattern (cleaner boundaries)
                    Regex("(?:^|\\n)\\s*To\\s+([A-Za-z0-9@_.\\s&'\\-]{2,50})(?:\\s*\\n|\\s+On|\\s*$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Beneficiary: NAME pattern
                    Regex("(?:benef(?:iciary)?|payee|recipient|account holder|a/c holder)\\s*(?::|is|name)?\\s*([A-Za-z0-9@_.\\s&'\\-]{2,50})(?:\\s+(?:on|dt|upi|\\.)|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // For NEFT/IMPS transfers to NAME
                    Regex("(?:neft|imps|rtgs|upi)\\s+(?:to|transfer to|txn to|payment to)\\s+([A-Za-z0-9@_.\\s&'\\-]{2,50})(?:\\s+|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Info: transfer to NAME format
                    Regex("(?:info|information|details|remarks|ref|reference)\\s*:?\\s*(?:transfer|trf|payment|sent|paid)\\s+(?:to|towards)\\s+([A-Za-z0-9@_.\\s&'\\-]{2,50})(?:\\s+(?:on|dt|upi|\\.)|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE)
                )
                
                // Try all recipient patterns - apply additional validation on matches
                for (pattern in recipientPatterns) {
                    pattern.find(body)?.let {
                        val candidate = it.groupValues[1].trim()
                        
                        // Additional validation to filter out noise
                        if (candidate.isNotEmpty() && 
                            candidate.length >= 2 && 
                            candidate.length <= 50 && 
                            !candidate.equals("account", ignoreCase = true) &&
                            !candidate.equals("customer", ignoreCase = true) &&
                            !candidate.matches(Regex("\\d+", RegexOption.IGNORE_CASE))) {
                            
                            merchantName = candidate
                        captureLog("Extracted using recipient pattern: $merchantName")
                        return@let
                        }
                    }
                }
            }
            
            // --------- PRIORITY 2: "AT" PATTERNS ---------
            
            // Pattern for card/POS transactions - enhanced with tighter boundaries
            if (merchantName.isEmpty() && (lowerBody.contains("card") || lowerBody.contains("pos") || 
                                          lowerBody.contains("purchase") || lowerBody.contains("spent"))) {
                                          
                val atPatterns = listOf(
                    // Standard "at POS" format
                    Regex("(?:at|@)\\s+(?:pos\\s+|ecom\\s+|ecom-)?([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:on|dt|\\.|$)|\\.|,|;|\\n|for)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Card was used at format
                    Regex("(?:card|debit card|credit card|your card) (?:was )?(?:used|charged|debited) (?:at|@|on|in|for)\\s+([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:on|dt|\\.|$)|\\.|,|;|\\n)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Purchase at/from format
                    Regex("(?:purchase|payment|spent|shopping|bought|order) (?:at|@|on|in|from|with)\\s+([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:on|dt|\\.|$)|\\.|,|;|\\n)", 
                          RegexOption.IGNORE_CASE)
                )
                
                // Iterate until we find a match or exhaust all patterns
                var found = false
                for (pattern in atPatterns) {
                    if (found) continue
                    
                    pattern.find(body)?.let {
                        val candidate = it.groupValues[1].trim()
                        if (candidate.isNotEmpty() && candidate.length >= 2 && candidate.length <= 50) {
                            merchantName = candidate
                            captureLog("Extracted using 'at/card' pattern: $merchantName")
                            found = true
                        }
                    }
                }
            }
            
            // Pattern for bill payments and subscriptions
            if (merchantName.isEmpty() && (lowerBody.contains("bill") || lowerBody.contains("payment") || 
                                          lowerBody.contains("ecs") || lowerBody.contains("premium") || 
                                          lowerBody.contains("mandate") || lowerBody.contains("subscription"))) {
                                          
                val forPatterns = listOf(
                    // Standard "for" format with tighter boundaries
                    Regex("(?:for|towards|against|paying for)\\s+([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:bill|payment|on|dt|\\.|$)|\\.|,|;|\\n)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Bill payment formats
                    Regex("(?:bill|payment|subscription|premium) (?:for|of|to|towards)\\s+([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:on|dt|\\.|$)|\\.|,|;|\\n)", 
                          RegexOption.IGNORE_CASE),
                    
                    // ECS/mandate formats
                    Regex("(?:ecs|mandate|auto-debit|auto debit|auto payment)\\s*(?:to|for|from|:|-)?\\s*([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:on|dt|\\.|$)|\\.|,|;|\\n)", 
                          RegexOption.IGNORE_CASE)
                )
                
                // Iterate until we find a match or exhaust all patterns
                var found = false
                for (pattern in forPatterns) {
                    if (found) continue
                    
                    pattern.find(body)?.let {
                        val candidate = it.groupValues[1].trim()
                        if (candidate.isNotEmpty() && candidate.length >= 2 && candidate.length <= 50) {
                            merchantName = candidate
                            captureLog("Extracted using 'for/bill' pattern: $merchantName")
                            found = true
                        }
                    }
                }
            }
            
            // --------- PRIORITY 3: UPI ID EXTRACTION ---------
            
            // Enhanced UPI ID extraction with better format handling and validation
            if (merchantName.isEmpty()) {
                // UPI format detectors with strict boundaries
                val upiPatterns = listOf(
                    // Standard UPI format with boundaries
                    Regex("(?:to|towards|via|using|thru|through|from)\\s+([a-zA-Z0-9_.\\-]{3,25})@([a-zA-Z0-9_.]{2,10})(?:\\s|\\.|,|;|$|\\n)", 
                          RegexOption.IGNORE_CASE),
                    
                    // UPI in remarks/ref with boundaries
                    Regex("(?:remarks|info|ref|upi|vpa)\\s*:?\\s*(?:upi\\/)?([a-zA-Z0-9_.\\-]{3,25})@([a-zA-Z0-9_.]{2,10})(?:\\s|\\.|,|;|$|\\n)", 
                          RegexOption.IGNORE_CASE),
                    
                    // UPI/NAME@provider format with clear boundaries
                    Regex("upi\\/([a-zA-Z0-9_.\\-]{3,25})@([a-zA-Z0-9_.]{2,10})(?:\\s|\\.|,|;|$|\\n|-)", 
                          RegexOption.IGNORE_CASE),
                    
                    // Named UPI: format
                    Regex("(?:to|via|thru|using|by) ([a-zA-Z0-9\\s&'\\.-]{2,25})(?:\\s+|:)(?:upi id|vpa|virtual address|upi)?\\s*:?\\s*([a-zA-Z0-9_.\\-]{2,25})@([a-zA-Z0-9_.]{2,10})", 
                          RegexOption.IGNORE_CASE)
                )
                
                // Try regular UPI patterns first
                var upiMatch: MatchResult? = null
                var namedUpiMatch: MatchResult? = null
                
                for (i in 0 until upiPatterns.size - 1) {
                    val pattern = upiPatterns[i]
                    pattern.find(body)?.let {
                        upiMatch = it
                        return@let
                    }
                }
                
                // Try named UPI pattern separately (has different capture groups)
                upiPatterns.last().find(body)?.let {
                    namedUpiMatch = it
                }
                
                // Handle named UPI match (has name in first capture group)
                if (namedUpiMatch != null) {
                    val displayName = namedUpiMatch!!.groupValues[1].trim()
                    val userName = namedUpiMatch!!.groupValues[2].trim()
                    val provider = namedUpiMatch!!.groupValues[3].trim()
                    
                    // Prefer display name if it's meaningful
                    if (displayName.length > 2 && !displayName.matches(Regex("\\d+"))) {
                        merchantName = displayName.replaceFirstChar { it.uppercase() }
                        captureLog("Extracted from named UPI pattern: $merchantName (UPI: $userName@$provider)")
                    } else {
                        // Fallback to formatted UPI ID
                        merchantName = "$userName@$provider"
                        captureLog("Extracted full UPI ID: $merchantName")
                    }
                }
                // Process regular UPI match
                else if (upiMatch != null) {
                    val userName = upiMatch!!.groupValues[1].trim()
                    val provider = upiMatch!!.groupValues[2].trim()
                    
                    // Filter out obvious non-names in userName
                    if (userName.contains(Regex("rent|bill|recharge|fee|payment|loan|service|order", RegexOption.IGNORE_CASE))) {
                        // Purpose-based UPI, format as title
                        merchantName = userName.split(".", "_", "-").joinToString(" ") { word ->
                            if (word.length > 1) word.replaceFirstChar { it.uppercase() } else word.uppercase()
                        }
                        captureLog("Extracted purpose from UPI ID: $merchantName")
                    } 
                    // Check for known merchants in the username
                    else if (KNOWN_MERCHANTS.any { merchant -> userName.contains(merchant, ignoreCase = true) }) {
                        val matchedMerchant = KNOWN_MERCHANTS.first { merchant -> 
                            userName.contains(merchant, ignoreCase = true) 
                        }
                        merchantName = matchedMerchant.replaceFirstChar { it.uppercase() }
                        captureLog("Matched known merchant in UPI ID: $merchantName (from $userName@$provider)")
                    } 
                    // Format personal UPI IDs nicely
                    else {
                        // Format as "Username (UPI)" if username seems meaningful
                        merchantName = if (userName.length > 2 && !userName.matches(Regex("\\d+"))) {
                            // Break by common separators and format
                            userName.split(".", "_", "-").joinToString(" ") { word ->
                                if (word.length > 1) word.replaceFirstChar { it.uppercase() } else word.uppercase()
                            } + " (UPI)"
                        } else {
                            // Keep full UPI ID if username is too short or numeric
                            "$userName@$provider"
                        }
                        captureLog("Formatted UPI ID: $merchantName")
                    }
                }
            }
            
            // --------- PRIORITY 4: REMARKS/PURPOSE EXTRACTION ---------
            
            // Extract purpose or remarks for transactions
            if (merchantName.isEmpty()) {
                val remarksPatterns = listOf(
                    // Remarks: PURPOSE pattern with tight boundaries
                    Regex("(?:remarks|remark|reason|purpose|note|memo|description)\\s*:?\\s*([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // USING APP/SERVICE pattern (for mobile banking)
                    Regex("(?:using|via|through)\\s+([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s+(?:on|app|service|banking|UPI)|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE),
                    
                    // for ECS/mandate - PURPOSE pattern
                    Regex("(?:ecs|mandate|auto-debit|auto debit|autopay)\\s*-\\s*([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s|\\.\\s|,\\s|;\\s|\\n|$)", 
                          RegexOption.IGNORE_CASE)
                )
                
                for (pattern in remarksPatterns) {
                    pattern.find(body)?.let {
                        val remark = it.groupValues[1].trim()
                        // Only use remarks if they seem meaningful (not just "UPI" or "Ref")
                        if (remark.length > 3 && 
                            !remark.matches(Regex("(?:upi|ref|no|number|id|txn|transaction|upi ref)", RegexOption.IGNORE_CASE)) &&
                            !remark.equals("payment", ignoreCase = true) &&
                            !remark.equals("transfer", ignoreCase = true)) {
                                
                            merchantName = remark
                            captureLog("Extracted from remarks: $merchantName")
                            return@let
                        }
                    }
                }
            }
            
            // --------- PRIORITY 5: TRANSACTION TYPE PATTERNS ---------
            
            // Find merchant names after payment keywords, looking for capitalized words
            if (merchantName.isEmpty()) {
                val paymentKeywords = listOf("sent", "paid", "debited", "payment", "transfer", "spent", "transferred")
                
                var found = false
                for (keyword in paymentKeywords) {
                    if (found) continue
                    
                    if (lowerBody.contains(keyword)) {
                        // Look for words starting with capital letter after the keyword
                        val keywordIndex = lowerBody.indexOf(keyword)
                        if (keywordIndex != -1 && keywordIndex + keyword.length < body.length) {
                            // Look within 50 chars after the keyword
                            val afterKeyword = body.substring(keywordIndex + keyword.length, 
                                                             minOf(body.length, keywordIndex + keyword.length + 50))
                            
                            // Look for capitalized words of reasonable length
                            val capitalizedPattern = Regex("\\b([A-Z][A-Za-z0-9'\\.-]{2,}(?:\\s+[A-Za-z0-9'\\.-]+){0,3})\\b")
                        val match = capitalizedPattern.find(afterKeyword)
                            
                        if (match != null) {
                                val candidate = match.groupValues[1].trim()
                                // Filter out common non-merchant words
                                if (!candidate.matches(Regex("(?:Rs|INR|USD|On|At|From|To|Info)", RegexOption.IGNORE_CASE))) {
                                    merchantName = candidate
                            captureLog("Extracted capitalized word after '$keyword': $merchantName")
                                    found = true
                        }
                    }
                        }
                    }
                }
            }
            
            // --------- PRIORITY 6: SPECIAL TRANSACTION FORMATS ---------
            
            // Extract from "thru UPI:" pattern (common in some banks)
            if (merchantName.isEmpty() && lowerBody.contains("thru upi:")) {
                // Check if there's a name before "thru UPI:"
                val beforeUpi = body.substring(0, lowerBody.indexOf("thru upi:"))
                val capitalizedPattern = Regex("([A-Z][A-Za-z0-9\\s&'\\.-]{2,})")
                val matches = capitalizedPattern.findAll(beforeUpi).toList()
                if (matches.isNotEmpty()) {
                    // Take the last capitalized word before "thru UPI:"
                    merchantName = matches.last().groupValues[1].trim()
                    captureLog("Extracted capitalized word before 'thru UPI:': $merchantName")
                }
            }
            
            // --------- PRIORITY 7: FALLBACK STRATEGIES ---------
            
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
            
            // Fallback 2: Extract after "via" keyword (mobile banking, payment services)
            if (merchantName.isEmpty() && (lowerBody.contains(" via ") || lowerBody.contains(" using "))) {
                val viaPattern = Regex("(?:via|using)\\s+([A-Za-z0-9\\s&'\\.-]{2,50})(?:\\s|\\.|,|;|\\n|$)", RegexOption.IGNORE_CASE)
                viaPattern.find(body)?.let {
                    val candidate = it.groupValues[1].trim()
                    // Don't use generic terms
                    if (!candidate.matches(Regex("(?:upi|neft|imps|internet banking|net banking|debit card|credit card)", RegexOption.IGNORE_CASE))) {
                        merchantName = candidate
                        captureLog("Extracted from 'via' pattern: $merchantName")
                    } else {
                        merchantName = "" // Reset if it's a generic term
                    }
                }
            }
            
            // Fallback 3: Generic transaction type with context-aware selection
            if (merchantName.isEmpty()) {
                // Use more detailed categories based on multiple keywords
                merchantName = when {
                    // UPI Transactions
                    lowerBody.contains("upi") && lowerBody.contains("p2p") -> "UPI P2P Transfer"
                    lowerBody.contains("upi") && lowerBody.contains("p2m") -> "UPI Merchant Payment"
                    lowerBody.contains("upi") -> "UPI Payment"
                    
                    // Card Transactions with detailed categorization
                    lowerBody.contains("pos") && lowerBody.contains("international") -> "International POS"
                    lowerBody.contains("pos") -> "POS Transaction"
                    lowerBody.contains("ecom") && lowerBody.contains("card") -> "Online Card Payment"
                    lowerBody.contains("card") && lowerBody.contains("pin") -> "Chip & PIN Payment"
                    lowerBody.contains("contactless") -> "Contactless Payment"
                    (lowerBody.contains("debit") || lowerBody.contains("credit")) && lowerBody.contains("card") -> "Card Payment"
                    
                    // Cash Transactions
                    lowerBody.contains("atm") && lowerBody.contains("withdrawal") -> "ATM Withdrawal"
                    lowerBody.contains("cash") && lowerBody.contains("withdrawal") -> "Cash Withdrawal"
                    
                    // Bank Transfers
                    lowerBody.contains("neft") -> "NEFT Transfer"
                    lowerBody.contains("rtgs") -> "RTGS Transfer"
                    lowerBody.contains("imps") -> "IMPS Transfer"
                    
                    // Standing Instructions
                    lowerBody.contains("ecs") -> "ECS Debit"
                    lowerBody.contains("mandate") -> "Standing Instruction"
                    lowerBody.contains("auto") && (lowerBody.contains("debit") || lowerBody.contains("payment")) -> "Auto Payment"
                    
                    // Bills & Subscriptions
                    lowerBody.contains("bill") && lowerBody.contains("electricity") -> "Electricity Bill"
                    lowerBody.contains("bill") && lowerBody.contains("water") -> "Water Bill"
                    lowerBody.contains("bill") && lowerBody.contains("gas") -> "Gas Bill"
                    lowerBody.contains("bill") && lowerBody.contains("telephone") -> "Telephone Bill"
                    lowerBody.contains("bill") && lowerBody.contains("mobile") -> "Mobile Bill"
                    lowerBody.contains("bill") && lowerBody.contains("internet") -> "Internet Bill"
                    lowerBody.contains("bill") -> "Bill Payment"
                    lowerBody.contains("subscription") -> "Subscription"
                    lowerBody.contains("premium") && lowerBody.contains("insurance") -> "Insurance Premium"
                    lowerBody.contains("premium") -> "Premium Payment"
                    
                    // Online/Mobile Banking
                    lowerBody.contains("mobile") && lowerBody.contains("banking") -> "Mobile Banking"
                    lowerBody.contains("internet") && lowerBody.contains("banking") -> "Internet Banking"
                    lowerBody.contains("netbanking") -> "Net Banking"
                    
                    // General Fallbacks
                    lowerBody.contains("payment") -> "Payment"
                    lowerBody.contains("purchase") -> "Purchase"
                    lowerBody.contains("spend") || lowerBody.contains("spent") -> "Purchase"
                    lowerBody.contains("transfer") || lowerBody.contains("trf") -> "Transfer"
                    
                    // Ultimate Fallback
                    else -> "Bank Transaction"
                }
                captureLog("Using context-aware transaction type: $merchantName")
            }
        }
        
        // --------- CLEANUP AND FORMATTING ---------
        
        // Clean up extracted merchant name
        merchantName = cleanupMerchantName(merchantName)
        
        return merchantName
    }
    
    /**
     * Cleans up and formats a merchant name for better display
     * Enhanced with more comprehensive regex patterns and smarter formatting
     */
    private fun cleanupMerchantName(name: String): String {
        if (name.isEmpty()) return "Unknown"
        
        // Keep UPI IDs and UPI references as is - special case handling
        if (name.contains("@") || name.startsWith("UPI") || name.startsWith("upi")) {
            // Standardize UPI capitalization
            return if (name.startsWith("upi", ignoreCase = true)) {
                "UPI" + name.substring(3)
            } else {
                name
            }
        }
        
        // Special handling for account numbers
        val accountPattern = Regex("(?:a/c|account|ac)\\s*(?:no\\.?)?\\s*(?:x+|\\*+)?(\\d+)", RegexOption.IGNORE_CASE)
        accountPattern.find(name)?.let {
            val accountNum = it.groupValues[1].trim()
            if (accountNum.isNotEmpty()) {
                return "Account ${accountNum.takeLast(4)}" // Show last 4 digits only
            }
        }
        
        // Check for just numeric strings with x's or *'s (masked account numbers)
        if (name.matches(Regex("[x\\d\\*]+\\d+", RegexOption.IGNORE_CASE))) {
            val lastDigits = name.filter { it.isDigit() }.takeLast(4)
            if (lastDigits.isNotEmpty()) {
                return "Account $lastDigits" // Show last digits only
            }
        }
        
        // Check for bank transfer keywords with no proper recipient name
        if (name.contains(Regex("(?:neft|rtgs|imps|transfer|ben|benef)", RegexOption.IGNORE_CASE)) && 
            !name.contains(Regex("[A-Za-z]{5,}", RegexOption.IGNORE_CASE))) {
            // This appears to be a bank transfer with no proper name
            // Extract any account number if present
            val digits = name.filter { it.isDigit() }.takeLast(4)
            
            // Determine transfer type
            val transferType = when {
                name.contains("imps", ignoreCase = true) -> "IMPS"
                name.contains("rtgs", ignoreCase = true) -> "RTGS"
                name.contains("neft", ignoreCase = true) -> "NEFT"
                name.contains("upi", ignoreCase = true) -> "UPI"
                name.contains("ben", ignoreCase = true) -> "BEN"
                name.contains("benef", ignoreCase = true) -> "BENF"
                else -> "Bank"
            }
            
            return if (digits.isNotEmpty()) {
                "$transferType Account $digits"
            } else {
                "$transferType Transfer"
            }
        }
        
        // Check for known businesses first - if it's a known business, use standard format
        for (merchant in KNOWN_MERCHANTS) {
            if (name.contains(merchant, ignoreCase = true)) {
                return merchant.replaceFirstChar { it.uppercase() }
            }
        }
        
        // Detect if this might be a person name rather than a business
        val isProbablyPerson = !name.contains(
            Regex("\\b(?:ltd|limited|pvt|private|services|systems|corporation|solutions|technologies|store|shop|mall|center)\\b", 
            RegexOption.IGNORE_CASE)
        )
        
        // For person names, preserve more of the original format
        if (isProbablyPerson) {
            // For person names, keep full name but apply proper capitalization
            return name.split(" ").joinToString(" ") { word ->
                when {
                    // Single letter (like initial) - keep as uppercase
                    word.length == 1 -> word.uppercase()
                    
                    // Normal words - capitalize first letter
                    else -> word.replaceFirstChar { it.uppercase() }.substring(0, 1) + 
                           (if (word.length > 1) word.substring(1).lowercase() else "")
                }
            }
        }
        
        // Initialize with the original name
        var cleanName = name
        
        // STEP 1: Remove common noise words and patterns with word boundaries
        val noisePatterns = listOf(
            // Business types and suffixes
            "\\b(?:ltd|limited|pvt|private|india|services|systems|corporation|solutions|international|technologies)\\b",
            
            // Transaction-related noise
            "\\b(?:bal|balance|bal[:\\s]|ref|refno|your upi|upi ref|not u|not you|fwd|info|reference|txn)\\b",
            
            // Payment method noise
            "\\b(?:ecom|ecom-|pos\\s|pos-|via|using|through)\\b",
            
            // Common senders
            "\\b(?:hdfc|sbi|icici|axis|kotak)\\b"
        )
        
        // Apply each noise pattern with case insensitivity
        for (pattern in noisePatterns) {
            cleanName = cleanName.replace(Regex(pattern, RegexOption.IGNORE_CASE), " ")
        }
        
        // STEP 2: Remove common transaction patterns
        
        // Remove transaction numbers and IDs that might be included
        cleanName = cleanName.replace(Regex("\\b[A-Z0-9]{6,}\\b"), "")
        
        // Remove dates in common formats (DD/MM/YY, etc.)
        cleanName = cleanName.replace(Regex("\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}\\b"), "")
        
        // STEP 3: Clean up special characters and formatting issues
        
        // Replace multiple dots, dashes with a single space
        cleanName = cleanName.replace(Regex("[\\.\\-_]{2,}"), " ")
        
        // Replace single dots, dashes between words with spaces
        cleanName = cleanName.replace(Regex("(?<=\\w)[\\.\\-_](?=\\w)"), " ")
        
        // Remove any trailing punctuation
        cleanName = cleanName.replace(Regex("[\\.,:;\\-_]+$"), "")
        
        // STEP 4: Fix spacing issues
        
        // Normalize whitespace - replace all whitespace sequences with a single space
        cleanName = cleanName.replace(Regex("\\s+"), " ").trim()
        
        // STEP 5: Handle empty or too short results
        
        // If name became too short after cleanup or is empty, return a sensible default
        if (cleanName.length < 3 || cleanName.isBlank()) {
            // Try to extract at least the first word of the original name if it exists
            val firstWord = Regex("^([A-Za-z0-9]+)").find(name)?.groupValues?.get(1)
            
            return if (firstWord != null && firstWord.length >= 2) {
                // Capitalize the first word if available
                firstWord.replaceFirstChar { it.uppercase() }
            } else if (name.length >= 3) {
                // Use original name if it's reasonably sized
                name.trim()
            } else {
                // Last resort
                "Unknown"
            }
        }
        
        // STEP 6: Apply proper capitalization if not already capitalized
        
        // Check if name already has proper capitalization (contains at least one uppercase letter)
        if (!cleanName.contains(Regex("[A-Z]"))) {
            // Apply title case capitalization - capitalize first letter of each word
            cleanName = cleanName.split(" ").joinToString(" ") { word ->
                when {
                    // Properly handle acronyms (all caps) with length check to avoid capitalizing single letters
                    word.length > 1 && word.all { it.isLetter() && it.isUpperCase() } -> word.uppercase()
                    
                    // For normal words, capitalize first letter
                    word.length > 1 -> word[0].uppercaseChar() + word.substring(1).lowercase()
                    
                    // For single letters, uppercase them
                    word.length == 1 -> word.uppercase()
                    
                    // Empty string fallback
                    else -> ""
                }
            }
                } else {
            // Fix inconsistent capitalization for existing mixed-case words
            // This improves names that have random capitalization patterns
            cleanName = cleanName.split(" ").joinToString(" ") { word ->
                when {
                    // Keep acronyms as is
                    word.length > 1 && word.all { it.isLetter() && it.isUpperCase() } -> word
                    
                    // If word has mixed case but doesn't start with uppercase, fix it
                    word.length > 1 && !word[0].isUpperCase() -> word[0].uppercaseChar() + word.substring(1)
                    
                    // Otherwise keep as is
                    else -> word
                }
            }
        }
        
        return cleanName
    }
} 