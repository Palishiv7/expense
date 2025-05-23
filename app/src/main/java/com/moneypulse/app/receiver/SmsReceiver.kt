package com.moneypulse.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.moneypulse.app.BuildConfig
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.util.NotificationHelper
import com.moneypulse.app.util.PreferenceHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
        private const val MAX_DEBUG_LOGS = 500
        
        // Add method to retrieve logs
        fun getDebugLogs(): List<String> {
            return debugLogs.toList()
        }
        
        // Add method to clear logs
        fun clearDebugLogs() {
            debugLogs.clear()
        }
        
        // Pre-compiled regex patterns for performance optimization
        // OTP and verification patterns
        private val STRONG_OTP_PATTERN = Regex("\\botp\\b|\\bone[ -]?time[ -]?password\\b|\\bverification[ -]?code\\b|\\bsecurity[ -]?code\\b|\\bauth(?:entication)?[ -]?code\\b", RegexOption.IGNORE_CASE)
        private val NUMERIC_CODE_PATTERN = Regex("\\b\\d{4,8}\\b(?:(?:\\s+is|\\s+as|:)\\s+(?:your|the)\\s+(?:otp|code|password))|(?:code(?:\\s+is)?:?\\s+\\b\\d{4,8}\\b)")
        
        // Transaction identifier patterns
        private val BANK_CODE_PATTERN = Regex("^([A-Z]{2})-?([A-Z]+(?:BK|BNK|BANK|NBK|INB))$")
        private val FLEXIBLE_BANK_PREFIX_PATTERN = Regex("^([A-Z]{2,3})-([A-Z]+(?:BK|BNK|BANK|NBK|INB|NBK|MSG))$")
        
        // Common patterns for promotional and balance messages
        private val BALANCE_REQUIREMENT_FORMAT = Regex("(?:maintain|average|minimum)\\s+(?:monthly|quarterly)?\\s+balance\\s+(?:of|:)\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)
        private val AMB_PATTERN = Regex("(?:AMB|Average Monthly Balance)\\s+(?:of|:)?\\s*(?:Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)
        
        // Banking promotional context patterns
        private val BANKING_PROMO_INVITE = Regex("(?:we|bank)\\s+(?:now\\s+)?invite\\s+you", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_DISCOVER = Regex("(?:discover|explore|know|learn|find out)\\s+(?:our|about|more)", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_BALANCE = Regex("(?:maintain|average|minimum)\\s+(?:monthly|quarterly|yearly)?\\s*balance", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_PROGRAM = Regex("priority\\s+banking|banking\\s+program", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_URL = Regex("(?:https?://|www\\.|bit\\.ly|tinyurl|axbk\\.io)[a-zA-Z0-9/?=&%._-]+", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_BENEFITS = Regex("(?:enjoy|get|avail|extend)\\s+(?:the\\s+)?benefits", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_MANAGER = Regex("(?:dedicated|relationship)\\s+manager", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_DISCOUNT = Regex("(?:up to|upto)\\s+\\d+%\\s+off", RegexOption.IGNORE_CASE)
        private val BANKING_PROMO_SERVICE = Regex("(?:enjoy|get|avail|discover)\\s+(?:a|the|our|exclusive|special|premium)\\s+(?:service|feature|benefit|program|offer)", RegexOption.IGNORE_CASE)
        
        // Promotional amount patterns
        private val PROMO_AMOUNT_PATTERN = Regex("(?:upto|up to|as much as)\\s+(?:\\d+%|Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)
        private val PROMO_DISCOUNT_PATTERN = Regex("(?:\\d+%)\\s+(?:off|discount|cashback)", RegexOption.IGNORE_CASE)
        private val MAINTAIN_BALANCE_PATTERN = Regex("(?:maintain)\\s+(?:an?|the)?\\s+(?:average|minimum)\\s+(?:monthly|quarterly)?\\s+balance\\s+of\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)
        
        // Loan offer patterns
        private val LOAN_TYPE_PATTERN = Regex("(?:personal|home|car|instant|quick|cash)\\s+loan", RegexOption.IGNORE_CASE)
        private val LOAN_WITH_PATTERN = Regex("loan\\s+(?:of|at|with|up to|upto)", RegexOption.IGNORE_CASE)
        private val LOAN_AMOUNT_PATTERN = Regex("(?:Rs\\.?|INR|₹)\\s*\\d+\\s*(?:lac|lakh|lacs|lakhs|l\\b)", RegexOption.IGNORE_CASE)
        private val LOAN_LARGE_AMOUNT = Regex("(?:Rs\\.?|INR|₹)\\s*\\d+\\s*(?:lac|lakh|crore)s?", RegexOption.IGNORE_CASE)
        private val LOAN_TERMS_PATTERN = Regex("exclusive\\s+rates|no\\s+paperwork|instant\\s+approval|apply\\s+now", RegexOption.IGNORE_CASE)
        private val LOAN_URL_PATTERN = Regex("(?:click|visit)\\s*(?::|->)?\\s*(?:https?://|www\\.|http)", RegexOption.IGNORE_CASE)
        
        // Debit transaction patterns
        private val STRONG_DEBIT_INDICATOR = Regex("\\b(?:debited|debit\\s+card|debit\\s+from|payment\\s+made|paid|purchase|spent)\\b", RegexOption.IGNORE_CASE)
        private val AMOUNT_WITH_CURRENCY = Regex("(?:Rs\\.?|INR|₹|USD|\\$)\\s*[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
        private val AMOUNT_KEYWORD = Regex("(?:amount|amt)(?:\\s+(?:of|:))?\\s+(?:Rs\\.?|INR|₹|USD|\\$)?\\s*[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
        private val TRANSACTION_REFERENCE = Regex("(?:transaction|txn|ref)(?:\\s+(?:id|no|number|:))?\\s*[:\\s]?\\s*[a-zA-Z0-9]+", RegexOption.IGNORE_CASE)
        private val MERCHANT_INDICATOR = Regex("(?:at|@|to|with|towards|through|merchant|store|shop)\\s+[A-Z][A-Za-z0-9\\s.&'-]+", RegexOption.IGNORE_CASE)
        private val CARD_USAGE_INDICATOR = Regex("(?:card\\s+(?:ending|[Xx*]{4,6})\\s+(?:with\\s+)?\\d{4}|card\\s+used|card\\s+transaction|card\\s+purchase|POS\\s+transaction)", RegexOption.IGNORE_CASE)
        
        // Balance and transaction type patterns
        private val DEBIT_KEYWORDS = Regex("\\b(?:debited|debit|paid|sent|spent|payment|purchase|transaction|txn)\\b", RegexOption.IGNORE_CASE)
        private val CREDIT_PATTERN = Regex("\\b(?:credited|received|deposited|added to|inward)\\b", RegexOption.IGNORE_CASE)
        private val DEBIT_PATTERN = Regex("\\b(?:debited|debit|paid|spent|payment|purchase|dr\\.?|withdraw|sent)\\b", RegexOption.IGNORE_CASE)
        private val BALANCE_UPDATE_PATTERN = Regex("\\b(?:balance|bal|statement|summary|info|update)\\b", RegexOption.IGNORE_CASE)
        
        // Amount patterns for final check
        private val AMOUNT_PATTERN_1 = Regex("(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?")
        private val AMOUNT_PATTERN_2 = Regex("\\b\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?\\b")
        private val AMOUNT_PATTERN_3 = Regex("\\b(?:sent|paid|debited|deducted|withdrew)\\s+(?:Rs\\.?|INR|₹)?\\s*\\d+(?:\\.\\d{1,2})?\\b")
        private val AMOUNT_PATTERN_4 = Regex("\\b\\d+(?:\\.\\d{1,2})?\\s+(?:from|to|by)\\b")
        
        // Transaction parsing patterns
        private val PRIMARY_AMOUNT_REGEX = Regex(
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
        
        private val SECONDARY_AMOUNT_REGEX = Regex(
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
        
        private val NUMBER_PATTERN = Regex("\\b(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\b")
        private val DEBIT_INDICATOR_WORDS = Regex("debited|spent|debit|dr|deducted|withdrawn|withdraw|sent|paid|payment|transfer|transferred|using card|purchase", RegexOption.IGNORE_CASE)
        
        // Reference pattern for transaction detection
        private val REFERENCE_PATTERNS = listOf(
            // Common reference number formats with labels
            Regex("(?:ref|reference|txn|transaction|utr|rrn|rrn no|ref no|txn id|upi ref|imps|neft|rtgs)[#:.-]?\\s*([a-zA-Z0-9]{6,})", RegexOption.IGNORE_CASE),
            
            // Reference in UPI format (often starts with numbers)
            Regex("(?:upi:)([0-9]{6,})", RegexOption.IGNORE_CASE),
            
            // Standalone alphanumeric code that looks like a reference
            Regex("\\b([A-Z0-9]{6,})\\b")
        )
        
        // Date and time pattern for reference generation
        private val DATE_TIME_PATTERN = Regex("\\b(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})(?:\\s*,?\\s*(\\d{1,2}:\\d{1,2}(?::\\d{1,2})?))?\\b")
        
        // Merchant name extraction patterns - constants for pattern building
        private val NAME_PATTERN = "[A-Za-z][A-Za-z0-9\\s&'\\.-]{2,48}"
        private val BOUNDARY_PATTERNS = "(?:\\s+(?:on|dt|ref|upi|not|bal|\\.)|\\s|\\.|,|;\\n|$)"
        
        // High priority merchant name patterns
        private val GENERAL_TO_PATTERN = Regex("(?:to|towards)\\s+($NAME_PATTERN)$BOUNDARY_PATTERNS", RegexOption.IGNORE_CASE)
        private val AXIS_UPI_PATTERN = Regex("UPI\\/P2A\\/\\d+\\/($NAME_PATTERN)", RegexOption.IGNORE_CASE)
        private val ICICI_CREDITED_PATTERN = Regex("([A-Z][A-Za-z0-9\\s&'\\.-]{2,50})\\s+credited", RegexOption.IGNORE_CASE)
        private val ICICI_PRECISE_PATTERN = Regex("on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2}\\s+([A-Z][A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s+credited", RegexOption.IGNORE_CASE)
        private val ICICI_EXACT_LOG_PATTERN = Regex("debited for\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,.]+\\s+on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2}\\s+([A-Z][A-Z]*[a-z]+(?:\\s+[A-Z][a-z]+)*)\\s+credited", RegexOption.IGNORE_CASE)
        private val ICICI_MERCHANT_ID_PATTERN = Regex("on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2}(?:;|:)\\s*([A-Za-z][A-Za-z0-9\\s&'\\.-]+?)\\s+([0-9]{4,})\\s+credited", RegexOption.IGNORE_CASE)
        private val WORD_BEFORE_CREDITED_PATTERN = Regex("\\b([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+)*)\\s+credited", RegexOption.IGNORE_CASE)
        private val ICICI_SEMICOLON_PATTERN = Regex("debited for\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,.]+\\s+on[^;]*;\\s*([A-Z][A-Za-z0-9\\s&'\\.-]{2,50})\\s+credited", RegexOption.IGNORE_CASE)
        private val ICICI_AGGRESSIVE_PATTERN = Regex("(?:Rs\\.?|INR|₹)\\s*[\\d,.]+(?:.*?)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\s+credited", RegexOption.IGNORE_CASE)
        private val ICICI_GENERAL_PATTERN = Regex("on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2}[^;]*;\\s*([A-Za-z][A-Za-z0-9\\s&'\\.-]{2,50})\\s+credited", RegexOption.IGNORE_CASE)
        
        // General patterns
        private val GENERAL_SENT_TO_PATTERN = Regex("(?:sent|paid|transfer(?:red)?)\\s+(?:to|from)\\s+($NAME_PATTERN)(?:\\s+(?:on|dt|via|UPI|Ref|ref)|\\s|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE)
        private val UPI_REF_PATTERN = Regex("UPI(?:\\:|\\s+Ref)\\s*\\d+\\.\\s*($NAME_PATTERN)", RegexOption.IGNORE_CASE)
        private val SMS_BLOCK_TEXT = Regex("SMS\\s+BLOCK", RegexOption.IGNORE_CASE)
        private val SMS_BLOCKUPI_TEXT = Regex("SMS\\s+BLOCKUPI", RegexOption.IGNORE_CASE)
        
        // Merchant validation patterns
        private val CAPITALIZED_NAME_PATTERN = Regex("\\b([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]+){0,3})\\b")
        private val NON_MERCHANT_TERMS = listOf(
            "account", "customer", "block", "sms", "cust id", "call", "your a/c", 
            "this is", "dial", "reference", "ref", "txn", "transaction", 
            "statement", "for", "net banking", "upi ref", "to a/c", "a/c no", 
            "account no", "balance", "bal", "not you", "msg", "banking", "info",
            "dispute", "complaint", "query", "detail", "amount", "acct", "help",
            "upi id", "vpa", "on", "dt", "please", "as per", "request", "www",
            "http", "avl bal", "password", "phone", "mobile", "email", "sms",
            "code", "otp", "verify", "confirmation", "ticket", "application"
        )
        private val ACCOUNT_PATTERN = Regex("(?:a/c|account|ac)\\s*(?:no\\.?)?\\s*(?:x+|\\*+)?(\\d+)", RegexOption.IGNORE_CASE)
        private val BUSINESS_TYPE_PATTERN = Regex("\\b(?:ltd|limited|pvt|private|services|systems|corporation|solutions|technologies|store|shop|mall|center)\\b", RegexOption.IGNORE_CASE)
        
        // Merchant name cleanup patterns
        private val NOISE_PATTERNS = listOf(
            // Business types and suffixes
            "\\b(?:ltd|limited|pvt|private|india|services|systems|corporation|solutions|international|technologies)\\b",
            
            // Transaction-related noise
            "\\b(?:bal|balance|bal[:\\s]|ref|refno|your upi|upi ref|not u|not you|fwd|info|reference|txn)\\b",
            
            // Payment method noise
            "\\b(?:ecom|ecom-|pos\\s|pos-|via|using|through)\\b",
            
            // Common senders and SMS block-related terms
            "\\b(?:hdfc|sbi|icici|axis|kotak|sms|block|cust|id|call|dial)\\b",
            
            // Phone numbers and instruction text
            "\\b\\d{10,}\\b", "\\b\\d{5,}\\s+to\\s+\\d{5,}\\b"
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
        
        // Capture log function
        private fun captureLog(message: String) {
            // Only log in debug builds
            if (!BuildConfig.DEBUG_LOGGING) return
            
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
    
    // Define a data class for SMS messages to be processed
    private data class SmsData(
        val sender: String,
        val body: String,
        val context: Context
    )
    
    // Create a dedicated CoroutineScope for the receiver with a SupervisorJob
    // This ensures that failures in one coroutine don't affect others
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Create a channel for SMS queue processing with buffer capacity
    private val smsProcessingChannel = Channel<SmsData>(Channel.BUFFERED)
    
    // Initialize the SMS processing worker
    init {
        // Start a single worker coroutine to process SMS messages sequentially
        receiverScope.launch {
            try {
                Log.d(TAG, "Starting SMS processing worker")
                
                // Process SMS messages one by one from the channel
                for (smsData in smsProcessingChannel) {
                    try {
                        processSmsMessage(smsData)
                        // Add a small delay to prevent excessive CPU usage if many messages arrive
                        delay(50)
                    } catch (e: Exception) {
                        // Catch exceptions for individual messages to prevent worker from stopping
                        Log.e(TAG, "Error processing SMS in queue: ${e.javaClass.simpleName}", e)
                        captureLog("Error in SMS processing queue: ${e.javaClass.simpleName}")
                    }
                }
            } catch (e: Exception) {
                // This should never happen since we catch exceptions inside the loop,
                // but add as a safety net to restart the worker if needed
                Log.e(TAG, "Critical error in SMS processing worker: ${e.javaClass.simpleName}", e)
                captureLog("Critical error in SMS worker - restarting")
                
                // Restart the worker after a delay
                delay(1000)
                receiverScope.launch {
                    for (smsData in smsProcessingChannel) {
                        try {
                            processSmsMessage(smsData)
                            delay(50)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in restarted worker: ${e.javaClass.simpleName}", e)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Improved check if a sender is from a financial institution
     * Uses multi-layered approach for greater flexibility with prefixed sender IDs
     */
    private fun isFinancialSender(sender: String): Boolean {
        // First, check exact matches for known senders
        if (BANK_SENDERS.contains(sender) || UPI_SENDERS.contains(sender)) {
            captureLog("Sender matched exact bank/UPI ID: $sender")
            return true
        }
        
        // Normalize the sender ID to uppercase
        val normalizedSender = sender.uppercase()
        
        // Regular expression for detecting bank-like patterns with prefixes
        if (BANK_CODE_PATTERN.matches(normalizedSender)) {
            captureLog("Sender matched bank pattern with prefix: $sender")
            return true
        }

        // Fallback: Split by hyphen and check known bank codes
        val bankId = if (normalizedSender.contains("-")) {
            normalizedSender.split("-").lastOrNull() ?: normalizedSender
        } else {
            normalizedSender
        }

        // Check for known bank IDs
        val knownBankIds = BANK_SENDERS + UPI_SENDERS
        val matches = knownBankIds.any { bankId.contains(it, ignoreCase = true) }
        
        if (matches) {
            captureLog("Sender matched bank ID after prefix removal: $bankId")
        }
        
        return matches
    }
    
    /**
     * Additional flexible check for financial senders that might use prefixes
     * Only used as a fallback after standard checks fail
     */
    private fun isFlexibleFinancialSender(sender: String): Boolean {
        // Normalize the sender ID to uppercase
        val normalizedSender = sender.uppercase()
        
        // Common prefix pattern (2-3 letters followed by hyphen)
        if (FLEXIBLE_BANK_PREFIX_PATTERN.matches(normalizedSender)) {
            val parts = normalizedSender.split("-", limit = 2)
            if (parts.size == 2) {
                val bankPart = parts[1]
                // Check if the part after the hyphen matches any known bank ID
                val bankMatch = BANK_SENDERS.any { bankPart.contains(it) || it.contains(bankPart) }
                if (bankMatch) {
                    captureLog("Flexible match: Sender '$sender' matched after prefix removal: $bankPart")
                    return true
                }
            }
        }
        
        // Try extracting the bank ID if there's a hyphen
        if (normalizedSender.contains("-")) {
            val parts = normalizedSender.split("-")
            // Try both parts (before and after hyphen)
            for (part in parts) {
                if (part.length >= 3) { // Only consider parts with reasonable length
                    val bankMatch = BANK_SENDERS.any { 
                        it.length >= 3 && (part.contains(it) || it.contains(part)) 
                    }
                    val upiMatch = UPI_SENDERS.any { 
                        it.length >= 3 && (part.contains(it) || it.contains(part))
                    }
                    
                    if (bankMatch || upiMatch) {
                        captureLog("Flexible match: Sender '$sender' contains known financial ID in part: $part")
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
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
                    
                    // Add to processing queue instead of processing immediately
                    // This prevents concurrent processing of multiple SMS messages
                    receiverScope.launch {
                        try {
                            smsProcessingChannel.send(SmsData(
                                sender = sanitizedSender,
                                body = sanitizedBody,
                                context = context
                            ))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error queuing SMS: ${e.javaClass.simpleName}", e)
                            captureLog("Error queuing SMS: ${e.javaClass.simpleName}")
                        }
                    }
                } catch (e: Exception) {
                    // Catch and log any exceptions during SMS extraction to avoid crashes
                    Log.e(TAG, "Error extracting SMS data: ${e.javaClass.simpleName}", e)
                    captureLog("Error extracting SMS data: ${e.javaClass.simpleName}")
                }
            }
        }
    }
    
    /**
     * Process a single SMS message from the queue
     * This runs sequentially to prevent race conditions
     */
    private suspend fun processSmsMessage(smsData: SmsData) {
        try {
            val sanitizedSender = smsData.sender
            val sanitizedBody = smsData.body
            val context = smsData.context
            
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
                    return
                }
                
                // Extract reference ID for duplicate detection
                val referenceId = extractReferenceNumber(sanitizedBody)
                
                // Check if this is a duplicate transaction we've seen recently
                if (isRecentDuplicate(referenceId)) {
                    Log.d(TAG, "Skipping duplicate transaction with reference: $referenceId")
                    captureLog("Skipping duplicate transaction: ${transaction.merchantName}, amount: [MASKED]")
                    return
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
                    
                    try {
                        // Now correctly called within a suspend function
                        transactionRepository.processNewTransactionSms(transaction)
                        Log.d(TAG, "Transaction successfully processed")
                        captureLog("Transaction successfully processed")
                    } catch (e: Exception) {
                        // Log any errors that occur during processing
                        Log.e(TAG, "Error processing transaction: ${e.javaClass.simpleName}")
                        captureLog("Error in transaction processing: ${e.javaClass.simpleName}")
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
            Log.e(TAG, "Error processing SMS: ${e.javaClass.simpleName}", e)
            captureLog("Error processing SMS: ${e.javaClass.simpleName}")
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
        
        // Add flexible matching as a fallback
        val isFlexibleFinancialSender = if (!isBankSender && !isPaymentAppSender) {
            isFlexibleFinancialSender(sender)
        } else {
            false
        }
        
        // Log which method matched the sender
        if (isBankSender) {
            captureLog("Sender matched as bank sender: $sender")
        } else if (isPaymentAppSender) {
            captureLog("Sender matched as payment app: $sender")
        } else if (isFlexibleFinancialSender) {
            captureLog("Sender matched using flexible matching: $sender")
        }
        
        // Only proceed if the message is from an official bank or payment service
        if (!isBankSender && !isPaymentAppSender && !isFlexibleFinancialSender) {
            Log.d(TAG, "SMS rejected: Not from a known financial sender - $sender")
            captureLog("SMS rejected: Not from a known financial sender - $sender")
            return false
        }
        
        // Unified pattern detector with prioritized checks
        
        // STEP 1: Quick rejection patterns - filter out obviously non-transaction messages
        
        // Reject OTP messages with high confidence patterns (using word boundaries)
        val isStrongOtpPattern = STRONG_OTP_PATTERN.containsMatchIn(body)
        
        if (isStrongOtpPattern) {
            Log.d(TAG, "SMS rejected: Strong OTP pattern detected")
            captureLog("SMS rejected: Strong OTP pattern detected")
            return false
        }
        
        // Check for numerical codes in the message - common in OTP texts
        val hasNumericCode = NUMERIC_CODE_PATTERN.containsMatchIn(body)
        
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
            (BANKING_PROMO_INVITE.containsMatchIn(body) ||
            BANKING_PROMO_DISCOVER.containsMatchIn(body) ||
            BANKING_PROMO_BALANCE.containsMatchIn(body) ||
            BANKING_PROMO_PROGRAM.containsMatchIn(body) ||
            
            // Check for promotional URL patterns - strong indicator of promotional content
            BANKING_PROMO_URL.containsMatchIn(body) ||
            
            // Banking program benefits indicators
            BANKING_PROMO_BENEFITS.containsMatchIn(body) ||
            BANKING_PROMO_MANAGER.containsMatchIn(body) ||
            BANKING_PROMO_DISCOUNT.containsMatchIn(body) ||
            
            // Check for promotional verbs with service nouns
            BANKING_PROMO_SERVICE.containsMatchIn(body)) &&
            
            // Combined with amount patterns that are likely promotional, not transactional
            (body.contains(Regex("(?:upto|up to|as much as)\\s+(?:\\d+%|Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:\\d+%)\\s+(?:off|discount|cashback)", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("(?:maintain)\\s+(?:an?|the)?\\s+(?:average|minimum)\\s+(?:monthly|quarterly)?\\s+balance\\s+of\\s+(?:Rs\\.?|INR|₹)\\s*[\\d,]+", RegexOption.IGNORE_CASE)))
        
        // Enhanced check for balance requirement format (reliably identifies banking promotional messages)
        val hasBalanceRequirementFormat = BALANCE_REQUIREMENT_FORMAT.containsMatchIn(body) || 
            AMB_PATTERN.containsMatchIn(body)
        
        // Specific check for loan offers with amount patterns - these are NOT transactions
        val isLoanOfferMessage = 
            (LOAN_TYPE_PATTERN.containsMatchIn(body) ||
            LOAN_WITH_PATTERN.containsMatchIn(body) ||
            // Add check for "Rs X Lacs/Lakhs/L" pattern which is very common in loan offers
            LOAN_AMOUNT_PATTERN.containsMatchIn(body)) &&
            // Combined with either amount patterns, promotional terms, or URLs
            (LOAN_LARGE_AMOUNT.containsMatchIn(body) ||
            LOAN_TERMS_PATTERN.containsMatchIn(body) ||
            LOAN_URL_PATTERN.containsMatchIn(body))
            
        // Additional check for promotional messages with "Get Cash" that aren't ATM withdrawals
        val isGetCashPromotionalMessage =
            body.contains(Regex("get\\s+cash", RegexOption.IGNORE_CASE)) &&
            !DEBIT_PATTERN.containsMatchIn(body) &&
            (body.contains(Regex("(?:exclusive|special|offer|instant|apply|click)", RegexOption.IGNORE_CASE)) ||
            BANKING_PROMO_URL.containsMatchIn(body))
            
        if (isPromotionalMessage || hasBankingPromotionalContext || isLoanOfferMessage || isGetCashPromotionalMessage) {
            Log.d(TAG, "SMS rejected: Appears to be a promotional message")
            captureLog("SMS rejected: Appears to be a promotional or loan offer message")
            return false
        }
        
        // Improved check for balance requirement format - these are typically promotional messages about banking programs
        // This is separate from the above check to provide more specific rejection reasons in logs
        if (hasBalanceRequirementFormat) {
            Log.d(TAG, "SMS rejected: Contains balance requirement format, likely a banking program promotion")
            captureLog("SMS rejected: Contains balance requirement format, likely a banking program promotion")
            return false
        }
        
        // STEP 2: Check for credit-only transactions (if the app only tracks expenses)
        
        // Enhanced credit transaction detection with more accurate pattern matching
        val isCreditOnlyTransaction = 
            // Message contains credit-related terms 
            CREDIT_PATTERN.containsMatchIn(body) && 
            // AND does NOT contain debit-related terms (critical for dual-mention transactions)
            !DEBIT_PATTERN.containsMatchIn(body)
        
        // For dual-mention transactions (common in ICICI and other banks): 
        // "Account debited ... recipient credited" - these ARE debit transactions
        // Enhanced to detect cases where "credited" doesn't have clear word boundaries
        val isDualTransactionFormat = 
            // Contains both debit and credit terms in any format
            body.contains(Regex("\\b(?:debited|debit)\\b", RegexOption.IGNORE_CASE)) &&
            // Simplified pattern to catch all credit variations including "NAME credited"
            body.contains(Regex("credited", RegexOption.IGNORE_CASE))
            
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
        
        // If message has balance requirement format but not clear transaction indicators, reject it
        if (hasBalanceRequirementFormat && !DEBIT_KEYWORDS.containsMatchIn(body)) {
            Log.d(TAG, "SMS rejected: Contains balance requirement format, not a transaction")
            captureLog("SMS rejected: Contains balance requirement format, not a transaction")
            return false
        }
        
        // Check for strong debit indicators with word boundary precision
        val hasStrongDebitIndicator = STRONG_DEBIT_INDICATOR.containsMatchIn(body)
        
        // Check for amount patterns with currency symbols - highly reliable indicator of transactions
        val hasAmountWithCurrency = AMOUNT_WITH_CURRENCY.containsMatchIn(body)
        
        // Check for word "amount" near numbers - strong transaction indicator
        val hasAmountKeyword = AMOUNT_KEYWORD.containsMatchIn(body)
        
        // Check for transaction IDs/reference numbers - reliable indicator of actual transactions
        val hasTransactionReference = TRANSACTION_REFERENCE.containsMatchIn(body)
        
        // STEP 4: Check for merchant indicators - very reliable for transaction detection
        
        // Look for merchant name indicators
        val hasMerchantIndicator = MERCHANT_INDICATOR.containsMatchIn(body)
        
        // Check for known merchants in body
        val hasKnownMerchant = KNOWN_MERCHANTS.any { merchant ->
            // Use word boundary for more accurate detection
            body.contains(Regex("\\b$merchant\\b", RegexOption.IGNORE_CASE))
        }
        
        // STEP 5: Check for card usage indicators
        
        // Card usage is a very reliable transaction indicator
        val hasCardUsageIndicator = CARD_USAGE_INDICATOR.containsMatchIn(body)
        
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
            AMOUNT_PATTERN_1,
            AMOUNT_PATTERN_2,
            AMOUNT_PATTERN_3,
            AMOUNT_PATTERN_4
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
            !LOAN_LARGE_AMOUNT.containsMatchIn(body)) {
            
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
        
        var amount = 0.0
        
        // Try primary amount regex first
        val primaryMatch = PRIMARY_AMOUNT_REGEX.find(body)
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
            val secondaryMatch = SECONDARY_AMOUNT_REGEX.find(body)
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
            val allAmounts = NUMBER_PATTERN.findAll(body)
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
        val isDebitTransaction = DEBIT_INDICATOR_WORDS.containsMatchIn(body)
        
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
        // Try each pattern in order of priority
        for (pattern in REFERENCE_PATTERNS) {
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        // If no reference found, create a hash from the essential transaction details
        // This helps identify duplicates even without an explicit reference number
        val amountMatch = AMOUNT_PATTERN_1.find(body)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "") ?: ""
        
        // Extract date and time if present (for more precise fingerprinting)
        val dateTimeMatch = DATE_TIME_PATTERN.find(body)
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
        
        captureLog("Extracting merchant name from: $body")
        
        // --------- HIGHEST PRIORITY: SPECIAL CASE PATTERNS ---------
        
        // These patterns have the highest confidence and override all others
        
        // 1. High priority "To [NAME]" pattern (works for many banks)
        GENERAL_TO_PATTERN.find(body)?.let {
            val candidate = it.groupValues[1].trim()
            if (isValidMerchantCandidate(candidate, 50, body)) {
                merchantName = candidate
                captureLog("HIGH PRIORITY - Extracted from general To pattern: $merchantName")
                return merchantName
            }
        }
        
        // 2. Axis Bank UPI/P2A pattern (highest priority)
        AXIS_UPI_PATTERN.find(body)?.let {
            val candidate = it.groupValues[1].trim()
            if (isValidMerchantCandidate(candidate, 50, body)) {
                merchantName = candidate
                captureLog("HIGH PRIORITY - Extracted from Axis UPI/P2A pattern: $merchantName")
                return merchantName
            }
        }
        
        // 3. ICICI Bank "[NAME] credited" pattern (highest priority)
        // Enhanced to handle name formats with or without boundaries
        ICICI_CREDITED_PATTERN.find(body)?.let {
            val candidate = it.groupValues[1].trim()
            if (isValidMerchantCandidate(candidate, 50, body)) {
                merchantName = candidate
                captureLog("HIGH PRIORITY - Extracted from ICICI credited pattern: $merchantName")
                return merchantName
            }
        }
        
        // New precise pattern for ICICI format without semicolon (highest priority)
        // Targets the format "on DD-MMM-YY NAME credited" seen in the logs
        ICICI_PRECISE_PATTERN.find(body)?.let {
            val candidate = it.groupValues[1].trim()
            if (isValidMerchantCandidate(candidate, 50, body)) {
                merchantName = candidate
                captureLog("HIGH PRIORITY - Extracted from ICICI precise date pattern: $merchantName")
                return merchantName
            }
        }
        
        // 3b. ICICI Bank specific debited/credited format - handles cases like "debited for Rs X; NAME credited"
        if (sender.contains("ICICI", ignoreCase = true) || body.contains("ICICI", ignoreCase = true)) {
            // Direct extraction for the exact format in the logs
            // This specifically targets formats like "ICICI Bank Acct XX804 debited for Rs 5.00 on 15-Apr-25 SHIVAM PALIWAL credited"
            ICICI_EXACT_LOG_PATTERN.find(body)?.let {
                val candidate = it.groupValues[1].trim() 
                if (isValidMerchantCandidate(candidate, 50, body)) {
                    merchantName = candidate
                    captureLog("HIGH PRIORITY - Extracted from ICICI exact log pattern: $merchantName")
                    return merchantName
                }
            }
            
            // Format with merchant name and ID: "on DATE; NAME ID credited"
            ICICI_MERCHANT_ID_PATTERN.find(body)?.let {
                // Combine merchant name and ID for better identification
                val nameComponent = it.groupValues[1].trim()
                val merchantId = it.groupValues[2].trim()
                val candidate = "$nameComponent $merchantId".trim()
                
                if (isValidMerchantCandidate(candidate, 50, body)) {
                    merchantName = candidate
                    captureLog("HIGH PRIORITY - Extracted from ICICI merchant+ID credited pattern: $merchantName")
                    return merchantName
                }
            }
            
            // Super aggressive pattern - directly look for capitalized names before "credited"
            // This is highly targeted at the exact format we're seeing in the logs
            WORD_BEFORE_CREDITED_PATTERN.findAll(body).toList().lastOrNull()?.let {
                val candidate = it.groupValues[1].trim()
                if (candidate.length >= 4 && isValidMerchantCandidate(candidate, 50, body)) {
                    merchantName = candidate
                    captureLog("HIGH PRIORITY - Extracted name before credited: $merchantName")
                    return merchantName
                }
            }
            
            // Keep original format with semicolon for backward compatibility
            // Format with semicolon: "debited for Rs X on DATE; NAME credited"
            ICICI_SEMICOLON_PATTERN.find(body)?.let {
                val candidate = it.groupValues[1].trim() 
                if (isValidMerchantCandidate(candidate, 50, body)) {
                    merchantName = candidate
                    captureLog("HIGH PRIORITY - Extracted from ICICI semicolon pattern: $merchantName")
                    return merchantName
                }
            }
            
            // Add a fallback pattern that tries to extract any name right before "credited"
            // This is a more aggressive approach but with proper validation should be reliable
            ICICI_AGGRESSIVE_PATTERN.find(body)?.let {
                val candidate = it.groupValues[1].trim()
                if (isValidMerchantCandidate(candidate, 50, body)) {
                    merchantName = candidate
                    captureLog("HIGH PRIORITY - Extracted from ICICI aggressive fallback pattern: $merchantName")
                    return merchantName
                }
            }
            
            // General fallback pattern for any format where a name appears before "credited"
            // This will catch various ICICI formats we haven't specifically handled
            ICICI_GENERAL_PATTERN.find(body)?.let {
                val candidate = it.groupValues[1].trim()
                if (isValidMerchantCandidate(candidate, 50, body)) {
                    merchantName = candidate
                    captureLog("HIGH PRIORITY - Extracted from ICICI general credited pattern: $merchantName")
                    return merchantName
                }
            }
        }
        
        // 4. General "sent to/paid to [NAME]" pattern (works across many banks)
        GENERAL_SENT_TO_PATTERN.find(body)?.let {
            val candidate = it.groupValues[1].trim()
            if (isValidMerchantCandidate(candidate, 40, body)) {
                merchantName = candidate
                captureLog("HIGH PRIORITY - Extracted from general sent to pattern: $merchantName")
                return merchantName
            }
        }
        
        // 5. Common UPI reference format (works for many UPI transactions)
        UPI_REF_PATTERN.find(body)?.let {
            val candidate = it.groupValues[1].trim()
            if (isValidMerchantCandidate(candidate, 50, body)) {
                merchantName = candidate
                captureLog("HIGH PRIORITY - Extracted from UPI Ref pattern: $merchantName")
                return merchantName
            }
        }
        
        // 6. Special handling for SMS BLOCK patterns - extract from text before the block instruction
        if (SMS_BLOCK_TEXT.containsMatchIn(body)) {
            // Find text before the SMS BLOCK instruction
            val blockIndex = lowerBody.indexOf("sms block")
            if (blockIndex > 20) { // Ensure there's enough text before the SMS BLOCK
                val beforeBlock = body.substring(0, blockIndex)
                
                // Try to find a name pattern in the text before SMS BLOCK
                // First look for a pattern like "NAME credited"
                ICICI_CREDITED_PATTERN.find(beforeBlock)?.let {
                    val candidate = it.groupValues[1].trim()
                    if (isValidMerchantCandidate(candidate, 50, beforeBlock)) {
                        merchantName = candidate
                        captureLog("SMS BLOCK special - Found name before block: $merchantName")
                        return merchantName
                    }
                }
                
                // Then try to find a "to NAME" pattern
                GENERAL_TO_PATTERN.find(beforeBlock)?.let {
                    val candidate = it.groupValues[1].trim()
                    if (isValidMerchantCandidate(candidate, 50, beforeBlock)) {
                        merchantName = candidate
                        captureLog("SMS BLOCK special - Found 'to NAME' before block: $merchantName")
                        return merchantName
                    }
                }
            }
        }
        
        // 7. Special handling for SMS BLOCKUPI patterns (specific to Axis Bank)
        if (SMS_BLOCKUPI_TEXT.containsMatchIn(body)) {
            // Find text before the SMS BLOCKUPI instruction
            val blockIndex = lowerBody.indexOf("sms blockupi")
            if (blockIndex > 20) { // Ensure there's enough text before the SMS BLOCKUPI
                val beforeBlock = body.substring(0, blockIndex)
                
                // First try to find UPI/P2A pattern which is very reliable
                AXIS_UPI_PATTERN.find(beforeBlock)?.let {
                    val candidate = it.groupValues[1].trim()
                    if (isValidMerchantCandidate(candidate, 50, beforeBlock)) {
                        merchantName = candidate
                        captureLog("SMS BLOCKUPI special - Found UPI/P2A pattern: $merchantName")
                        return merchantName
                    }
                }
                
                // Try to find any capitalized name pattern as fallback
                val matches = CAPITALIZED_NAME_PATTERN.findAll(beforeBlock).toList()
                if (matches.isNotEmpty()) {
                    // Take the last capitalized name as it's likely to be the merchant
                    val candidate = matches.last().groupValues[1].trim()
                    if (isValidMerchantCandidate(candidate, 50, beforeBlock)) {
                        merchantName = candidate
                        captureLog("SMS BLOCKUPI special - Found capitalized name: $merchantName")
                        return merchantName
                    }
                }
            }
        }
        
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
            
            // Common regex pattern components for consistency
            val upiIdPattern = "(?:[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*@[A-Za-z0-9]+)"
            val merchantNamePattern = "[A-Za-z0-9@_.\\s&'\\-]{2,50}"
            val boundaryPattern = "(?:\\s+(?:on|dt|ref|upi|bal|\\.)|\\.\\s|,\\s|;\\s|\\n|$)"
            
            // --------- PRIORITY 0: BANK-SPECIFIC PATTERNS ---------
            
            // Map of bank-specific patterns for better targeting
            val bankPatterns = mapOf(
                // HDFC Bank patterns
                "HDFC" to listOf(
                    // HDFC common format with "To NAME" on a separate line
                    Regex("To\\s+($merchantNamePattern)(?:\\s*\\n+\\s*On|\\s+On|\\s+Info)", RegexOption.IGNORE_CASE),
                    // HDFC sent to format
                    Regex("(?:Sent|sent)\\s+(?:Rs\\.?|INR|₹)?\\s*[\\d,.]+\\s+(?:from|From)\\s+[A-Za-z0-9\\s&'\\.-]+\\s+(?:to|To)\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // HDFC UPI format
                    Regex("(?:sent to|paid to|UPI-|UPI:|UPI>)\\s*($merchantNamePattern)(?:\\s|\\.|,|;|-|$|\\n)", RegexOption.IGNORE_CASE)
                ),
                
                // SBI Bank patterns
                "SBI" to listOf(
                    // SBI common formats
                    Regex("(?:paid|sent)\\s+to\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    Regex("(?:to|towards)\\s+($merchantNamePattern)(?:\\s+(?:on|dt|upi|a/c)|\\.|,|;|$|\\n)", RegexOption.IGNORE_CASE),
                    // SBI credit card format
                    Regex("(?:purchase|payment) at\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // SBI IMPS format
                    Regex("IMPS/P2A/($merchantNamePattern)/", RegexOption.IGNORE_CASE)
                ),
                
                // ICICI Bank patterns
                "ICICI" to listOf(
                    // ICICI new format with "credited" pattern (highest priority) - moved to the top
                    Regex("($merchantNamePattern)\\s+credited", RegexOption.IGNORE_CASE),
                    // ICICI common format
                    Regex("transferred to\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // ICICI "to NAME" pattern
                    Regex("to\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // ICICI UPI format
                    Regex("UPI[-:]($merchantNamePattern)(?:-|\\s|\\.|,|$)", RegexOption.IGNORE_CASE),
                    // ICICI VPA format
                    Regex("VPA-($merchantNamePattern)(?:-|\\s|$)", RegexOption.IGNORE_CASE)
                ),
                
                // Axis Bank patterns
                "AXIS" to listOf(
                    // Axis UPI/P2A format (highest priority)
                    Regex("UPI\\/P2A\\/\\d+\\/($merchantNamePattern)", RegexOption.IGNORE_CASE),
                    // Axis to/towards format
                    Regex("(?:to|towards)\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // Axis sent to/paid to format
                    Regex("(?:sent to|paid to)\\s*($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // Axis purchase/payment at format
                    Regex("(?:purchase|payment) at\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    // General UPI format (fallback)
                    Regex("UPI[:\\/]($merchantNamePattern)(?:\\s|\\.|,|;|-|$)", RegexOption.IGNORE_CASE)
                ),
                
                // Other banks follow similar pattern structure...
                "KOTAK" to listOf(
                    Regex("Sent\\s+(?:Rs\\.?|INR|₹)?\\s*[\\d,.]+\\s+from\\s+Kotak\\s+Bank\\s+AC\\s+.+\\s+to\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    Regex("(?:paid|transfer) to\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    Regex("UPI-($merchantNamePattern)(?:-|\\s|$)", RegexOption.IGNORE_CASE)
                ),
                
                "INDIAN" to listOf(
                    Regex("debited\\s+(?:Rs\\.?|INR|₹)?\\s*[\\d,.]+\\s+(?:on|dt)?\\s+[\\d\\-/]+\\s+to\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE)
                ),
                
                "YES" to listOf(
                    Regex("(?:sent|paid|transferred|debited)\\s+(?:to|towards)\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    Regex("UPI[-:\\/]($merchantNamePattern)(?:-|\\s|$)", RegexOption.IGNORE_CASE)
                ),
                
                "BOB" to listOf(
                    Regex("(?:sent|paid|transferred|debited)\\s+(?:to|towards)\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE),
                    Regex("UPI/($merchantNamePattern)(?:/|\\s|$)", RegexOption.IGNORE_CASE)
                ),
                
                "PNB" to listOf(
                    Regex("(?:sent|paid|transferred|debited)\\s+(?:to|towards)\\s+($merchantNamePattern)$boundaryPattern", RegexOption.IGNORE_CASE)
                )
            )
            
            // Helper function to attempt extraction using a pattern
            fun extractFromPattern(pattern: Regex, body: String, description: String): String? {
                val matchResult = pattern.find(body)
                if (matchResult != null) {
                    val candidate = matchResult.groupValues[1].trim()
                    if (isValidMerchantCandidate(candidate, 50, body)) {
                        captureLog("Extracted using $description: $candidate")
                        return candidate
                    }
                }
                return null
            }
            
            // Detect which bank's SMS this is
            var detectedBank = ""
            for (bank in listOf("HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "INDIAN", "YES", "BOB", "PNB")) {
                if (sender.contains(bank, ignoreCase = true) || body.contains(bank, ignoreCase = true)) {
                    detectedBank = bank
                    break
                }
            }
            
            // Try bank-specific patterns first if a bank is detected
            if (detectedBank.isNotEmpty()) {
                val patterns = bankPatterns[detectedBank] ?: emptyList()
                for (pattern in patterns) {
                    extractFromPattern(pattern, body, "$detectedBank bank pattern")?.let {
                        merchantName = it
                        return merchantName  // Return immediately after finding valid merchant
                    }
                }
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
            return when {
                name.startsWith("upi", ignoreCase = true) -> "UPI" + name.substring(3)
                else -> name
            }
        }
        
        // Filter out SMS BLOCK related text if it somehow got included
        if (SMS_BLOCK_TEXT.containsMatchIn(name) || SMS_BLOCKUPI_TEXT.containsMatchIn(name)) {
            // Extract the part before SMS BLOCK
            val blockIndex = name.indexOf("SMS", ignoreCase = true)
            if (blockIndex > 2) {
                val cleanedName = name.substring(0, blockIndex).trim()
                if (cleanedName.length >= 3) {
                    return cleanupMerchantName(cleanedName) // Recursively clean the filtered name
                }
            }
        }
        
        // Special handling for account numbers
        ACCOUNT_PATTERN.find(name)?.let {
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
        val isProbablyPerson = !BUSINESS_TYPE_PATTERN.containsMatchIn(name)
        
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
        for (pattern in NOISE_PATTERNS) {
            cleanName = cleanName.replace(pattern, " ")
        }
        
        // STEP 2: Remove common transaction patterns
        
        // Remove transaction numbers and IDs that might be included
        cleanName = cleanName.replace(Regex("\\b[A-Z0-9]{6,}\\b"), "")
        
        // Remove dates in common formats (DD/MM/YY, etc.)
        cleanName = cleanName.replace(DATE_TIME_PATTERN, "")
        
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
    
    /**
     * Helper function to determine if a candidate merchant name is likely not a valid merchant
     * Filters out common false positives and noise
     */
    private fun isLikelyNotMerchant(candidate: String): Boolean {
        val lowerCandidate = candidate.lowercase()
        
        // Check for common non-merchant terms
        for (term in NON_MERCHANT_TERMS) {
            val termPattern = Regex("\\b$term\\b", RegexOption.IGNORE_CASE)
            if (termPattern.containsMatchIn(lowerCandidate)) {
                captureLog("Rejected merchant candidate '$candidate' - contains non-merchant term '$term'")
                return true
            }
        }
        
        // Check if candidate contains too many numbers (likely a reference number)
        val digitCount = candidate.count { it.isDigit() }
        if (digitCount > candidate.length * 0.4 || digitCount >= 5) {
            captureLog("Rejected merchant candidate '$candidate' - too many digits")
            return true
        }
        
        // Check if candidate is too long to be a reasonable merchant name
        if (candidate.length > 40) {
            captureLog("Rejected merchant candidate '$candidate' - too long")
            return true
        }
        
        // Check if candidate is too short to be a reasonable merchant name
        if (candidate.length < 3) {
            captureLog("Rejected merchant candidate '$candidate' - too short")
            return true
        }
        
        // Check if candidate has no letters (likely just numbers or symbols)
        if (!candidate.any { it.isLetter() }) {
            captureLog("Rejected merchant candidate '$candidate' - no letters")
            return true
        }
        
        // Check if candidate looks like a phone number
        if (candidate.matches(Regex("\\d{3,}\\s*to\\s*\\d{3,}"))) {
            captureLog("Rejected merchant candidate '$candidate' - looks like phone number instruction")
            return true
        }
        
        // This is probably a valid merchant
        return false
    }
    
    /**
     * Helper function to validate a merchant candidate with consistent rules
     * Centralizes merchant validation logic to eliminate duplicate code
     */
    private fun isValidMerchantCandidate(candidate: String, maxLength: Int = 50, bodyText: String = ""): Boolean {
        // Basic validation checks
        if (candidate.isEmpty()) {
            return false
        }
        
        if (candidate.length < 2 || candidate.length > maxLength) {
            captureLog("Invalid merchant candidate '$candidate' - invalid length")
            return false
        }
        
        // Check if this is after SMS BLOCK instruction - only if bodyText is provided
        if (bodyText.isNotEmpty()) {
            val lowerBodyText = bodyText.lowercase()
            val blockIndex = lowerBodyText.indexOf("sms block")
            if (blockIndex > -1) {
                val candidateIndex = lowerBodyText.indexOf(candidate.lowercase())
                if (candidateIndex > blockIndex) {
                    captureLog("Invalid merchant candidate '$candidate' - appears after SMS BLOCK")
                    return false
                }
            }
        }
        
        // Check if it's a pure numeric string (likely a phone number)
        if (candidate.all { it.isDigit() }) {
            captureLog("Invalid merchant candidate '$candidate' - only digits")
            return false
        }
        
        // Check if it's likely not a merchant using comprehensive checks
        if (isLikelyNotMerchant(candidate)) {
            return false
        }
        
        return true
    }
} 