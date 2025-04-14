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
     * Enhanced merchant name extraction with comprehensive pattern matching
     * Handles various transaction formats including merchants, person-to-person, and edge cases
     */
    private fun extractMerchantName(sender: String, body: String): String {
        var merchantName = ""
        val lowerBody = body.lowercase()

        // Try to find the transfer type (IMPS, NEFT, RTGS, UPI, etc.)
        var transferType = ""
        val transferTypePattern = Regex("\\b(imps|neft|rtgs|upi)\\b", RegexOption.IGNORE_CASE)
        transferTypePattern.find(body)?.let {
            transferType = it.groupValues[1].uppercase()
        }
        
        // Capture log function for diagnostic info
        fun captureLog(message: String) {
            Log.d(TAG, "Merchant extraction: $message")
        }

        if (merchantName.isEmpty()) {
            
            // --------- PRIORITY 0: BANK-SPECIFIC PATTERNS ---------
            
            // Map of bank-specific patterns for better targeting
            val bankPatterns = mapOf(
                // ... existing patterns ...
            )
            
            // ... existing extraction code ...
        }
        
        // --------- SPECIAL HANDLING FOR ACCOUNT-ONLY TRANSFERS ---------
        
        // If we only extracted an account number or couldn't extract anything meaningful,
        // try to create a better merchant name using transfer type and account information
        if (merchantName.isEmpty() || merchantName.matches(Regex("(?:a/c|account|ac|acc)[\\s#*xX]*\\d+", RegexOption.IGNORE_CASE))) {
            // Look for account numbers in the message
            val accountPattern = Regex("(?:a/c|account|ac)\\s*(?:[#*xX]+|\\*+)(\\d{4,})|(?:a/c|account|ac)\\s*(\\d{4,})|x{2,}(\\d{4,})", RegexOption.IGNORE_CASE)
            val accountMatch = accountPattern.find(body)
            
            if (accountMatch != null) {
                // Extract the account number's last 4-6 digits
                val accountNumber = (accountMatch.groupValues[1].takeIf { it.isNotEmpty() } 
                                   ?: accountMatch.groupValues[2].takeIf { it.isNotEmpty() }
                                   ?: accountMatch.groupValues[3])
                
                // Create a more descriptive merchant name
                val accountLast = accountNumber.takeLast(4)
                
                merchantName = if (transferType.isNotEmpty()) {
                    "$transferType Transfer - Acc. $accountLast"
                } else {
                    "Bank Transfer - Acc. $accountLast"
                }
                
                captureLog("Created descriptive name for account-only transfer: $merchantName")
            }
        }
        
        // Clean up the merchant name before returning
        return cleanupMerchantName(merchantName)
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