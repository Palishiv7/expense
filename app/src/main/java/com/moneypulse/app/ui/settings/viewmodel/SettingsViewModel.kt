package com.moneypulse.app.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceHelper: PreferenceHelper
) : ViewModel() {
    
    // State for automatic transaction mode
    private val _isAutoTransaction = MutableStateFlow(preferenceHelper.isAutoTransactionEnabled())
    val isAutoTransaction: StateFlow<Boolean> = _isAutoTransaction.asStateFlow()
    
    // State for user's monthly income
    private val _userIncome = MutableStateFlow(preferenceHelper.getUserIncome())
    val userIncome: StateFlow<Double> = _userIncome.asStateFlow()
    
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
} 