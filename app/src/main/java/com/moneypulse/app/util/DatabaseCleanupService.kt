package com.moneypulse.app.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moneypulse.app.worker.DatabaseCleanupWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to manage database cleanup operations
 * 
 * This service schedules periodic cleanup of the database
 * to optimize storage and remove unnecessary SMS body content
 * from transactions older than the configured threshold.
 */
@Singleton
class DatabaseCleanupService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "DatabaseCleanupService"
        private const val WORK_NAME = "database_cleanup_work"
        
        // Run the cleanup job every 6 hours
        private const val CLEANUP_INTERVAL_HOURS = 6L
    }
    
    /**
     * Schedule the database cleanup worker to run periodically
     * Will replace any existing work with the same name
     */
    fun scheduleCleanup() {
        try {
            Log.d(TAG, "Scheduling database cleanup to run every $CLEANUP_INTERVAL_HOURS hours")
            
            // Set constraints - only run when device is idle and charging to minimize impact
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            
            // Create the periodic work request
            val cleanupWorkRequest = PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(
                CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag("database_cleanup")
                .build()
            
            // Schedule the work, replacing any existing work with the same name
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                cleanupWorkRequest
            )
            
            Log.d(TAG, "Database cleanup scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling database cleanup: ${e.message}", e)
        }
    }
    
    /**
     * Cancel any scheduled cleanup work
     */
    fun cancelCleanup() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Database cleanup canceled")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling database cleanup: ${e.message}", e)
        }
    }
} 