package com.moneypulse.app.ui.transactions.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.domain.model.TransactionSms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    
    // All transactions to display
    private val _transactions = MutableStateFlow<List<TransactionSms>>(emptyList())
    val transactions: StateFlow<List<TransactionSms>> = _transactions.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // Load transactions when ViewModel is created
        loadTransactions()
    }
    
    /**
     * Load all transactions
     */
    private fun loadTransactions() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Load all transactions
            transactionRepository.getRecentTransactions(50).collectLatest { transactions ->
                _transactions.value = transactions
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh transactions (can be called by pull-to-refresh)
     */
    fun refresh() {
        loadTransactions()
    }
} 