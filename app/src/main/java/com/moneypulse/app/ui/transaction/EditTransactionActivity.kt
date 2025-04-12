package com.moneypulse.app.ui.transaction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.moneypulse.app.R
import com.moneypulse.app.data.repository.TransactionRepository
import com.moneypulse.app.domain.model.TransactionSms
import com.moneypulse.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import android.widget.Toast

@AndroidEntryPoint
class EditTransactionActivity : ComponentActivity() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get transaction from intent
        val transaction = intent.getParcelableExtra<TransactionSms>(NotificationHelper.EXTRA_TRANSACTION_DATA)
        
        if (transaction == null) {
            // No transaction data, close activity
            finish()
            return
        }

        setContent {
            EditTransactionScreen(
                transaction = transaction,
                onSave = { updatedTransaction ->
                    saveTransaction(updatedTransaction)
                },
                onCancel = {
                    finish()
                }
            )
        }
    }

    private fun saveTransaction(transaction: TransactionSms) {
        kotlinx.coroutines.MainScope().launch {
            transactionRepository.processNewTransactionSms(transaction)
            Toast.makeText(
                this@EditTransactionActivity,
                getString(R.string.transaction_added),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}

@Composable
fun EditTransactionScreen(
    transaction: TransactionSms,
    onSave: (TransactionSms) -> Unit,
    onCancel: () -> Unit
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    
    var description by remember { mutableStateOf("Transaction at ${transaction.merchantName}") }
    var category by remember { mutableStateOf("General") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.add_transaction)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Amount display
            Text(text = stringResource(id = R.string.amount))
            Text(
                text = formatter.format(transaction.amount),
                style = MaterialTheme.typography.h5
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Merchant name
            Text(text = "Merchant")
            Text(
                text = transaction.merchantName,
                style = MaterialTheme.typography.h6
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description field
            Text(text = stringResource(id = R.string.description))
            TextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Category field (could be enhanced with dropdown)
            Text(text = stringResource(id = R.string.category))
            TextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCancel() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }
                
                Button(
                    onClick = {
                        // Create updated transaction with user edits
                        val updatedTransaction = TransactionSms(
                            sender = transaction.sender,
                            body = transaction.body,
                            amount = transaction.amount,
                            merchantName = transaction.merchantName,
                            timestamp = transaction.timestamp,
                            description = description,
                            category = category
                        )
                        onSave(updatedTransaction)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.save))
                }
            }
        }
    }
} 