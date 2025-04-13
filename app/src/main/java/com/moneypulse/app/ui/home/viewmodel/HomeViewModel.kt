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
        
        // Load total monthly income (base + transactions) using the unified calculator
        viewModelScope.launch {
            // Get the base income from preferences
            val baseIncome = preferenceHelper.getUserIncome()
            
            // Get combined income (base + transactions)
            transactionRepository.getTotalMonthlyIncome(baseIncome).collectLatest { totalIncome ->
                _monthlyIncome.value = totalIncome
                Log.d("HomeViewModel", "Updated income to: $totalIncome")
            }
        }
    }
    
    /**
     * Update the user's monthly income
     */
    fun updateMonthlyIncome(income: Double) {
        preferenceHelper.setUserIncome(income)
        
        // Refresh the income calculation to include both base income and transactions
        viewModelScope.launch {
            transactionRepository.getTotalMonthlyIncome(income).collectLatest { totalIncome ->
                _monthlyIncome.value = totalIncome
            }
        }
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
        
        // Refresh data after returning from add transaction screen to ensure
        // any new transactions are reflected in the income and spending totals
        refresh()
    }
} 