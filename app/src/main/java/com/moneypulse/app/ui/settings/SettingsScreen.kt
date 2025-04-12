package com.moneypulse.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneypulse.app.R
import com.moneypulse.app.ui.settings.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isAutoTransactionEnabled by viewModel.isAutoTransaction.collectAsState()
    val transactionModeTitle = stringResource(id = R.string.transaction_mode_title)
    val autoModeDescription = stringResource(id = R.string.transaction_mode_auto_description)
    val manualModeDescription = stringResource(id = R.string.transaction_mode_manual_description)
    
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
    }
} 