package com.moneypulse.app.ui.transactions.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneypulse.app.data.local.entity.TransactionType
import com.moneypulse.app.data.repository.TransactionRepository
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
    
    // Transaction amount
    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()
    
    // Merchant name
    private val _merchantName = MutableStateFlow("")
    val merchantName: StateFlow<String> = _merchantName.asStateFlow()
    
    // Transaction description
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()
    
    // Category
    private val _category = MutableStateFlow("Other")
    val category: StateFlow<String> = _category.asStateFlow()
    
    // Transaction type (expense or income)
    private val _transactionType = MutableStateFlow(TransactionType.EXPENSE)
    val transactionType: StateFlow<TransactionType> = _transactionType.asStateFlow()
    
    // Save status
    private val _saveSuccessful = MutableStateFlow<Boolean?>(null)
    val saveSuccessful: StateFlow<Boolean?> = _saveSuccessful.asStateFlow()
    
    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Update amount field
    fun updateAmount(newAmount: String) {
        // Only allow valid decimal input
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
        _transactionType.value = if (_transactionType.value == TransactionType.EXPENSE) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }
    
    // Save the transaction
    fun saveTransaction() {
        viewModelScope.launch {
            try {
                // Validate input
                val amountValue = _amount.value.toDoubleOrNull()
                if (amountValue == null || amountValue <= 0) {
                    _errorMessage.value = "Please enter a valid amount"
                    return@launch
                }
                
                if (_merchantName.value.isBlank()) {
                    _errorMessage.value = "Please enter a merchant name"
                    return@launch
                }
                
                // Create TransactionSms object
                val transaction = TransactionSms(
                    sender = "Manual Entry",
                    body = "Manually added transaction",
                    amount = if (_transactionType.value == TransactionType.EXPENSE) amountValue else -amountValue,
                    merchantName = _merchantName.value,
                    timestamp = System.currentTimeMillis(),
                    description = _description.value,
                    category = _category.value
                )
                
                // Save to repository
                transactionRepository.processNewTransactionSms(transaction)
                _saveSuccessful.value = true
                
                // Reset form
                resetForm()
            } catch (e: Exception) {
                _errorMessage.value = "Error saving transaction: ${e.message}"
                _saveSuccessful.value = false
            }
        }
    }
    
    // Reset the form
    fun resetForm() {
        _amount.value = ""
        _merchantName.value = ""
        _description.value = ""
        _category.value = "Other"
        _transactionType.value = TransactionType.EXPENSE
    }
    
    // Reset status values after they've been observed
    fun resetStatus() {
        _saveSuccessful.value = null
        _errorMessage.value = null
    }
} 