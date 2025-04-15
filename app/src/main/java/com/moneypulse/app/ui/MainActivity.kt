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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    
    // State variable to track if the permission explanation dialog is showing
    private var showSmsPermissionExplanation by mutableStateOf(false)

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with SMS reading
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
            // If permission is granted, default to manual approval mode
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
            checkAndRequestNotificationPermission()
        } else {
            // Handle the case where permission is denied
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_DENIED)
            // We will proceed without SMS functionality in fully manual mode
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
            checkAndRequestNotificationPermission()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Notification permission granted
            checkFirstLaunch()
        } else {
            // Handle notification permission denied
            checkFirstLaunch()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if the SMS permission dialog has been shown before
        if (!preferenceHelper.hasSmsPermissionDialogShown()) {
            // If not shown before, prepare to show SMS permission explanation dialog
            showSmsPermissionExplanation = true
        } else {
            // Dialog already shown before, proceed based on previous choice
            proceedBasedOnSmsPermissionStatus()
        }
        
        setContent {
            MoneyPulseTheme {
                // Show SMS permission explanation dialog if needed
                if (showSmsPermissionExplanation) {
                    SmsPermissionExplanationDialog(
                        onContinue = {
                            showSmsPermissionExplanation = false
                            // Mark that dialog has been shown
                            preferenceHelper.markSmsPermissionDialogShown()
                            checkAndRequestSmsPermission()
                        },
                        onSkip = {
                            showSmsPermissionExplanation = false
                            // Mark that dialog has been shown and user skipped
                            preferenceHelper.markSmsPermissionDialogShown()
                            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_SKIPPED)
                            // Ensure we're in fully manual mode if user skips
                            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
                            // Skip SMS features and move to next step
                            checkAndRequestNotificationPermission()
                        }
                    )
                }
                
                MainScreen()
            }
        }
    }
    
    /**
     * Proceed with app initialization based on previously saved SMS permission status
     */
    private fun proceedBasedOnSmsPermissionStatus() {
        when (preferenceHelper.getSmsPermissionStatus()) {
            PreferenceHelper.PERMISSION_STATUS_GRANTED -> {
                // Permission already granted, check if it's still actually granted
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS
                    ) == PackageManager.PERMISSION_GRANTED) {
                    // Permission is still valid, proceed
                    checkAndRequestNotificationPermission()
                } else {
                    // Permission was revoked in settings, update status and handle
                    preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_DENIED)
                    preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
                    checkAndRequestNotificationPermission()
                }
            }
            PreferenceHelper.PERMISSION_STATUS_DENIED, 
            PreferenceHelper.PERMISSION_STATUS_SKIPPED -> {
                // User previously denied or skipped, respect that choice
                checkAndRequestNotificationPermission()
            }
            else -> {
                // Unexpected state, show dialog again
                showSmsPermissionExplanation = true
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
                // Permission is granted, update status
                preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
                // Proceed to check notification permission
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
                    checkFirstLaunch()
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
            checkFirstLaunch()
        }
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