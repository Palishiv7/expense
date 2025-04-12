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
import com.moneypulse.app.receiver.AddTransactionReceiver
import com.moneypulse.app.receiver.IgnoreTransactionReceiver
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
        val formattedAmount = formatter.format(transaction.amount)
        
        // Clean up merchant name to remove any "To" suffix
        val merchantName = cleanMerchantName(transaction.merchantName)
        
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
        val addIntent = Intent(context, AddTransactionReceiver::class.java).apply {
            action = ACTION_ADD_TRANSACTION
            putExtra(EXTRA_TRANSACTION_DATA, transaction)
        }
        val addPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create "Ignore Transaction" action
        val ignoreIntent = Intent(context, IgnoreTransactionReceiver::class.java).apply {
            action = ACTION_IGNORE_TRANSACTION
            putExtra(EXTRA_TRANSACTION_DATA, transaction)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Prepare a detailed notification message
        val shortMessage = "$formattedAmount spent at $merchantName"
        
        // More detailed message for expanded view
        val detailedMessage = """
            Amount: $formattedAmount
            Merchant: $merchantName
            ${if (transaction.description.isNullOrEmpty()) "" else "Description: ${transaction.description}"}
            Tap to review details
        """.trimIndent()
        
        // Build notification with circular icon-only action buttons
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.transaction_notification_title))
            .setContentText(shortMessage)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(detailedMessage))
            .setContentIntent(editPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Add green checkmark action (icon only, no text)
            .addAction(
                R.drawable.ic_check_circle,
                "", // Empty text for icon-only button
                addPendingIntent
            )
            // Add red X action (icon only, no text)
            .addAction(
                R.drawable.ic_cancel_circle,
                "", // Empty text for icon-only button
                ignorePendingIntent
            )
        
        // Show the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(TRANSACTION_NOTIFICATION_ID, notificationBuilder.build())
    }
    
    /**
     * Clean up merchant name to make it more presentable in notifications
     */
    private fun cleanMerchantName(merchantName: String): String {
        // Remove any trailing "To" suffix
        val cleanedName = merchantName.trim().replace(Regex("\\s+To$"), "")
        
        // If it's just a bunch of digits (likely a phone number), return a generic name
        if (cleanedName.replace(Regex("\\D"), "").length >= 10) {
            return "Payment"
        }
        
        return cleanedName
    }
} 