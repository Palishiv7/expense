package com.moneypulse.app.ui.home.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val preferenceHelper: PreferenceHelper
) : ViewModel() {
    
    // Monthly spending for the dashboard
    private val _monthlySpending = MutableStateFlow(0.0)
    val monthlySpending: StateFlow<Double> = _monthlySpending.asStateFlow()
    
    // User's monthly income
    private val _monthlyIncome = MutableStateFlow(preferenceHelper.getUserIncome())
    val monthlyIncome: StateFlow<Double> = _monthlyIncome.asStateFlow()
    
    // Recent transactions to show on home screen
    private val _recentTransactions = MutableStateFlow<List<TransactionSms>>(emptyList())
    val recentTransactions: StateFlow<List<TransactionSms>> = _recentTransactions.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Navigation event for adding manual transaction
    private val _navigateToAddTransaction = MutableStateFlow(false)
    val navigateToAddTransaction: StateFlow<Boolean> = _navigateToAddTransaction.asStateFlow()
    
    init {
        // Load data when ViewModel is created
        loadData()
        
        // Set up observer for income transactions
        observeIncomeTransactions()
    }
    
    /**
     * Load monthly spending and recent transactions
     */
    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Load monthly spending
            transactionRepository.getMonthlySpending().collectLatest { spending ->
                _monthlySpending.value = spending
            }
        }
        
        viewModelScope.launch {
            // Load recent transactions (limit to 5)
            transactionRepository.getRecentTransactions(5).collectLatest { transactions ->
                _recentTransactions.value = transactions
                _isLoading.value = false
            }
        }
        
        // Refresh income value from preferences
        _monthlyIncome.value = preferenceHelper.getUserIncome()
    }
    
    /**
     * Observe income transactions to update monthly income
     */
    private fun observeIncomeTransactions() {
        viewModelScope.launch {
            // Get income transactions for the current month
            transactionRepository.getMonthlyIncomeTransactions().collectLatest { incomeTransactions ->
                // Log the income transactions we received
                Log.d("HomeViewModel", "Received ${incomeTransactions.size} income transactions")
                incomeTransactions.forEach { transaction ->
                    Log.d("HomeViewModel", "Income transaction: ${transaction.merchantName}, amount: ${transaction.amount}")
                }
                
                // Calculate total from transactions and add to base income
                var totalIncome = preferenceHelper.getUserIncome()
                Log.d("HomeViewModel", "Base income from preferences: $totalIncome")
                
                // Add up all income transactions for the month
                var transactionsTotal = 0.0
                incomeTransactions.forEach { transaction ->
                    if (transaction.amount > 0) {
                        transactionsTotal += transaction.amount
                    }
                }
                
                totalIncome += transactionsTotal
                Log.d("HomeViewModel", "Added $transactionsTotal from transactions, new total: $totalIncome")
                
                // Update the income value
                _monthlyIncome.value = totalIncome
            }
        }
    }
    
    /**
     * Update the user's monthly income
     */
    fun updateMonthlyIncome(income: Double) {
        preferenceHelper.setUserIncome(income)
        _monthlyIncome.value = income
    }
    
    /**
     * Refresh data (can be called by pull-to-refresh)
     */
    fun refresh() {
        loadData()
    }
    
    /**
     * Add a manual transaction - triggers navigation to add transaction screen
     */
    fun addManualTransaction() {
        _navigateToAddTransaction.value = true
    }
    
    /**
     * Called after navigation is handled to reset the state
     */
    fun onAddTransactionNavigated() {
        _navigateToAddTransaction.value = false
    }
} 