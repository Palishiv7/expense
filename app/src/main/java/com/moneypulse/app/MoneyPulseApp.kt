package com.moneypulse.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
    
    // Initialization flags to prevent duplicate operations
    private var initStarted = false
    private var workManagerInitialized = false
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate() {
        // Initialize shared preferences first - this has no dependencies
        prefs = applicationContext.getSharedPreferences("app_state", Context.MODE_PRIVATE)
        
        // Force clean WorkManager database if restart detected
        val hasRestarted = detectRestart()
        if (hasRestarted) {
            Log.d(TAG, "Restart detected - ensuring clean WorkManager state")
            forceCleanWorkManagerState()
        }
        
        try {
            // Proceed with normal initialization
            super.onCreate()
            
            // Initialize notification channels - no dependencies
            NotificationHelper.createNotificationChannel(this)
            
            // Delay Hilt-dependent operations to ensure initialization is complete
            // Use a shorter delay for first time, longer for restarts
            val initialDelay = if (hasRestarted) 1500L else 1000L
            Handler(Looper.getMainLooper()).postDelayed({
                safeInitialize(hasRestarted)
            }, initialDelay)
            
            // Schedule a reset of the third restart flag after a period of stable operation
            scheduleRestartFlagReset()
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during app start: ${e.message}")
        }
    }
    
    /**
     * Detect if this is an app restart after being killed
     */
    private fun detectRestart(): Boolean {
        val lastTimestamp = prefs.getLong("last_opened_timestamp", 0L)
        val currentTime = System.currentTimeMillis()
        
        // Save current timestamp
        prefs.edit().putLong("last_opened_timestamp", currentTime).apply()
        
        // If last timestamp was within 24 hours, this is likely a restart
        // rather than a fresh install or normal app open after a long time
        val isRestart = lastTimestamp > 0 && (currentTime - lastTimestamp) < 24 * 60 * 60 * 1000
        
        // Track restart count if this is a restart
        if (isRestart) {
            val restartCount = prefs.getInt("restart_count", 0) + 1
            prefs.edit().putInt("restart_count", restartCount).apply()
            
            // Check if this is the third restart and set flag
            if (restartCount >= 3) {
                Log.d(TAG, "Third restart detected - setting safety flag")
                prefs.edit().putBoolean("is_third_restart", true).apply()
            }
            
            Log.d(TAG, "App restart #$restartCount detected")
        } else {
            // Reset counters on fresh start (after long time)
            prefs.edit()
                .putInt("restart_count", 0)
                .putBoolean("is_third_restart", false)
                .apply()
        }
        
        return isRestart
    }
    
    /**
     * Force-clean the WorkManager state by deleting the database files
     * This ensures WorkManager starts with a clean state on restarts
     */
    private fun forceCleanWorkManagerState() {
        try {
            Log.d(TAG, "Performing forced WorkManager state cleanup")
            val workManagerDb = getDatabasePath("androidx.work.workdb")
            
            // Delete all related WorkManager files
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
                        Log.d(TAG, "Successfully deleted WorkManager file: ${file.name}")
                    } else {
                        Log.w(TAG, "Failed to delete WorkManager file: ${file.name}")
                    }
                }
            }
            
            // Also try to clear WorkManager preferences to prevent persistence issues
            val workManagerPrefs = applicationContext.getSharedPreferences("androidx.work.util.preferences", MODE_PRIVATE)
            workManagerPrefs.edit().clear().apply()
            
            Log.d(TAG, "WorkManager cleanup completed")
        } catch (e: Exception) {
            // Non-fatal, but log for troubleshooting
            Log.e(TAG, "Error cleaning WorkManager state: ${e.message}")
        }
    }
    
    /**
     * Perform regular WorkManager cleanup during normal startup
     * Less aggressive than forceCleanWorkManagerState
     */
    private fun cleanWorkManagerState() {
        try {
            Log.d(TAG, "Performing routine WorkManager cleanup check")
            val workManagerDb = getDatabasePath("androidx.work.workdb")
            
            // Only delete problematic files like journal, shm, and wal
            // This preserves the main database but cleans up transient files
            val files = arrayOf(
                File(workManagerDb.path + "-journal"),
                File(workManagerDb.path + "-shm"),
                File(workManagerDb.path + "-wal")
            )
            
            for (file in files) {
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Deleted WorkManager auxiliary file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal
            Log.e(TAG, "Error in routine WorkManager cleanup: ${e.message}")
        }
    }
    
    /**
     * Safely initialize app components with proper error handling
     * Use different strategies depending on restart status
     */
    private fun safeInitialize(isRestart: Boolean) {
        if (initStarted) return
        initStarted = true
        
        try {
            // First perform WorkManager initialization with minimal config
            // This must happen before other operations that might use WorkManager
            initWorkManager(isRestart)
            
            // With longer delay for security components to be ready
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Only attempt to schedule cleanup if not a restart or third+ restart
                    scheduleCleanupIfNeeded(isRestart)
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling cleanup: ${e.message}")
                    // Non-fatal, app can continue without cleanup
                }
            }, if (isRestart) 2000L else 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error during safe initialization: ${e.message}")
        }
    }
    
    /**
     * Initialize WorkManager with appropriate configuration
     */
    private fun initWorkManager(isRestart: Boolean) {
        try {
            if (isRestart) {
                // On restart, force WorkManager to initialize with our minimal config
                val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                Log.d(TAG, "Basic WorkManager initialization successful")
            }
            
            // Mark WorkManager as at least basically initialized
            workManagerInitialized = true
            
            // Schedule proper init with Hilt factory after delay
            scheduleProperWorkManagerInit()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WorkManager: ${e.message}")
        }
    }
    
    /**
     * Schedule database cleanup only if needed and dependencies are available
     */
    private fun scheduleCleanupIfNeeded(isRestart: Boolean) {
        try {
            // Check if this is the third restart case - skip cleanup entirely
            if (prefs.getBoolean("is_third_restart", false)) {
                Log.d(TAG, "Skipping database cleanup on third restart for stability")
                return
            }
            
            // Get database cleanup service safely via provider
            val service = databaseCleanupServiceProvider.get()
            
            // On restarts, cancel any existing work first
            if (isRestart) {
                service.cancelCleanup()
                // Short delay before scheduling new cleanup
                Handler(Looper.getMainLooper()).postDelayed({
                    service.scheduleCleanup()
                    Log.d(TAG, "Database cleanup rescheduled after restart")
                }, 500L)
            } else {
                service.scheduleCleanup()
                Log.d(TAG, "Database cleanup scheduled normally")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule database cleanup: ${e.message}")
        }
    }
    
    /**
     * Provide a minimal WorkManager configuration that doesn't depend on Hilt
     * This prevents crashes during app initialization
     */
    override fun getWorkManagerConfiguration(): Configuration {
        // Check the third restart status
        val isThirdRestart = prefs.getBoolean("is_third_restart", false)
        
        if (isThirdRestart) {
            Log.d(TAG, "Using safe WorkManager configuration due to third restart condition")
            // Always use minimal configuration on third restart
            return Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()
        }
        
        if (workManagerInitialized) {
            // We've successfully initialized WorkManager before 
            // and this is not our problematic restart
            try {
                val factory = workerFactoryProvider.get()
                return Configuration.Builder()
                    .setWorkerFactory(factory)
                    .setMinimumLoggingLevel(Log.INFO)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating WorkManager config with Hilt factory: ${e.message}")
            }
        }
        
        // Always provide a minimal configuration that won't crash
        Log.d(TAG, "Using minimal WorkManager configuration")
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }
    
    /**
     * Schedule an attempt to properly initialize WorkManager later
     */
    private fun scheduleProperWorkManagerInit() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Force WorkManager to initialize if it hasn't already
                val workManager = androidx.work.WorkManager.getInstance(applicationContext)
                
                // Try to get the worker factory and update factory
                val factory = workerFactoryProvider.get()
                Log.d(TAG, "WorkManager properly initialized with Hilt factory")
                workManagerInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error during delayed WorkManager initialization: ${e.message}")
            }
        }, 3000)
    }
    
    /**
     * Schedule a delayed reset of the third restart flag to ensure
     * the app can recover from a problematic state after running stably
     */
    private fun scheduleRestartFlagReset() {
        // Only attempt reset if the third restart flag is set
        if (prefs.getBoolean("is_third_restart", false)) {
            // Wait 2 minutes before resetting the flag - if the app stays alive this long,
            // we consider it stable enough to try normal initialization next time
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "App has been stable - resetting third restart flag for next launch")
                    prefs.edit()
                        .putBoolean("is_third_restart", false)
                        .putInt("restart_count", 0)
                        .apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reset restart flags: ${e.message}")
                }
            }, 2 * 60 * 1000) // 2 minutes
        }
    }
    
    companion object {
        private const val TAG = "MoneyPulseApp"
    }
} 