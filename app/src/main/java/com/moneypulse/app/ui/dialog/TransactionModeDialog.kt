package com.moneypulse.app.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
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
        
        // Set up dialog buttons
        val builder = AlertDialog.Builder(context)
            .setPositiveButton(R.string.save) { _, _ ->
                val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_transaction_mode)
                val isAutomatic = radioGroup.checkedRadioButtonId == R.id.radio_automatic
                
                // Save the preference
                if (isAutomatic) {
                    preferenceHelper.setTransactionMode(PreferenceHelper.MODE_AUTOMATIC)
                } else {
                    preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
                }
                
                // Mark first launch as completed
                preferenceHelper.completeFirstLaunch()
                
                // Callback with the selected mode
                onModeSelected(isAutomatic)
                
                dismiss()
            }
        
        // Don't allow cancellation
        setCancelable(false)
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