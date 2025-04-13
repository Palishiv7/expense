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
import com.moneypulse.app.domain.model.Categories
import com.moneypulse.app.domain.model.Category
import com.moneypulse.app.ui.transactions.viewmodel.AddTransactionViewModel
import kotlinx.coroutines.delay

@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: AddTransactionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val amount by viewModel.amount.collectAsState()
    val merchantName by viewModel.merchantName.collectAsState()
    val description by viewModel.description.collectAsState()
    val category by viewModel.category.collectAsState()
    val isExpense by viewModel.isExpense.collectAsState()
    val saveSuccessful by viewModel.saveSuccessful.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Handle navigation after successful save
    LaunchedEffect(saveSuccessful) {
        if (saveSuccessful) {
            // Small delay to show success message
            delay(500)
            navController.popBackStack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.add_transaction_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Transaction type toggle (Expense/Income)
            TransactionTypeSelector(
                isExpense = isExpense,
                onToggle = { viewModel.toggleTransactionType() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Amount input
            OutlinedTextField(
                value = amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text(stringResource(R.string.amount_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Text("₹") },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Merchant input
            OutlinedTextField(
                value = merchantName,
                onValueChange = { viewModel.updateMerchantName(it) },
                label = { Text(stringResource(R.string.merchant)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.merchant_hint)) },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Category input
            Text(
                text = stringResource(R.string.category),
                fontWeight = FontWeight.Medium
            )
            
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.category)) },
                value = category,
                onValueChange = { viewModel.updateCategory(it) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.category_hint)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colors.primary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Visual Category Selector
            Text(
                text = stringResource(R.string.category),
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Category chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Get all categories from the Categories object
                val allCategories = Categories.ALL
                
                items(allCategories) { categoryItem ->
                    val isSelected = category == categoryItem.id
                    
                    CategoryChip(
                        category = categoryItem,
                        isSelected = isSelected,
                        onClick = { viewModel.updateCategory(categoryItem.id) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
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

@Composable
fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) category.color.copy(alpha = 0.2f) else Color.Transparent
    val borderColor = category.color
    val textColor = if (isSelected) category.color else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
    
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        elevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Category color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(category.color)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Category name
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.body2,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
    }
} 