package com.moneypulse.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneypulse.app.data.local.dao.TransactionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for cleaning up SMS data from the database
 * 
 * This worker runs periodically to clean up SMS bodies from old transactions,
 * keeping the transaction details intact (amount, merchant, date, category)
 * but removing the full SMS content that's no longer needed for duplicate detection.
 */
@HiltWorker
class DatabaseCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DatabaseCleanupWorker"
        
        // Define how old transactions need to be before SMS bodies are cleared
        // Here we use 2 hours as a safe margin (way beyond the 30-minute duplicate detection window)
        private const val CLEANUP_THRESHOLD_HOURS = 2L
    }
    
    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, "Starting database cleanup job")
            
            // Calculate cutoff timestamp (current time minus threshold hours)
            val cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(CLEANUP_THRESHOLD_HOURS)
            
            // First count how many records will be affected (for logging)
            val affectedCount = transactionDao.countTransactionsWithSmsBodiesOlderThan(cutoffTime)
            Log.d(TAG, "Found $affectedCount transactions with SMS bodies older than $CLEANUP_THRESHOLD_HOURS hours")
            
            if (affectedCount > 0) {
                // Perform the actual cleanup
                val updatedCount = transactionDao.clearSmsBodiesOlderThan(cutoffTime)
                Log.d(TAG, "Successfully cleared SMS bodies from $updatedCount transactions")
            } else {
                Log.d(TAG, "No transactions need cleanup at this time")
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up database: ${e.message}", e)
            return Result.retry()
        }
    }
} 