package com.moneypulse.app.util.security

import android.util.Log
import java.security.Key
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Helper for cryptography operations
 * Encapsulates potentially problematic or missing classes for compatibility
 */
object CryptoHelper {
    private const val TAG = "CryptoHelper"
    
    /**
     * Create a key from a password and salt using PBKDF2
     */
    fun createPBEKey(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): SecretKey {
        try {
            // Get the key factory for PBKDF2
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            
            // Create the PBEKeySpec
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            
            // Generate the secret key
            val secretKey = factory.generateSecret(spec)
            
            // Convert to an AES key which is more broadly compatible
            return SecretKeySpec(secretKey.encoded, "AES")
        } catch (e: NoSuchAlgorithmException) {
            android.util.Log.e(TAG, "Algorithm not available: ${e.message}")
            throw e
        } catch (e: InvalidKeySpecException) {
            android.util.Log.e(TAG, "Invalid key specification: ${e.message}")
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating PBE key: ${e.message}")
            throw e
        }
    }
} 