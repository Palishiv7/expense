package com.moneypulse.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.moneypulse.app.R
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.ui.MainActivity
import com.moneypulse.app.ui.transaction.EditTransactionActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * Helper class for creating and displaying notifications
 */
object NotificationHelper {
    
    private const val CHANNEL_ID = "transaction_channel"
    private const val TRANSACTION_NOTIFICATION_ID = 1001
    
    // Action constants for broadcast receivers
    const val ACTION_ADD_TRANSACTION = "com.moneypulse.app.ADD_TRANSACTION"
    const val ACTION_IGNORE_TRANSACTION = "com.moneypulse.app.IGNORE_TRANSACTION"
    const val EXTRA_TRANSACTION_DATA = "transaction_data"
    
    /**
     * Create notification channel for Android O and above
     */
    fun createNotificationChannel(context: Context) {
        // Only needed for Android 8.0 (API 26) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.app_name)
            val description = "Transaction notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
                enableLights(true)
                enableVibration(true)
            }
            
            // Register the channel with the system
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a notification for a new transaction with standard action buttons
     */
    fun showTransactionNotification(context: Context, transaction: TransactionSms) {
        // Format the amount for display
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        formatter.maximumFractionDigits = 0
        val amount = formatter.format(transaction.amount)
        
        // Create intent for when user taps the notification (to edit details)
        val editIntent = Intent(context, EditTransactionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_TRANSACTION_DATA, transaction)
        }
        
        val editPendingIntent = PendingIntent.getActivity(
            context,
            0,
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create "Add Transaction" action
        val addIntent = Intent(ACTION_ADD_TRANSACTION).apply {
            setPackage(context.packageName) // Important for Android 12+
            putExtra(EXTRA_TRANSACTION_DATA, transaction)
        }
        val addPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create "Ignore Transaction" action
        val ignoreIntent = Intent(ACTION_IGNORE_TRANSACTION).apply {
            setPackage(context.packageName) // Important for Android 12+
            putExtra(EXTRA_TRANSACTION_DATA, transaction)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification with standard actions
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.transaction_notification_title))
            .setContentText("₹${transaction.amount} spent at ${transaction.merchantName}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("₹${transaction.amount} spent at ${transaction.merchantName}"))
            .setContentIntent(editPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Add action with green check icon
            .addAction(
                R.drawable.ic_check_circle,
                context.getString(R.string.add_transaction),
                addPendingIntent
            )
            // Add action with red X icon
            .addAction(
                R.drawable.ic_cancel_circle,
                context.getString(R.string.ignore),
                ignorePendingIntent
            )
        
        // Show the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(TRANSACTION_NOTIFICATION_ID, notificationBuilder.build())
    }
} 