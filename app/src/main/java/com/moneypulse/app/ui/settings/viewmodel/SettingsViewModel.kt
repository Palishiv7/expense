package com.moneypulse.app.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import com.moneypulse.app.util.PreferenceHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceHelper: PreferenceHelper,
    private val securityHelper: SecurityHelper
) : ViewModel() {
    
    // State for automatic transaction mode
    private val _isAutoTransaction = MutableStateFlow(preferenceHelper.isAutoTransactionEnabled())
    val isAutoTransaction: StateFlow<Boolean> = _isAutoTransaction.asStateFlow()
    
    // State for user's monthly income
    private val _userIncome = MutableStateFlow(preferenceHelper.getUserIncome())
    val userIncome: StateFlow<Double> = _userIncome.asStateFlow()
    
    // State for biometric authentication availability
    private val _isBiometricAvailable = MutableStateFlow(securityHelper.isBiometricAvailable())
    val isBiometricAvailable: StateFlow<Boolean> = _isBiometricAvailable.asStateFlow()
    
    // State for biometric authentication enabled status
    private val _isBiometricEnabled = MutableStateFlow(
        // Only consider it enabled if it's both enabled in preferences AND available on device
        preferenceHelper.isBiometricEnabled() && securityHelper.isBiometricAvailable()
    )
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()
    
    /**
     * Set the automatic transaction processing mode
     */
    fun setAutoTransaction(enabled: Boolean) {
        if (enabled) {
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_AUTOMATIC)
        } else {
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
        }
        _isAutoTransaction.value = enabled
    }
    
    /**
     * Update the user's monthly income
     */
    fun updateIncome(income: Double) {
        preferenceHelper.setUserIncome(income)
        _userIncome.value = income
    }
    
    /**
     * Enable or disable biometric authentication
     */
    fun setBiometricEnabled(enabled: Boolean) {
        // Only update if biometric is available
        if (securityHelper.isBiometricAvailable()) {
            preferenceHelper.setBiometricEnabled(enabled)
            _isBiometricEnabled.value = enabled
        } else if (enabled) {
            // If trying to enable but not available, ensure it's disabled
            preferenceHelper.setBiometricEnabled(false)
            _isBiometricEnabled.value = false
        }
    }
} 