package com.moneypulse.app.ui.test

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.moneypulse.app.R
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.receiver.SmsReceiver
import com.moneypulse.app.util.NotificationHelper
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Debug activity for testing SMS parsing and transaction processing
 * This activity is only meant to be used during development
 */
@AndroidEntryPoint
class TestActivity : AppCompatActivity() {

    @Inject
    lateinit var smsReceiver: SmsReceiver
    
    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    // Test cases - collection of sample SMS messages
    private val testCases = mapOf(
        "HDFC Bank Transfer - Uber" to "Money Transfer:Rs 701.29 from HDFC Bank A/c **3974 on 10-02-24 to UBER INDIA SYSTEMS PRIVATE LIMITED UPI: 403138 Not you? Call 18002586161",
        "HDFC Bank Transfer - Person" to "Money Transfer:Rs 40.00 from HDFC Bank A/c **3974 on 12-02-24 to Nitin Chourasiya UPI: 4040 Not you? Call 18002586161",
        "HDFC Bank Transfer - Company" to "Money Transfer:Rs 10000.00 from HDFC Bank A/c **3974 on 13-02-24 to Nextbillion Technology Private Limited UPI: 40591 Not you? Call 18002586161",
        "SBI Card Purchase" to "You've spent Rs.359.00 on your SBI Card 52XX1234 at AMAZON RETAIL on 2024-02-10. Available limit: Rs. 45,000.",
        "ICICI Bank UPI" to "Rs.100.00 debited from A/c no. XX5678 on 15-Feb-24 using UPI-RAZORUPIIN. UPI Ref ICIC333456. Balance: Rs.24,560.98",
        "Axis Bank Online Shopping" to "INR 2,499.00 debited on Axis Bank XX7890 at FLIPKART. Avl Bal INR 15,345.60. For dispute call 1860 419 5555. 15-02-24 10:34:56",
        "PayTM UPI" to "Rs.50 paid to Swiggy from a/c xxxxx7890 on 15/02/24. UPI Ref 123456789. Balance Rs.1234.56. For queries, call 1800-1234-1234",
        "PhonePe UPI" to "Rs.750 successfully paid to Zomato using PhonePe. Ref: 987654321 Bal: Rs.2345.67",
        "American Express" to "Dear Customer, you've made a purchase of INR 5,400.00 at MYNTRA STORE using your AmEx Card ending 2468 on 15/02/2024.",
        "Google Pay" to "You paid Rs.125 to Sharma General Store. Transaction ID: UPI/123456789123/GPay. Merchant ref: 978979. Payment successful!"
    )
    
    // UI elements
    private lateinit var spinnerTestCases: Spinner
    private lateinit var textSmsContent: TextView
    private lateinit var btnTestManual: Button
    private lateinit var btnTestAuto: Button
    private lateinit var textResults: TextView
    
    // Current test case
    private var currentSmsContent: String = ""
    private var currentSender: String = "HDFCBK"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        
        // Init UI elements
        spinnerTestCases = findViewById(R.id.spinner_test_cases)
        textSmsContent = findViewById(R.id.text_sms_content)
        btnTestManual = findViewById(R.id.btn_test_manual)
        btnTestAuto = findViewById(R.id.btn_test_auto)
        textResults = findViewById(R.id.text_results)
        
        setupSpinner()
        setupButtons()
    }
    
    private fun setupSpinner() {
        // Create adapter for spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            testCases.keys.toList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTestCases.adapter = adapter
        
        // Handle selection
        spinnerTestCases.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val key = parent.getItemAtPosition(position) as String
                currentSmsContent = testCases[key] ?: ""
                // Set sender based on the test case
                currentSender = when {
                    key.contains("HDFC") -> "HDFCBK"
                    key.contains("SBI") -> "SBIINB"
                    key.contains("ICICI") -> "ICICIB"
                    key.contains("Axis") -> "AXISBK"
                    key.contains("PayTM") -> "PAYTMB"
                    key.contains("PhonePe") -> "PHONPE"
                    key.contains("American Express") -> "AMEXIN"
                    key.contains("Google Pay") -> "GPAYBN"
                    else -> "ALERTBK"
                }
                
                // Update SMS content display
                textSmsContent.text = currentSmsContent
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
        
        // Select first item
        if (testCases.isNotEmpty()) {
            spinnerTestCases.setSelection(0)
        }
    }
    
    private fun setupButtons() {
        // Test in manual mode
        btnTestManual.setOnClickListener {
            testResults("MANUAL MODE TEST")
            performManualTest()
        }
        
        // Test in auto mode
        btnTestAuto.setOnClickListener {
            testResults("AUTOMATIC MODE TEST")
            performAutoTest()
        }
    }
    
    private fun performManualTest() {
        if (currentSmsContent.isBlank()) {
            appendResult("Error: No SMS content selected")
            return
        }
        
        val transaction = createTransactionFromSms()
        if (transaction != null) {
            // Show parsed details
            appendResult("Parsing results:")
            appendResult("- Sender: ${transaction.sender}")
            appendResult("- Amount: ${transaction.amount}")
            appendResult("- Merchant: ${transaction.merchantName}")
            
            // Show notification
            appendResult("\nTesting notification...")
            NotificationHelper.showTransactionNotification(this, transaction)
            appendResult("Notification displayed. Please check notification drawer.")
        } else {
            appendResult("Error: Could not parse SMS as transaction")
        }
    }
    
    private fun performAutoTest() {
        if (currentSmsContent.isBlank()) {
            appendResult("Error: No SMS content selected")
            return
        }
        
        val transaction = createTransactionFromSms()
        if (transaction != null) {
            // Show parsed details
            appendResult("Parsing results:")
            appendResult("- Sender: ${transaction.sender}")
            appendResult("- Amount: ${transaction.amount}")
            appendResult("- Merchant: ${transaction.merchantName}")
            
            // Simulate auto processing
            appendResult("\nSimulating automatic processing...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    transactionRepository.processNewTransactionSms(transaction)
                    CoroutineScope(Dispatchers.Main).launch {
                        appendResult("Transaction successfully saved to database")
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        appendResult("Error saving transaction: ${e.message}")
                    }
                }
            }
        } else {
            appendResult("Error: Could not parse SMS as transaction")
        }
    }
    
    private fun createTransactionFromSms(): TransactionSms? {
        // Use our existing SmsReceiver's functionality to parse the SMS
        val testId = "TEST-${UUID.randomUUID()}"
        
        // Check if this is a transaction SMS
        if (!smsReceiver.isTransactionSms(currentSender, currentSmsContent)) {
            appendResult("This SMS was not recognized as a transaction SMS")
            return null
        }
        
        // Parse the transaction
        return smsReceiver.parseTransactionDetails(currentSender, currentSmsContent)
    }
    
    private fun testResults(title: String) {
        textResults.text = "=== $title ===\n\n"
    }
    
    private fun appendResult(message: String) {
        textResults.append("\n$message")
    }
} 