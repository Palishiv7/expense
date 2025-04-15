package com.moneypulse.app.ui.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.moneypulse.app.BuildConfig
import com.moneypulse.app.R
import com.moneypulse.app.ui.debug.DebugLogActivity
import com.moneypulse.app.ui.privacy.PrivacyPolicyActivity
import com.moneypulse.app.ui.settings.viewmodel.SettingsViewModel
import com.moneypulse.app.util.PreferenceHelper
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import android.widget.Toast

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isAutoTransaction by viewModel.isAutoTransaction.collectAsState()
    val securityEnabled by viewModel.isSecurityEnabled.collectAsState()
    val screenCaptureBlocked by viewModel.isScreenCaptureBlocked.collectAsState()
    val smsPermissionStatus by viewModel.smsPermissionStatus.collectAsState()
    val userIncome by viewModel.userIncome.collectAsState()
    val context = LocalContext.current
    
    // Refresh permission status when the screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshSmsPermissionStatus()
    }
    
    // Use lifecycle-aware API to detect when returning from settings
    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        
        // Create the lifecycle observer properly
        val lifecycleObserver = object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                // Refresh permissions when activity resumes
                viewModel.refreshSmsPermissionStatus()
            }
        }
        
        // Add the observer
        activity?.lifecycle?.addObserver(lifecycleObserver)
        
        onDispose {
            // Clean up when leaving the screen - remove the correct observer
            activity?.lifecycle?.removeObserver(lifecycleObserver)
        }
    }
    
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
        
        // Transaction Mode Section with integrated SMS permission handling
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
                
                // Add SMS permission status info if not granted
                if (smsPermissionStatus != PreferenceHelper.PERMISSION_STATUS_GRANTED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(4.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SMS permission required for automatic detection",
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFFD32F2F),
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp)
                        )
                        
                        TextButton(
                            onClick = { viewModel.requestSmsPermission() },
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text("Enable", color = MaterialTheme.colors.primary)
                        }
                    }
                }
                
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
                            text = if (isAutoTransaction) 
                                    stringResource(id = R.string.transaction_mode_auto)
                                  else 
                                    stringResource(id = R.string.transaction_mode_manual),
                            style = MaterialTheme.typography.subtitle1
                        )
                        
                        Text(
                            text = if (isAutoTransaction) autoModeDescription else manualModeDescription,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Switch(
                        checked = isAutoTransaction,
                        onCheckedChange = { viewModel.setAutoTransaction(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colors.primary
                        ),
                        // Disable switch if SMS permission not granted
                        enabled = smsPermissionStatus == PreferenceHelper.PERMISSION_STATUS_GRANTED
                    )
                }
            }
        }
        
        // Privacy & Security Section
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
                    text = "Privacy & Security",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Privacy Policy Row
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
                            text = "Privacy Policy",
                            style = MaterialTheme.typography.subtitle1
                        )
                        
                        Text(
                            text = "View our privacy policy and SMS data handling",
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Button(
                        onClick = { 
                            val intent = Intent(context, PrivacyPolicyActivity::class.java)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text("View", color = MaterialTheme.colors.onPrimary)
                    }
                }
            }
        }
        
        // SMS Debug Logs Section - Only show in debug builds
        if (BuildConfig.DEBUG) {
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
                        viewModel.setMonthlyIncome(newIncome)
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