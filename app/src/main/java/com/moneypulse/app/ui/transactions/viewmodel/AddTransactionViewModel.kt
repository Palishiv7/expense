package com.moneypulse.app.ui.transactions.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.domain.model.Categories
import com.moneypulse.app.domain.model.TransactionSms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    
    // Amount
    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()
    
    // Merchant name
    private val _merchantName = MutableStateFlow("")
    val merchantName: StateFlow<String> = _merchantName.asStateFlow()
    
    // Description
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()
    
    // Category
    private val _category = MutableStateFlow(Categories.OTHER.id)
    val category: StateFlow<String> = _category.asStateFlow()
    
    // Transaction type (expense by default)
    private val _isExpense = MutableStateFlow(true)
    val isExpense: StateFlow<Boolean> = _isExpense.asStateFlow()
    
    // Status indicators
    private val _saveSuccessful = MutableStateFlow(false)
    val saveSuccessful: StateFlow<Boolean> = _saveSuccessful.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Reset form after navigation
    init {
        resetStatus()
    }
    
    // Update amount
    fun updateAmount(newAmount: String) {
        // Only allow valid decimal numbers
        if (newAmount.isEmpty() || newAmount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _amount.value = newAmount
        }
    }
    
    // Update merchant name
    fun updateMerchantName(newName: String) {
        _merchantName.value = newName
    }
    
    // Update description
    fun updateDescription(newDescription: String) {
        _description.value = newDescription
    }
    
    // Update category
    fun updateCategory(newCategory: String) {
        _category.value = newCategory
    }
    
    // Toggle transaction type between expense and income
    fun toggleTransactionType() {
        _isExpense.value = !_isExpense.value
    }
    
    // Save the transaction
    fun saveTransaction() {
        viewModelScope.launch {
            try {
                // Validate input
                if (_amount.value.isEmpty() || _merchantName.value.isEmpty()) {
                    _errorMessage.value = "Please enter amount and merchant name"
                    return@launch
                }
                
                val amountValue = _amount.value.toDoubleOrNull()
                if (amountValue == null || amountValue <= 0) {
                    _errorMessage.value = "Please enter a valid amount"
                    return@launch
                }
                
                // Create transaction with or without a sign based on type
                val finalAmount = if (_isExpense.value) -amountValue else amountValue
                
                // Create transaction object
                val transaction = TransactionSms(
                    sender = "Manual Entry",
                    body = _description.value.ifEmpty { "Manual transaction" },
                    amount = finalAmount,
                    merchantName = _merchantName.value,
                    category = _category.value,
                    timestamp = Date().time
                )
                
                // Save to repository
                transactionRepository.processNewTransactionSms(transaction)
                
                // Reset form first, then set success flag
                resetForm()
                
                // Set success flag last (this will trigger navigation)
                _saveSuccessful.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Error saving transaction: ${e.message}"
            }
        }
    }
    
    // Reset form after successful save
    private fun resetForm() {
        _amount.value = ""
        _merchantName.value = ""
        _description.value = ""
        _category.value = Categories.OTHER.id
        _isExpense.value = true
    }
    
    // Reset status values after they've been observed
    fun resetStatus() {
        _saveSuccessful.value = false
        _errorMessage.value = null
    }
} 