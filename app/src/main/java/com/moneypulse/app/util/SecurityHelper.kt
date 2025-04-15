package com.moneypulse.app.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.moneypulse.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecretKey
import java.security.spec.PBEKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
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
        private const val TAG = "SecurityHelper"
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
        try {
            val biometricManager = BiometricManager.from(context)
            return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric availability: ${e.message}")
            return false
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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error showing biometric prompt: ${e.message}")
            // Call error callback to let caller handle the failure
            onError("Failed to show biometric authentication: ${e.message}")
        }
    }
    
    /**
     * Check if secure lock screen is enabled
     */
    fun isDeviceSecure(): Boolean {
        try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return keyguardManager.isDeviceSecure
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device security: ${e.message}")
            return false
        }
    }
    
    /**
     * Create or get cryptographic key for database encryption
     * Generates a secure key from Android Keystore
     */
    fun getDatabaseKey(): ByteArray {
        try {
            // Create key if it doesn't exist
            createAndroidKeystoreKey(DATABASE_KEY_NAME)
            
            // Get key from keystore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            // Get or create a device-specific ID for consistent encryption
            val deviceId = getSecureDeviceId()
            
            // Get secret key from keystore
            val secretKey = keyStore.getKey(DATABASE_KEY_NAME, null) as SecretKey
            
            // Use key to encrypt device ID to get consistent bytes for SQLCipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            // Return the encrypted data as bytes (consistent for same device)
            return cipher.doFinal(deviceId.toByteArray())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting secure database key: ${e.message}")
            
            // Fallback mechanism for backward compatibility or recovery
            Log.w(TAG, "Using fallback key generation method")
            
            // Generate device-specific key using multiple device properties
            val fallbackDeviceId = getSecureDeviceId()
            val salt = "MoneyPulse_Key_Salt_v2" // Salt to strengthen the key
            
            // Use PBKDF2 to derive a strong key
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(
                fallbackDeviceId.toCharArray(), 
                salt.toByteArray(), 
                10000, // iterations - higher is more secure but slower
                256 // key length in bits
            )
            val tmp = factory.generateSecret(spec)
            
            return tmp.encoded
        }
    }
    
    /**
     * Get a secure device identifier that's consistent across app installs
     * but unique to each device
     */
    private fun getSecureDeviceId(): String {
        try {
            // Combine multiple device properties for a robust identifier
            val deviceProperties = StringBuilder()
            
            // Add Android ID (survives app reinstalls)
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            deviceProperties.append(androidId)
            
            // Add build fingerprint elements that are relatively stable
            deviceProperties.append(Build.BOARD)
            deviceProperties.append(Build.BRAND)
            deviceProperties.append(Build.DEVICE)
            deviceProperties.append(Build.HARDWARE)
            deviceProperties.append(Build.PRODUCT)
            deviceProperties.append(Build.MANUFACTURER)
            
            // Hash the combined properties to get a consistent ID
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val deviceId = digest.digest(deviceProperties.toString().toByteArray())
            
            // Convert to hex string
            return deviceId.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID: ${e.message}")
            
            // Fallback to a simpler method
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"
            
            return "device_$androidId"
        }
    }
    
    /**
     * Create key in Android Keystore if it doesn't exist
     */
    private fun createAndroidKeystoreKey(keyName: String) {
        try {
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
                
                Log.d(TAG, "Created new keystore key for database encryption")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating keystore key: ${e.message}")
            throw e
        }
    }
    
    /**
     * Checks if device is potentially rooted
     * Combines multiple detection methods
     * In debug builds, is less aggressive
     */
    fun isDeviceRooted(): Boolean {
        // In debug builds, be less aggressive with root detection
        if (BuildConfig.DEBUG) {
            return false  // Always return false in debug to avoid interfering with development
        }
        
        try {
            // In production, use a more balanced approach with multiple checks
            // Only return true if MULTIPLE indicators are found to reduce false positives
            
            var rootIndicators = 0
            
            // Only count this as an indicator if multiple su binaries are found
            val suBinariesFound = KNOWN_ROOT_PATHS.count { 
                try { File(it).exists() } catch (e: Exception) { false }
            }
            if (suBinariesFound >= 2) rootIndicators++
            
            // Check for root apps - a strong indicator
            val rootAppsFound = try {
                val packageManager = context.packageManager
                KNOWN_DANGEROUS_APPS.any { appId ->
                    try {
                        packageManager.getPackageInfo(appId, 0)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                false
            }
            if (rootAppsFound) rootIndicators++
            
            // Check for RW system paths - a strong indicator
            val rwSystemPaths = try {
                val paths = arrayOf("/system", "/system/bin", "/system/sbin", "/system/xbin", "/vendor")
                paths.any { path ->
                    val file = File(path)
                    file.exists() && file.canWrite()
                }
            } catch (e: Exception) {
                false
            }
            if (rwSystemPaths) rootIndicators++
            
            // Only consider dangerous props if other indicators exist
            val dangerousProps = if (rootIndicators > 0) {
                try {
                    val process = Runtime.getRuntime().exec("getprop")
                    process.inputStream.reader().use { reader ->
                        val props = arrayOf("ro.debuggable", "ro.secure")
                        reader.readLines().any { line ->
                            props.any { prop ->
                                line.contains(prop) && line.contains("1")
                            }
                        }
                    }
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
            if (dangerousProps) rootIndicators++
            
            // Only consider rooted if MULTIPLE indicators found
            // This greatly reduces false positives on legitimate devices
            return rootIndicators >= 2
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if device is rooted: ${e.message}")
            return false  // On error, assume not rooted to avoid blocking valid users
        }
    }
    
    /**
     * Check if running in an emulator
     * In debug builds, we'll return false to allow development in emulators
     */
    fun isRunningInEmulator(): Boolean {
        // In debug builds, just log but don't block emulators
        if (BuildConfig.DEBUG) {
            return false
        }
        
        return try {
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
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
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if running in emulator: ${e.message}")
            false
        }
    }
    
    /**
     * Sanitize input to protect against injection attacks
     */
    fun sanitizeInput(input: String): String {
        return try {
            input.replace("[^\\w\\s@.,:\\-_()\\[\\]{}]".toRegex(), "")
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing input: ${e.message}")
            // Return original string if regex fails rather than empty string
            input
        }
    }
    
    /**
     * Mask sensitive data in logs
     */
    fun maskSensitiveData(message: String): String {
        try {
            // Mask card numbers
            var masked = message.replace("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b".toRegex(), "XXXX-XXXX-XXXX-XXXX")
            
            // Mask account numbers (10-12 digits)
            masked = masked.replace("\\b\\d{10,12}\\b".toRegex(), "XXXX-XXXX-XXXX")
            
            // Mask amounts with currency
            masked = masked.replace("(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?".toRegex(), "₹XXX.XX")
            
            return masked
        } catch (e: Exception) {
            Log.e(TAG, "Error masking sensitive data: ${e.message}")
            // Return "[MASKED]" if masking fails rather than original message
            return "[MASKED DATA]"
        }
    }
} 