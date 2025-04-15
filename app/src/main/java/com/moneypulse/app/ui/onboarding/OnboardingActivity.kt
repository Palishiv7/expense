package com.moneypulse.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moneypulse.app.ui.MainActivity
import com.moneypulse.app.util.PreferenceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
        } else {
            preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_DENIED)
            preferenceHelper.setTransactionMode(PreferenceHelper.MODE_MANUAL)
        }
        
        // After SMS permission is handled, check for notification permission
        checkAndRequestNotificationPermission()
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Regardless of notification permission result, continue to MainActivity
        continueToMainActivity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen()
        }
        
        super.onCreate(savedInstanceState)
        
        // If onboarding has been completed before, go directly to MainActivity
        if (preferenceHelper.hasCompletedOnboarding()) {
            continueToMainActivity()
            return
        }

        setContent {
            OnboardingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    OnboardingScreen(
                        onGetStarted = { 
                            // Mark onboarding as completed
                            preferenceHelper.setOnboardingCompleted(true)
                            // Request SMS permission
                            requestSmsPermission()
                        }
                    )
                }
            }
        }
    }
    
    private fun requestSmsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                preferenceHelper.setSmsPermissionStatus(PreferenceHelper.PERMISSION_STATUS_GRANTED)
                checkAndRequestNotificationPermission()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS) -> {
                // Show in-app rationale and then request permission
                smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
            else -> {
                // Request permission directly
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
                    // Permission already granted
                    continueToMainActivity()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show in-app rationale and then request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request permission directly
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Notification permission not needed
            continueToMainActivity()
        }
    }
    
    private fun continueToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
} 