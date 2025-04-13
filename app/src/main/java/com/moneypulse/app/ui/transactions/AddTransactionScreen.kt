package com.moneypulse.app.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.moneypulse.app.R
import com.moneypulse.app.data.local.entity.TransactionType
import com.moneypulse.app.ui.transactions.viewmodel.AddTransactionViewModel

@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val amount by viewModel.amount.collectAsState()
    val merchantName by viewModel.merchantName.collectAsState()
    val description by viewModel.description.collectAsState()
    val category by viewModel.category.collectAsState()
    val transactionType by viewModel.transactionType.collectAsState()
    val saveSuccessful by viewModel.saveSuccessful.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Handle navigation after successful save
    LaunchedEffect(saveSuccessful) {
        if (saveSuccessful == true) {
            // Wait briefly to show success message
            kotlinx.coroutines.delay(1000)
            navController.popBackStack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_transaction_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transaction type selector
            TransactionTypeSelector(
                isExpense = transactionType == TransactionType.EXPENSE,
                onToggle = { viewModel.toggleTransactionType() }
            )
            
            // Amount input
            Text(
                text = stringResource(R.string.amount),
                fontWeight = FontWeight.Medium
            )
            
            OutlinedTextField(
                value = amount,
                onValueChange = { viewModel.updateAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.amount_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { Text("₹", fontSize = 18.sp) },
                singleLine = true
            )
            
            // Merchant name input
            Text(
                text = stringResource(R.string.merchant),
                fontWeight = FontWeight.Medium
            )
            
            OutlinedTextField(
                value = merchantName,
                onValueChange = { viewModel.updateMerchantName(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.merchant_hint)) },
                singleLine = true
            )
            
            // Category input
            Text(
                text = stringResource(R.string.category),
                fontWeight = FontWeight.Medium
            )
            
            OutlinedTextField(
                value = category,
                onValueChange = { viewModel.updateCategory(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.category_hint)) },
                singleLine = true
            )
            
            // Description input (optional)
            Text(
                text = stringResource(R.string.description_optional),
                fontWeight = FontWeight.Medium
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.updateDescription(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.description_hint)) },
                maxLines = 3
            )
            
            // Error message display
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            
            // Save button
            Button(
                onClick = { viewModel.saveTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = amount.isNotEmpty() && merchantName.isNotEmpty()
            ) {
                Text(
                    stringResource(R.string.save_transaction),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionTypeSelector(
    isExpense: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TransactionTypeButton(
                    text = stringResource(R.string.expense),
                    isSelected = isExpense,
                    color = Color(0xFFE53935),
                    onClick = { if (!isExpense) onToggle() }
                )
            }
            
            Box(modifier = Modifier.weight(1f)) {
                TransactionTypeButton(
                    text = stringResource(R.string.income),
                    isSelected = !isExpense,
                    color = Color(0xFF43A047),
                    onClick = { if (isExpense) onToggle() }
                )
            }
        }
    }
}

@Composable
fun TransactionTypeButton(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) color else Color.Transparent
    val textColor = if (isSelected) Color.White else Color.Gray
    
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = textColor
        ),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
} 