package com.moneypulse.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneypulse.app.R
import com.moneypulse.app.ui.dialog.TransactionModeDialog
import com.moneypulse.app.ui.home.HomeScreen
import com.moneypulse.app.ui.home.viewmodel.HomeViewModel
import com.moneypulse.app.ui.settings.SettingsScreen
import com.moneypulse.app.ui.transactions.AddTransactionScreen
import com.moneypulse.app.ui.transactions.TransactionsScreen
import com.moneypulse.app.util.PreferenceHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    
    @Inject
    lateinit var securityHelper: SecurityHelper
    
    // State variable to track if the permission explanation dialog is showing
    private var showSmsPermissionExplanation by mutableStateOf(false)
    
    // State variable to track if biometric authentication is required
    private var isBiometricRequired by mutableStateOf(false)
    
    // State variable to track if biometric authentication is successful
    private var isBiometricAuthenticated by mutableStateOf(false)
    
    // State variable to track biometric auth error message
    private var biometricErrorMessage by mutableStateOf<String?>(null)

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with SMS reading
            checkAndRequestNotificationPermission()
        } else {
            // Handle the case where permission is denied
            // We will proceed without SMS functionality
            checkAndRequestNotificationPermission()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Notification permission granted
            checkBiometricRequirement()
        } else {
            // Handle notification permission denied
            checkBiometricRequirement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show SMS permission explanation dialog on app start
        // instead of directly requesting permissions
        showSmsPermissionExplanationDialog()
        
        setContent {
            MoneyPulseTheme {
                // Show SMS permission explanation dialog if needed
                if (showSmsPermissionExplanation) {
                    SmsPermissionExplanationDialog(
                        onContinue = {
                            showSmsPermissionExplanation = false
                            checkAndRequestSmsPermission()
                        },
                        onSkip = {
                            showSmsPermissionExplanation = false
                            // Skip SMS features and move to next step
                            checkAndRequestNotificationPermission()
                        }
                    )
                }
                // Show biometric authentication dialog if required
                else if (isBiometricRequired && !isBiometricAuthenticated) {
                    BiometricRequiredDialog()
                }
                // Show main content if permissions and authentication are handled
                else {
                    MainScreen()
                }
                
                // Show error message if biometric authentication fails
                biometricErrorMessage?.let { errorMsg ->
                    BiometricErrorDialog(
                        errorMessage = errorMsg,
                        onDismiss = { biometricErrorMessage = null }
                    )
                }
            }
        }
    }
    
    /**
     * Shows the explanation dialog for SMS permissions
     */
    private fun showSmsPermissionExplanationDialog() {
        showSmsPermissionExplanation = true
    }
    
    private fun checkAndRequestSmsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is granted, proceed to check notification permission
                checkAndRequestNotificationPermission()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS) -> {
                // Show permission explanation dialog
                showSmsPermissionExplanationDialog()
            }
            else -> {
                // Request permission
                smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
        }
    }
    
    private fun checkAndRequestNotificationPermission() {
        // Only needed for Android 13 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Notification permission already granted
                    checkBiometricRequirement()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show explanation dialog for notification permission
                }
                else -> {
                    // Request notification permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For older Android versions, no notification permission needed
            checkBiometricRequirement()
        }
    }
    
    /**
     * Check if biometric authentication is required based on user preferences
     * and device capabilities
     */
    private fun checkBiometricRequirement() {
        // Only require biometric if both available on device AND enabled in preferences
        val isBiometricAvailable = securityHelper.isBiometricAvailable()
        val isBiometricEnabled = preferenceHelper.isBiometricEnabled()
        
        if (isBiometricAvailable && isBiometricEnabled) {
            isBiometricRequired = true
            // Authentication will be handled by BiometricRequiredDialog composable
        } else {
            // No biometric required or available, proceed to first launch check
            isBiometricRequired = false
            isBiometricAuthenticated = true
            checkFirstLaunch()
        }
    }
    
    /**
     * Show biometric authentication prompt using SecurityHelper
     */
    private fun showBiometricPrompt() {
        securityHelper.showBiometricPrompt(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButtonText = getString(R.string.biometric_prompt_cancel),
            onSuccess = {
                isBiometricAuthenticated = true
                checkFirstLaunch()
            },
            onError = { errorMessage ->
                biometricErrorMessage = errorMessage
            }
        )
    }
    
    /**
     * Check if this is the first app launch and show the transaction mode dialog
     */
    private fun checkFirstLaunch() {
        if (preferenceHelper.isFirstLaunch()) {
            // Show transaction mode selection dialog
            TransactionModeDialog.show(this, preferenceHelper) { isAutomatic ->
                // Apply the selected mode (can be used for immediate actions if needed)
            }
        }
    }
    
    /**
     * Composable function for biometric authentication dialog
     */
    @Composable
    private fun BiometricRequiredDialog() {
        AlertDialog(
            onDismissRequest = {
                // Don't allow dismissing by tapping outside
            },
            title = { Text(text = stringResource(R.string.biometric_prompt_title)) },
            text = { Text(text = stringResource(R.string.biometric_prompt_subtitle)) },
            confirmButton = {
                Button(
                    onClick = { showBiometricPrompt() }
                ) {
                    Text(stringResource(R.string.biometric_setup_enable))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // For user convenience, allow proceeding without biometric
                        // but don't disable the setting
                        isBiometricAuthenticated = true
                        checkFirstLaunch()
                    }
                ) {
                    Text(stringResource(R.string.biometric_prompt_cancel))
                }
            }
        )
    }
    
    /**
     * Composable function for displaying biometric error messages
     */
    @Composable
    private fun BiometricErrorDialog(errorMessage: String, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.biometric_auth_failed)) },
            text = { 
                Text(
                    text = stringResource(
                        R.string.biometric_auth_error, 
                        errorMessage
                    )
                ) 
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text(text = "OK")
                }
            }
        )
    }
}

