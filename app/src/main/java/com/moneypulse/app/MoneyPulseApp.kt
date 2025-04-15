package com.moneypulse.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moneypulse.app.util.DatabaseCleanupService
import com.moneypulse.app.util.NotificationHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class MoneyPulseApp : Application(), Configuration.Provider {
    
    // Use Provider instead of direct injection for better lazy loading
    @Inject
    lateinit var workerFactoryProvider: Provider<HiltWorkerFactory>
    
    @Inject
    lateinit var securityHelperProvider: Provider<SecurityHelper>
    
    @Inject
    lateinit var databaseCleanupServiceProvider: Provider<DatabaseCleanupService>
    
    // Initialization flags
    private var initStarted = false
    private var workManagerInitialized = false
    
    override fun onCreate() {
        // Ensure we start with a clean WorkManager state
        cleanWorkManagerState()
        
        // Standard initialization
        try {
            super.onCreate()
            
            // Initialize notification channels immediately (no dependencies)
            NotificationHelper.createNotificationChannel(this)
            
            // Schedule the rest of initialization for after Hilt is ready
            Handler(Looper.getMainLooper()).postDelayed({
                safeInitialize()
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during app start: ${e.message}")
        }
    }
    
    /**
     * Ensure WorkManager has a clean state
     * This prevents persistence issues that can lead to crashes
     */
    private fun cleanWorkManagerState() {
        try {
            val workManagerDb = getDatabasePath("androidx.work.workdb")
            if (workManagerDb.exists()) {
                try {
                    // Delete the database files
                    val files = arrayOf(
                        workManagerDb,
                        File(workManagerDb.path + "-journal"),
                        File(workManagerDb.path + "-shm"),
                        File(workManagerDb.path + "-wal")
                    )
                    
                    for (file in files) {
                        if (file.exists()) {
                            val deleted = file.delete()
                            if (deleted) {
                                Log.d(TAG, "Deleted WorkManager file: ${file.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Non-fatal
                    Log.e(TAG, "Failed to clean WorkManager state: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // Non-fatal
            Log.e(TAG, "Error accessing WorkManager database: ${e.message}")
        }
    }
    
    /**
     * Safely initialize app components with proper error handling
     */
    private fun safeInitialize() {
        if (initStarted) return
        initStarted = true
        
        try {
            // Only attempt to schedule cleanup if the service is available
            try {
                // Get database cleanup service safely via provider
                val service = databaseCleanupServiceProvider.get()
                service.scheduleCleanup()
                Log.d(TAG, "Database cleanup service scheduled")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling cleanup: ${e.message}")
                // Non-fatal, app can continue without cleanup
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during safe initialization: ${e.message}")
        }
    }
    
    /**
     * Provide a minimal WorkManager configuration that doesn't depend on Hilt
     * This prevents crashes during app initialization
     */
    override fun getWorkManagerConfiguration(): Configuration {
        if (workManagerInitialized) {
            // We've successfully initialized WorkManager before
            try {
                return Configuration.Builder()
                    .setWorkerFactory(workerFactoryProvider.get())
                    .setMinimumLoggingLevel(Log.INFO)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating WorkManager config: ${e.message}")
            }
        }
        
        // Always provide a minimal configuration that won't crash
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
            
        // Mark that we've initialized WorkManager with a minimal config
        workManagerInitialized = true
        
        // If we can, upgrade to a proper config in the future
        scheduleProperWorkManagerInit()
        
        return config
    }
    
    /**
     * Schedule an attempt to properly initialize WorkManager later
     */
    private fun scheduleProperWorkManagerInit() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // If initialization has completed, simply create an instance of WorkManager
                // This will force proper initialization with our config
                androidx.work.WorkManager.getInstance(applicationContext)
                
                // Try to get the worker factory and mark as initialized
                workerFactoryProvider.get()
                workManagerInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error during delayed WorkManager init: ${e.message}")
            }
        }, 3000)
    }
    
    companion object {
        private const val TAG = "MoneyPulseApp"
    }
} 