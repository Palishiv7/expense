package com.moneypulse.app.ui.settings.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceHelper: PreferenceHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "SettingsViewModel"
    }
    
    // State for auto transaction toggle
    private val _isAutoTransaction = MutableStateFlow(preferenceHelper.getTransactionMode() == PreferenceHelper.MODE_AUTOMATIC)
    val isAutoTransaction: StateFlow<Boolean> = _isAutoTransaction.asStateFlow()
    
    // State for security toggle
    private val _isSecurityEnabled = MutableStateFlow(preferenceHelper.isSecurityEnabled())
    val isSecurityEnabled: StateFlow<Boolean> = _isSecurityEnabled.asStateFlow()
    
    // State for screen capture blocking
    private val _isScreenCaptureBlocked = MutableStateFlow(preferenceHelper.isScreenCaptureBlocked())
    val isScreenCaptureBlocked: StateFlow<Boolean> = _isScreenCaptureBlocked.asStateFlow()
    
    // State for SMS permission status
    private val _smsPermissionStatus = MutableStateFlow(preferenceHelper.getSmsPermissionStatus())
    val smsPermissionStatus: StateFlow<String> = _smsPermissionStatus.asStateFlow()
    
    // State for user income
    private val _userIncome = MutableStateFlow(preferenceHelper.getUserMonthlyIncome())
    val userIncome: StateFlow<Double> = _userIncome.asStateFlow()
    
    init {
        // Refresh permission status
        refreshSmsPermissionStatus()
    }
    
    fun refreshSmsPermissionStatus() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val previousStatus = preferenceHelper.getSmsPermissionStatus()
        
        if (hasPermission) {
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
            
            // If permission was just granted (previously denied or skipped),
            // automatically enable automatic mode as a convenience
            if (previousStatus != PreferenceHelper.PERMISSION_STATUS_GRANTED) {
                preferenceHelper.setTransactionMode(PreferenceHelper.MODE_AUTOMATIC)
                _isAutoTransaction.value = true
            }
        } else if (preferenceHelper.getSmsPermissionStatus() == PreferenceHelper.PERMISSION_STATUS_GRANTED) {
            // Permission was revoked in settings
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_DENIED)
            // Force to manual mode if permission revoked
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
            _isAutoTransaction.value = false
        }
        
        _smsPermissionStatus.value = preferenceHelper.getSmsPermissionStatus()
    }
    
    /**
     * Set the automatic transaction processing mode
     */
    fun setAutoTransaction(enabled: Boolean) {
        if (enabled) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable auto mode
                preferenceHelper.setTransactionMode(PreferenceHelper.MODE_AUTOMATIC)
                _isAutoTransaction.value = true
            } else {
                // No permission, can't enable
                _isAutoTransaction.value = false
                
                // Open app settings to enable SMS permission
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } else {
            // Disable auto mode
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
            _isAutoTransaction.value = false
        }
    }
    
    /**
     * Set monthly income
     */
    fun setMonthlyIncome(amount: Double) {
        preferenceHelper.setUserMonthlyIncome(amount)
        _userIncome.value = amount
    }
    
    /**
     * Toggle app security features
     */
    fun setSecurityEnabled(enabled: Boolean) {
        preferenceHelper.setSecurityEnabled(enabled)
        _isSecurityEnabled.value = enabled
    }
    
    /**
     * Toggle screen capture blocking
     */
    fun setScreenCaptureBlocked(blocked: Boolean) {
        preferenceHelper.setScreenCaptureBlocked(blocked)
        _isScreenCaptureBlocked.value = blocked
    }
} 