/**
 * SMS Permission explanation dialog - follows Material Design guidelines
 * and explains clearly why the app needs SMS permissions
 */
@Composable
fun SmsPermissionExplanationDialog(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Do nothing, force user to make a choice */ },
        title = { Text("SMS Access Required") },
        text = { 
            Column {
                Text(
                    "MoneyPulse needs access to SMS to automatically detect and categorize " +
                    "financial transactions from your bank messages."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This allows the app to track your expenses without manual entry, " +
                    "saving you time and ensuring accurate records."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "We only process bank transaction messages and never share your SMS data " +
                    "with third parties. All processing happens on your device.",
                    style = MaterialTheme.typography.body2
                )
            }
        },
        confirmButton = { 
            Button(
                onClick = onContinue
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip
            ) {
                Text("Skip")
            }
        }
    )
}

@Composable
fun MoneyPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = lightColors(
            primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
            primaryVariant = androidx.compose.ui.graphics.Color(0xFF3700B3),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC5)
        ),
        content = content
    )
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    
    val items = listOf(
        Screen.Home,
        Screen.Transactions,
        Screen.Settings
    )
    
    // Observe navigation changes to reload data when returning to home screen
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    
    // Effect to refresh home data when navigating back to home screen
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Home.route) {
            homeViewModel.refresh()
        }
    }
    
    Scaffold(
        bottomBar = {
            BottomNavigation {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { screen ->
                    BottomNavigationItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.resourceId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = homeViewModel, navController = navController)
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable("add_transaction") {
                AddTransactionScreen(navController = navController)
            }
        }
    }
}

sealed class Screen(val route: String, val resourceId: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Filled.Home)
    object Transactions : Screen("transactions", R.string.transactions, Icons.Filled.List)
    object Settings : Screen("settings", R.string.settings, Icons.Filled.Settings)
} 