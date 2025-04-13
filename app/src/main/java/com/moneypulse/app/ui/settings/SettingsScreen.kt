package com.moneypulse.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneypulse.app.R
import com.moneypulse.app.ui.debug.DebugLogActivity
import com.moneypulse.app.ui.settings.viewmodel.SettingsViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isAutoTransactionEnabled by viewModel.isAutoTransaction.collectAsState()
    val userIncome by viewModel.userIncome.collectAsState()
    val context = LocalContext.current
    
    val transactionModeTitle = stringResource(id = R.string.transaction_mode_title)
    val autoModeDescription = stringResource(id = R.string.transaction_mode_auto_description)
    val manualModeDescription = stringResource(id = R.string.transaction_mode_manual_description)
    
    // Income input state
    val (incomeText, setIncomeText) = remember { mutableStateOf(userIncome.toString()) }
    var showIncomeDialog by remember { mutableStateOf(false) }
    
    // Format income for display
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    val formattedIncome = formatter.format(userIncome)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Monthly Income Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Monthly Income",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = formattedIncome,
                            style = MaterialTheme.typography.subtitle1
                        )
                        
                        Text(
                            text = "Your monthly income used for calculations",
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Button(
                        onClick = { 
                            setIncomeText(userIncome.toString())
                            showIncomeDialog = true 
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text("Edit", color = MaterialTheme.colors.onPrimary)
                    }
                }
            }
        }
        
        // Transaction Mode Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = transactionModeTitle,
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isAutoTransactionEnabled) 
                                    stringResource(id = R.string.transaction_mode_auto)
                                  else 
                                    stringResource(id = R.string.transaction_mode_manual),
                            style = MaterialTheme.typography.subtitle1
                        )
                        
                        Text(
                            text = if (isAutoTransactionEnabled) autoModeDescription else manualModeDescription,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Switch(
                        checked = isAutoTransactionEnabled,
                        onCheckedChange = { viewModel.setAutoTransaction(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colors.primary
                        )
                    )
                }
            }
        }
        
        // SMS Debug Logs Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_section_title),
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.debug_logs_title),
                            style = MaterialTheme.typography.subtitle1
                        )
                        
                        Text(
                            text = stringResource(R.string.debug_logs_description),
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Button(
                        onClick = { 
                            val intent = Intent(context, DebugLogActivity::class.java)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text(stringResource(R.string.debug_view_logs), color = MaterialTheme.colors.onPrimary)
                    }
                }
            }
        }
    }
    
    // Income Edit Dialog
    if (showIncomeDialog) {
        AlertDialog(
            onDismissRequest = { showIncomeDialog = false },
            title = { Text("Set Monthly Income") },
            text = {
                Column {
                    Text(
                        "Enter your monthly income amount:",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = incomeText,
                        onValueChange = { setIncomeText(it.replace(Regex("[^0-9]"), "")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newIncome = incomeText.toDoubleOrNull() ?: userIncome
                        viewModel.updateIncome(newIncome)
                        showIncomeDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showIncomeDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
} 