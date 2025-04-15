package com.moneypulse.app.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.moneypulse.app.R
import com.moneypulse.app.util.PreferenceHelper

/**
 * Dialog for choosing between automatic and manual transaction processing
 */
class TransactionModeDialog(
    context: Context,
    private val preferenceHelper: PreferenceHelper,
    private val onModeSelected: (Boolean) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_mode, null)
        setContentView(view)
        
        // Dialog cannot be dismissed by clicking outside
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        
        // Get references to UI elements
        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_transaction_mode)
        val confirmButton = view.findViewById<Button>(R.id.btn_confirm)
        val radioAutomatic = view.findViewById<RadioButton>(R.id.radio_automatic)
        val radioManual = view.findViewById<RadioButton>(R.id.radio_manual)
        val messageView = view.findViewById<TextView>(R.id.text_permission_message)
        
        // Check SMS permission status and adjust UI accordingly
        val smsPermissionStatus = preferenceHelper.getSmsPermissionStatus()
        
        if (smsPermissionStatus == PreferenceHelper.PERMISSION_STATUS_GRANTED) {
            // SMS permission granted, both options available
            radioAutomatic.isEnabled = true
            messageView?.visibility = View.GONE
        } else {
            // SMS permission denied or skipped, force manual mode
            radioAutomatic.isEnabled = false
            radioManual.isChecked = true
            
            // Show message explaining why automatic mode is disabled
            messageView?.visibility = View.VISIBLE
            messageView?.text = "Automatic mode requires SMS permission which was not granted. " +
                               "You can enable it later in Settings."
        }
        
        // Set up the confirm button
        confirmButton.setOnClickListener {
            val isAutomatic = radioGroup.checkedRadioButtonId == R.id.radio_automatic
            
            // Only allow automatic mode if SMS permission is granted
            if (isAutomatic && smsPermissionStatus == PreferenceHelper.PERMISSION_STATUS_GRANTED) {
                preferenceHelper.setTransactionMode(PreferenceHelper.MODE_AUTOMATIC)
            } else {
                preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
            }
            
            // Mark first launch as completed
            preferenceHelper.completeFirstLaunch()
            
            // Callback with the selected mode
            onModeSelected(isAutomatic && smsPermissionStatus == PreferenceHelper.PERMISSION_STATUS_GRANTED)
            
            // Dismiss the dialog
            dismiss()
        }
    }
    
    companion object {
        /**
         * Show the transaction mode selection dialog
         */
        fun show(
            context: Context,
            preferenceHelper: PreferenceHelper,
            onModeSelected: (Boolean) -> Unit
        ) {
            val dialog = TransactionModeDialog(context, preferenceHelper, onModeSelected)
            dialog.show()
        }
    }
} 