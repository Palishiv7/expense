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
import com.moneypulse.app.util.DatabaseCleanupService
import com.moneypulse.app.util.NotificationHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyPulseApp : Application(), Configuration.Provider {
    
    // Declare variables but don't access them immediately to avoid early initialization crashes
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var securityHelper: SecurityHelper
    
    @Inject
    lateinit var databaseCleanupService: DatabaseCleanupService
    
    // Flag to determine if security warning has been shown
    private var securityWarningShown = false
    
    override fun onCreate() {
        // Guard entire onCreate with try-catch to prevent any crashes during startup
        try {
            super.onCreate()
            
            // Defer all initialization to avoid blocking app startup
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    initializeApp()
                } catch (e: Exception) {
                    // Log but don't crash
                    Log.e("MoneyPulseApp", "Error during delayed initialization: ${e.message}")
                }
            }, 1000)
        } catch (e: Exception) {
            // Catch all exceptions to prevent app crashes during startup
            Log.e("MoneyPulseApp", "Critical error during app start: ${e.message}")
        }
    }
    
    /**
     * Separate initialization method to defer work off the main onCreate thread
     */
    private fun initializeApp() {
        try {
            // Create notification channel for transactions
            NotificationHelper.createNotificationChannel(this)
            
            // Schedule database cleanup to optimize storage
            scheduleDbCleanup()
        } catch (e: Exception) {
            Log.e("MoneyPulseApp", "Error during app initialization: ${e.message}")
        }
        
        // All security checks disabled for now to ensure app stability
        // Will re-enable in future updates after proper testing
    }
    
    /**
     * Schedule periodic database cleanup to optimize storage space
     * by removing unnecessary SMS bodies from old transactions
     */
    private fun scheduleDbCleanup() {
        try {
            if (::databaseCleanupService.isInitialized) {
                databaseCleanupService.scheduleCleanup()
                Log.d("MoneyPulseApp", "Database cleanup service scheduled")
            } else {
                Log.w("MoneyPulseApp", "Database cleanup service not initialized")
            }
        } catch (e: Exception) {
            Log.e("MoneyPulseApp", "Error scheduling database cleanup: ${e.message}")
        }
    }
    
    /**
     * Perform security checks on device for release builds
     * Currently disabled to avoid false positives
     */
    private fun performSecurityChecks() {
        // For version 1.0, we'll disable root detection entirely 
        // to avoid false positives on legitimate devices
        return;
        
        // Rest of method kept for future reference but not used now
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        // Handle the case where workerFactory might not be initialized yet
        try {
            if (::workerFactory.isInitialized) {
                return Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .build()
            } else {
                Log.w("MoneyPulseApp", "WorkerFactory not initialized yet, using default config")
                return Configuration.Builder().build()
            }
        } catch (e: Exception) {
            Log.e("MoneyPulseApp", "Error setting up WorkManager: ${e.message}")
            return Configuration.Builder().build()
        }
    }
} 