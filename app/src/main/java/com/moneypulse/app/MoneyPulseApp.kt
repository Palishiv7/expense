package com.moneypulse.app

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moneypulse.app.R
import com.moneypulse.app.util.NotificationHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyPulseApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var securityHelper: SecurityHelper
    
    // Flag to determine if security warning has been shown
    private var securityWarningShown = false
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel for transactions
        NotificationHelper.createNotificationChannel(this)
        
        // Run security checks
        Handler(Looper.getMainLooper()).postDelayed({
            performSecurityChecks()
        }, 2000) // Delay to allow app to initialize fully
    }
    
    /**
     * Perform security checks on device
     * Shows warnings but doesn't block app usage to ensure accessibility
     */
    private fun performSecurityChecks() {
        // Skip if warning already shown in this session
        if (securityWarningShown) return
        
        var securityIssueFound = false
        
        // Check if device is rooted
        if (securityHelper.isDeviceRooted()) {
            securityIssueFound = true
        }
        
        // Check if running in emulator - only in release builds
        if (!BuildConfig.DEBUG && securityHelper.isRunningInEmulator()) {
            securityIssueFound = true
        }
        
        // Show warning if security issues found
        if (securityIssueFound) {
            Toast.makeText(
                this,
                getString(R.string.device_security_warning),
                Toast.LENGTH_LONG
            ).show()
            
            securityWarningShown = true
        }
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
} 