package com.moneypulse.app.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.ui.home.viewmodel.HomeViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    // Collect state from ViewModel
    val monthlySpending by viewModel.monthlySpending.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // For debug triple-tap
    val context = LocalContext.current
    var tapCount by remember { mutableStateOf(0) }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Debug: Triple-tap on header to access test screen
            DashboardHeader(
                modifier = Modifier.clickable {
                    tapCount++
                    if (tapCount == 3) {
                        // Reset count and launch test activity
                        tapCount = 0
                        launchTestActivity(context)
                    }
                }
            )
            SpendingSummaryCard(monthlySpending)
            RecentTransactionsCard(recentTransactions, isLoading)
        }
        
        // Floating Action Button for adding manual transactions
        FloatingActionButton(
            onClick = { viewModel.addManualTransaction() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            backgroundColor = MaterialTheme.colors.primary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Transaction",
                tint = Color.White
            )
        }
    }
}

// Launch the test activity
private fun launchTestActivity(context: Context) {
    // Only in debug builds (will be checked by manifest)
    val intent = Intent().apply {
        setClassName(context, "com.moneypulse.app.ui.test.TestActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
fun DashboardHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Hello, User",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your financial summary",
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun SpendingSummaryCard(monthlySpending: Double) {
    // Format the amounts for display
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    
    // For the MVP, we're using placeholder values for income and balance
    val spentAmount = formatter.format(monthlySpending)
    val incomeAmount = formatter.format(45000)
    val balanceAmount = formatter.format(45000 - monthlySpending)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "This Month's Spending",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AmountColumn(title = "Spent", amount = spentAmount, color = Color(0xFFE53935))
                AmountColumn(title = "Income", amount = incomeAmount, color = Color(0xFF43A047))
                AmountColumn(title = "Balance", amount = balanceAmount, color = Color(0xFF1976D2))
            }
        }
    }
}

@Composable
fun AmountColumn(title: String, amount: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = amount,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun RecentTransactionsCard(transactions: List<TransactionSms>, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recent Transactions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (isLoading) {
                // Show loading indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (transactions.isEmpty()) {
                // Show empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transactions yet",
                        color = Color.Gray
                    )
                }
            } else {
                // Show transaction list
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Show max 3 transactions on home screen
                    transactions.take(3).forEach { transaction ->
                        TransactionItem(transaction)
                    }
                    
                    // "See all" button if we have more than 3 transactions
                    if (transactions.size > 3) {
                        TextButton(
                            onClick = { /* TODO: Navigate to transactions screen */ },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("See all transactions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: TransactionSms) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = transaction.merchantName,
                fontWeight = FontWeight.Medium
            )
        }
        
        Text(
            text = formatter.format(transaction.amount),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935) // Assuming all SMS transactions are expenses
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen()
    }
} 