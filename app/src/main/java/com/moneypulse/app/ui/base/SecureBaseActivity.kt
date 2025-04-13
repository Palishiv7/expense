package com.moneypulse.app.ui.base

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.moneypulse.app.R
import com.moneypulse.app.util.PreferenceHelper
import com.moneypulse.app.util.SecurityHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Base activity class with built-in security features:
 * - Screen capture prevention
 * - Automatic session timeout
 * - Biometric re-authentication
 * - Security features enforcement
 */
@AndroidEntryPoint
abstract class SecureBaseActivity : AppCompatActivity() {
    
    @Inject
    lateinit var securityHelper: SecurityHelper
    
    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    
    // Last interaction timestamp
    private var lastInteractionTime = System.currentTimeMillis()
    
    // Security check handler
    private val securityHandler = Handler(Looper.getMainLooper())
    private val securityRunnable = Runnable { checkSecurityTimeout() }
    
    // Security timeout check interval - every 30 seconds
    private val securityCheckInterval = 30_000L
    
    // Flag to determine if the screen is currently locked
    private var isScreenLocked = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots if enabled in settings
        if (preferenceHelper.isScreenCaptureBlocked()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Reset interaction time when activity becomes visible
        lastInteractionTime = System.currentTimeMillis()
        
        // Start security check
        startSecurityCheck()
        
        // Verify biometric authentication if screen was locked
        if (isScreenLocked) {
            promptBiometricAuthentication()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Stop security check when activity is not visible
        stopSecurityCheck()
    }
    
    override fun onUserInteraction() {
        super.onUserInteraction()
        
        // Update interaction time on any user activity
        lastInteractionTime = System.currentTimeMillis()
    }
    
    /**
     * Start scheduled security checks
     */
    private fun startSecurityCheck() {
        securityHandler.postDelayed(securityRunnable, securityCheckInterval)
    }
    
    /**
     * Stop scheduled security checks
     */
    private fun stopSecurityCheck() {
        securityHandler.removeCallbacks(securityRunnable)
    }
    
    /**
     * Check if the app has been inactive too long
     * and should require re-authentication
     */
    private fun checkSecurityTimeout() {
        val currentTime = System.currentTimeMillis()
        val inactivityTime = currentTime - lastInteractionTime
        
        // Get timeout from preferences (in minutes, convert to milliseconds)
        val timeoutMinutes = preferenceHelper.getAutoLockTimeout()
        val timeoutMillis = timeoutMinutes * 60 * 1000L
        
        // Lock screen if inactive for too long
        if (inactivityTime > timeoutMillis) {
            isScreenLocked = true
            promptBiometricAuthentication()
        }
        
        // Schedule next check
        securityHandler.postDelayed(securityRunnable, securityCheckInterval)
    }
    
    /**
     * Show biometric authentication prompt
     */
    private fun promptBiometricAuthentication() {
        // Skip authentication if biometric is not available
        if (!securityHelper.isBiometricAvailable()) {
            isScreenLocked = false
            return
        }
        
        // Show authentication dialog
        securityHelper.showBiometricPrompt(
            activity = this as FragmentActivity,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButtonText = getString(R.string.biometric_prompt_cancel),
            onSuccess = {
                // Authentication successful
                isScreenLocked = false
                lastInteractionTime = System.currentTimeMillis()
            },
            onError = { errorMessage ->
                // Handle authentication error
                // For security reasons, we keep the screen locked on failure
                // The user will need to retry authentication
            }
        )
    }
} 