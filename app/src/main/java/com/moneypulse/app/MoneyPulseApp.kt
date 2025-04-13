package com.moneypulse.app

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moneypulse.app.BuildConfig
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
        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (e: Exception) {
            Log.e("MoneyPulseApp", "Error creating notification channel: ${e.message}")
        }
        
        // Run security checks - but only after a delay and with error handling
        if (!BuildConfig.DEBUG) {
            // Only run strict checks in release builds
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    performSecurityChecks()
                } catch (e: Exception) {
                    Log.e("MoneyPulseApp", "Security check error: ${e.message}")
                }
            }, 2000) // Delay to allow app to initialize fully
        } else {
            // In debug mode, just log security issues rather than showing warnings
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    performDebugSecurityChecks()
                } catch (e: Exception) {
                    Log.e("MoneyPulseApp", "Debug security check error: ${e.message}")
                }
            }, 2000)
        }
    }
    
    /**
     * Perform security checks on device for release builds
     * Shows warnings but doesn't block app usage to ensure accessibility
     */
    private fun performSecurityChecks() {
        // For version 1.0, we'll disable root detection entirely 
        // to avoid false positives on legitimate devices
        return;
        
        // Skip if warning already shown in this session
        if (securityWarningShown) return
        
        var securityIssueFound = false
        
        try {
            // Check if device is rooted - now using a more reliable method with fewer false positives
            if (securityHelper.isDeviceRooted()) {
                Log.w("SecurityCheck", "Device appears to be rooted")
                securityIssueFound = true
            }
            
            // Check if running in emulator
            if (securityHelper.isRunningInEmulator()) {
                Log.w("SecurityCheck", "App running in emulator")
                securityIssueFound = true
            }
            
            // Show warning if security issues found - but only once and non-blocking
            if (securityIssueFound) {
                try {
                    // Use a handler to show the toast after a delay to ensure UI is ready
                    Handler(Looper.getMainLooper()).postDelayed({
                        Toast.makeText(
                            this,
                            getString(R.string.device_security_warning),
                            Toast.LENGTH_LONG
                        ).show()
                    }, 3000)
                    
                    securityWarningShown = true
                } catch (e: Exception) {
                    Log.e("MoneyPulseApp", "Error showing security warning: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // Catch any security check errors to prevent app crashes
            Log.e("MoneyPulseApp", "Security check error: ${e.message}")
        }
    }
    
    /**
     * Perform less aggressive security checks for debug builds
     * Only logs issues rather than showing warnings
     */
    private fun performDebugSecurityChecks() {
        // In debug builds, just log security issues
        if (securityHelper.isDeviceRooted()) {
            Log.d("SecurityCheck", "Debug build running on rooted device or emulator")
        }
        
        if (securityHelper.isRunningInEmulator()) {
            Log.d("SecurityCheck", "Debug build running in emulator")
        }
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
} 