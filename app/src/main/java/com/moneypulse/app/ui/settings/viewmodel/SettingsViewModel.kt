package com.moneypulse.app.ui.settings.viewmodel

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preferenceHelper: PreferenceHelper
) : AndroidViewModel(application) {
    
    private val context = getApplication<Application>()
    
    // State for automatic transaction mode
    private val _isAutoTransaction = MutableStateFlow(preferenceHelper.isAutoTransactionEnabled())
    val isAutoTransaction: StateFlow<Boolean> = _isAutoTransaction.asStateFlow()
    
    // State for user's monthly income
    private val _userIncome = MutableStateFlow(preferenceHelper.getUserIncome())
    val userIncome: StateFlow<Double> = _userIncome.asStateFlow()
    
    // State for SMS permission status
    private val _smsPermissionStatus = MutableStateFlow(preferenceHelper.getSmsPermissionStatus())
    val smsPermissionStatus: StateFlow<String> = _smsPermissionStatus.asStateFlow()
    
    init {
        // Refresh permission status when ViewModel is created
        refreshSmsPermissionStatus()
    }
    
    /**
     * Refresh the SMS permission status from the system
     */
    private fun refreshSmsPermissionStatus() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
        } else if (preferenceHelper.getSmsPermissionStatus() == PreferenceHelper.PERMISSION_STATUS_GRANTED) {
            // Permission was revoked in settings
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_DENIED)
        }
        
        _smsPermissionStatus.value = preferenceHelper.getSmsPermissionStatus()
    }
    
    /**
     * Set the automatic transaction processing mode
     */
    fun setAutoTransaction(enabled: Boolean) {
        if (enabled) {
            // Only enable automatic mode if SMS permission is granted
            if (_smsPermissionStatus.value == PreferenceHelper.PERMISSION_STATUS_GRANTED) {
                preferenceHelper.setTransactionMode(PreferenceHelper.MODE_AUTOMATIC)
                _isAutoTransaction.value = true
            } else {
                // Request SMS permission first
                requestSmsPermission()
                // Keep switch in off position
                _isAutoTransaction.value = false
            }
        } else {
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
            _isAutoTransaction.value = false
        }
    }
    
    /**
     * Update the user's monthly income
     */
    fun updateIncome(income: Double) {
        preferenceHelper.setUserIncome(income)
        _userIncome.value = income
    }
    
    /**
     * Request SMS permission by opening app settings
     * since we can't directly request permission from settings screen
     */
    fun requestSmsPermission() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
} 