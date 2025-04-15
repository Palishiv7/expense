package com.moneypulse.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for handling app preferences with encryption
 * Enhanced with security-focused preferences
 */
@Singleton
class PreferenceHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFERENCES_FILE = "moneypulse_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_TRANSACTION_MODE = "transaction_mode"
        private const val KEY_USER_INCOME = "user_income"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        
        // SMS Permission preferences
        private const val KEY_SMS_PERMISSION_DIALOG_SHOWN = "sms_permission_dialog_shown"
        private const val KEY_SMS_PERMISSION_STATUS = "sms_permission_status"
        
        // Transaction modes
        const val MODE_AUTOMATIC = "automatic"
        const val MODE_MANUAL = "manual"
        
        // SMS Permission status values
        const val PERMISSION_STATUS_NOT_ASKED = "NOT_ASKED"
        const val PERMISSION_STATUS_GRANTED = "GRANTED"
        const val PERMISSION_STATUS_DENIED = "DENIED"
        const val PERMISSION_STATUS_SKIPPED = "SKIPPED"
        
        // Default income value
        const val DEFAULT_INCOME = 45000.0
        
        // Security preferences
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SCREEN_CAPTURE_BLOCKED = "screen_capture_blocked"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_SECURE_MODE = "secure_mode"
        
        // Default security settings
        private const val DEFAULT_AUTO_LOCK_TIMEOUT = 5 // 5 minutes
    }
    
    // Create or retrieve the encrypted shared preferences
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFERENCES_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        return encryptedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    /**
     * Mark first launch as completed
     */
    fun completeFirstLaunch() {
        encryptedPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingCompleted(): Boolean {
        return encryptedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    /**
     * Set onboarding completed status
     */
    fun setOnboardingCompleted(completed: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        if (completed) {
            // Also mark first launch as completed when onboarding is completed
            completeFirstLaunch()
        }
    }
    
    /**
     * Get current transaction processing mode
     */
    fun getTransactionMode(): String {
        return encryptedPrefs.getString(KEY_TRANSACTION_MODE, MODE_MANUAL) ?: MODE_MANUAL
    }
    
    /**
     * Set transaction processing mode
     */
    fun setTransactionMode(mode: String) {
        encryptedPrefs.edit().putString(KEY_TRANSACTION_MODE, mode).apply()
    }
    
    /**
     * Check if automatic transaction processing is enabled
     */
    fun isAutoTransactionEnabled(): Boolean {
        return getTransactionMode() == MODE_AUTOMATIC
    }
    
    /**
     * Get user's monthly income
     */
    fun getUserIncome(): Double {
        return encryptedPrefs.getFloat(KEY_USER_INCOME, DEFAULT_INCOME.toFloat()).toDouble()
    }
    
    /**
     * Set user's monthly income
     */
    fun setUserIncome(income: Double) {
        encryptedPrefs.edit().putFloat(KEY_USER_INCOME, income.toFloat()).apply()
    }
    
    // --- SECURITY SETTINGS ---
    
    /**
     * Check if biometric authentication is enabled
     */
    fun isBiometricEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
    
    /**
     * Enable or disable biometric authentication
     */
    fun setBiometricEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    /**
     * Check if screen capture is blocked
     */
    fun isScreenCaptureBlocked(): Boolean {
        return encryptedPrefs.getBoolean(KEY_SCREEN_CAPTURE_BLOCKED, true) // Blocked by default
    }
    
    /**
     * Enable or disable screen capture blocking
     */
    fun setScreenCaptureBlocked(blocked: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_SCREEN_CAPTURE_BLOCKED, blocked).apply()
    }
    
    /**
     * Get auto-lock timeout in minutes
     */
    fun getAutoLockTimeout(): Int {
        return encryptedPrefs.getInt(KEY_AUTO_LOCK_TIMEOUT, DEFAULT_AUTO_LOCK_TIMEOUT)
    }
    
    /**
     * Set auto-lock timeout in minutes
     */
    fun setAutoLockTimeout(timeoutMinutes: Int) {
        encryptedPrefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, timeoutMinutes).apply()
    }
    
    /**
     * Check if secure mode is enabled (hides financial data when app is in background)
     */
    fun isSecureModeEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_SECURE_MODE, true) // Enabled by default
    }
    
    /**
     * Enable or disable secure mode
     */
    fun setSecureModeEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_SECURE_MODE, enabled).apply()
    }

    /**
     * Check if SMS permission dialog has been shown to the user
     */
    fun hasSmsPermissionDialogShown(): Boolean {
        return encryptedPrefs.getBoolean(KEY_SMS_PERMISSION_DIALOG_SHOWN, false)
    }

    /**
     * Mark that SMS permission dialog has been shown to the user
     */
    fun markSmsPermissionDialogShown() {
        encryptedPrefs.edit().putBoolean(KEY_SMS_PERMISSION_DIALOG_SHOWN, true).apply()
    }

    /**
     * Get current SMS permission status
     */
    fun getSmsPermissionStatus(): String {
        return encryptedPrefs.getString(KEY_SMS_PERMISSION_STATUS, PERMISSION_STATUS_NOT_ASKED) 
            ?: PERMISSION_STATUS_NOT_ASKED
    }

    /**
     * Set SMS permission status
     */
    fun setSmsPermissionStatus(status: String) {
        encryptedPrefs.edit().putString(KEY_SMS_PERMISSION_STATUS, status).apply()
    }
} 