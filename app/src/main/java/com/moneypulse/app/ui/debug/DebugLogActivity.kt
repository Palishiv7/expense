package com.moneypulse.app.ui.debug

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.moneypulse.app.R
import com.moneypulse.app.receiver.SmsReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug activity to view SMS processing logs in real-time
 * This helps diagnose issues with transaction detection without needing ADB access
 */
class DebugLogActivity : AppCompatActivity() {
    
    private lateinit var textViewLogs: TextView
    private lateinit var scrollView: ScrollView
    private var isAutoscrolling = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)
        
        // Setup UI
        textViewLogs = findViewById(R.id.textViewLogs)
        scrollView = findViewById(R.id.scrollViewLogs)
        
        // Set title
        title = getString(R.string.debug_logs_title)
        
        // Button to clear logs
        findViewById<Button>(R.id.buttonClearLogs).setOnClickListener {
            SmsReceiver.clearDebugLogs()
            updateLogDisplay()
        }
        
        // Button to export logs (for sharing)
        findViewById<Button>(R.id.buttonExportLogs).setOnClickListener {
            shareLogsAsText()
        }
        
        // Button to toggle auto-scrolling
        findViewById<Button>(R.id.buttonToggleScroll).setOnClickListener {
            isAutoscrolling = !isAutoscrolling
            it.isSelected = isAutoscrolling
        }
        
        // Start automatic log updates
        startLogUpdates()
    }
    
    private fun startLogUpdates() {
        lifecycleScope.launch {
            while (true) {
                updateLogDisplay()
                delay(1000) // Update every second
            }
        }
    }
    
    private fun updateLogDisplay() {
        val logs = SmsReceiver.getDebugLogs()
        val logText = if (logs.isEmpty()) {
            getString(R.string.debug_waiting)
        } else {
            logs.joinToString("\n")
        }
        
        textViewLogs.text = logText
        
        // Auto-scroll to bottom if enabled
        if (isAutoscrolling) {
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
    
    private fun shareLogsAsText() {
        val logs = SmsReceiver.getDebugLogs()
        val logText = if (logs.isEmpty()) {
            "No SMS logs captured"
        } else {
            getString(R.string.app_name) + " SMS Receiver Logs\n" +
            "===============================\n" +
            logs.joinToString("\n")
        }
        
        // Create sharing intent
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " SMS Logs")
            putExtra(android.content.Intent.EXTRA_TEXT, logText)
        }
        
        startActivity(android.content.Intent.createChooser(shareIntent, "Share SMS Logs"))
    }
} 