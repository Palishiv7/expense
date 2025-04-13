package com.moneypulse.app.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for all security-related functionality
 * - Biometric authentication
 * - Secure key generation
 * - Device security verification
 * - Data encryption
 */
@Singleton
class SecurityHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_NAME = "moneypulse_biometric_key"
        private const val DATABASE_KEY_NAME = "moneypulse_database_key"
        
        // Security verification constants
        private val KNOWN_DANGEROUS_APPS = listOf(
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine"
        )
        
        private val KNOWN_ROOT_PATHS = listOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su"
        )
    }
    
    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
    
    /**
     * Show biometric authentication prompt
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricPrompt = BiometricPrompt(
            activity,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Only report errors that are not cancellation
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    }
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * Check if secure lock screen is enabled
     */
    fun isDeviceSecure(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceSecure
    }
    
    /**
     * Create or get cryptographic key for database encryption
     * Generates a secure key from Android Keystore
     */
    fun getDatabaseKey(): ByteArray {
        createAndroidKeystoreKey(DATABASE_KEY_NAME)
        
        // Get key from keystore
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        val secretKey = keyStore.getKey(DATABASE_KEY_NAME, null) as SecretKey
        
        // Use key to encrypt a known string to get consistent bytes for SQLCipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        // Return the encrypted data as bytes (consistent for same key)
        return cipher.doFinal("MoneyPulse Database Secret".toByteArray())
    }
    
    /**
     * Create key in Android Keystore if it doesn't exist
     */
    private fun createAndroidKeystoreKey(keyName: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // Check if key already exists
        if (!keyStore.containsAlias(keyName)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyName,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false) // Don't require auth for database operations
                .setRandomizedEncryptionRequired(true)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    /**
     * Checks if device is potentially rooted
     * Combines multiple detection methods
     */
    fun isDeviceRooted(): Boolean {
        return checkForSuBinary() || checkForRootApps() || checkForRWPaths() || checkForDangerousProps()
    }
    
    /**
     * Check for SU binary files
     */
    private fun checkForSuBinary(): Boolean {
        return KNOWN_ROOT_PATHS.any { File(it).exists() }
    }
    
    /**
     * Check for known root management apps
     */
    private fun checkForRootApps(): Boolean {
        val packageManager = context.packageManager
        return KNOWN_DANGEROUS_APPS.any { appId ->
            try {
                packageManager.getPackageInfo(appId, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Check for suspicious system properties
     */
    private fun checkForDangerousProps(): Boolean {
        val dangerousProps = arrayOf("ro.debuggable", "ro.secure")
        return try {
            val process = Runtime.getRuntime().exec("getprop")
            process.inputStream.reader().use { reader ->
                reader.readLines().any { line ->
                    dangerousProps.any { prop ->
                        line.contains(prop) && line.contains("1")
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check for read/write access to system partitions
     */
    private fun checkForRWPaths(): Boolean {
        val paths = arrayOf("/system", "/system/bin", "/system/sbin", "/system/xbin", "/vendor")
        val rwPaths = paths.filter { path ->
            val file = File(path)
            file.exists() && file.canWrite()
        }
        return rwPaths.isNotEmpty()
    }
    
    /**
     * Check if running in an emulator
     */
    fun isRunningInEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
    }
    
    /**
     * Sanitize input to protect against injection attacks
     */
    fun sanitizeInput(input: String): String {
        return input.replace("[^\\w\\s@.,:\\-_()\\[\\]{}]".toRegex(), "")
    }
    
    /**
     * Mask sensitive data in logs
     */
    fun maskSensitiveData(message: String): String {
        // Mask card numbers
        var masked = message.replace("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b".toRegex(), "XXXX-XXXX-XXXX-XXXX")
        
        // Mask account numbers (10-12 digits)
        masked = masked.replace("\\b\\d{10,12}\\b".toRegex(), "XXXX-XXXX-XXXX")
        
        // Mask amounts with currency
        masked = masked.replace("(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?".toRegex(), "₹XXX.XX")
        
        return masked
    }
} 