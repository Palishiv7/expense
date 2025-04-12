package com.moneypulse.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.moneypulse.app.R
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receiver for handling "Add Transaction" action from notification
 */
@AndroidEntryPoint
class AddTransactionReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_ADD_TRANSACTION) {
            val transaction = intent.getParcelableExtra<TransactionSms>(NotificationHelper.EXTRA_TRANSACTION_DATA)
            
            // Cancel the notification immediately
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.TRANSACTION_NOTIFICATION_ID)
            
            if (transaction != null) {
                // Process in background
                CoroutineScope(Dispatchers.IO).launch {
                    transactionRepository.processNewTransactionSms(transaction)
                    
                    // Show toast on the main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            context, 
                            context.getString(R.string.transaction_added),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}

/**
 * Receiver for handling "Ignore Transaction" action from notification
 */
@AndroidEntryPoint
class IgnoreTransactionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_IGNORE_TRANSACTION) {
            // Cancel the notification immediately
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.TRANSACTION_NOTIFICATION_ID)
            
            // Just show a toast that the transaction was ignored
            Toast.makeText(
                context,
                context.getString(R.string.transaction_ignored),
                Toast.LENGTH_SHORT
            ).show()
            
            // No need to save anything to the database
        }
    }
} 