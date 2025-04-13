package com.moneypulse.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.widget.RemoteViews
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
    const val TRANSACTION_NOTIFICATION_ID = 1001
    
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
                setShowBadge(true) // Shows badge on app icon
            }
            
            // Register the channel with the system
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a notification for a new transaction with custom layout and colored buttons
     */
    fun showTransactionNotification(context: Context, transaction: TransactionSms) {
        // Format the amount for display
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        formatter.maximumFractionDigits = 0
        val formattedAmount = formatter.format(transaction.amount)
        
        // Use the merchant name that was already extracted by SmsReceiver
        // Only fall back to cleaning if the merchant name is empty
        val merchantName = if (transaction.merchantName.isNotEmpty()) {
            transaction.merchantName
        } else {
            cleanMerchantName(transaction.body)
        }
        
        // Create transaction object with final merchant name
        val finalTransaction = transaction.copy(
            merchantName = merchantName
        )
        
        // Create intent for when user taps the notification (to edit details)
        val editIntent = Intent(context, EditTransactionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_TRANSACTION_DATA, finalTransaction)
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
            putExtra(EXTRA_TRANSACTION_DATA, finalTransaction)
        }
        val addPendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(), // Use timestamp to ensure unique request code
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create "Ignore Transaction" action
        val ignoreIntent = Intent(context, IgnoreTransactionReceiver::class.java).apply {
            action = ACTION_IGNORE_TRANSACTION
            putExtra(EXTRA_TRANSACTION_DATA, finalTransaction)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt() + 1, // Use timestamp+1 to ensure unique request code
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Prepare message text
        val amountText = "$formattedAmount spent at $merchantName"
        
        // Create custom notification layout using RemoteViews
        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_transaction)
        
        // Set the text content
        notificationLayout.setTextViewText(R.id.notification_amount, amountText)
        
        // Set up button click listeners
        notificationLayout.setOnClickPendingIntent(R.id.btn_add_transaction, addPendingIntent)
        notificationLayout.setOnClickPendingIntent(R.id.btn_ignore, ignorePendingIntent)
        
        // Get default notification sound
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // Build notification with custom layout
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(notificationLayout)
            .setCustomBigContentView(notificationLayout) // Same layout for expanded state
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(editPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Maximum priority
            .setCategory(NotificationCompat.CATEGORY_CALL) // Treat as high-priority like a call
            .setSound(defaultSoundUri) // Play notification sound
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Custom vibration pattern
        
        // Show the notification with increased priority
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Cancel any existing notifications first to ensure this one shows at the top
        notificationManager.cancel(TRANSACTION_NOTIFICATION_ID)
        
        // Display the new high-priority notification
        notificationManager.notify(TRANSACTION_NOTIFICATION_ID, notificationBuilder.build())
    }
    
    /**
     * Clean up merchant name to make it more presentable in notifications
     * This version uses the full SMS body for better context
     */
    private fun cleanMerchantName(smsBody: String): String {
        // Check for common merchants first
        val knownMerchants = listOf(
            "amazon", "flipkart", "swiggy", "zomato", "bigbasket", "grofers", 
            "uber", "ola", "phonepe", "gpay", "paytm", "airtel", "jio", "vodafone",
            "makemytrip", "irctc", "bookmyshow", "myntra", "nykaa", "snapdeal"
        )
        
        for (merchant in knownMerchants) {
            if (smsBody.contains(merchant, ignoreCase = true)) {
                return merchant.replaceFirstChar { it.uppercase() }
            }
        }
        
        // Fall back to a generic name for all other cases
        return "Payment"
    }
} 