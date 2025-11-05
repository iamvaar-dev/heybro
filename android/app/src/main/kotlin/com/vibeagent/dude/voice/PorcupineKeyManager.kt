package com.vibeagent.dude.voice

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class PorcupineKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "PorcupineKeyManager"
        private const val KEYSTORE_ALIAS = "PorcupineKeyAlias"
        private const val SHARED_PREFS_NAME = "porcupine_secure_prefs"
        private const val ENCRYPTED_KEY_PREF = "encrypted_access_key"
        private const val IV_PREF = "encryption_iv"
        private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    }

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save the Porcupine access key securely
     */
    suspend fun saveAccessKey(accessKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedKey = cipher.doFinal(accessKey.toByteArray())
            val iv = cipher.iv
            
            val encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.DEFAULT)
            val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)
            
            sharedPreferences.edit()
                .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                .putString(IV_PREF, ivBase64)
                .apply()
            
            Log.d(TAG, "Porcupine access key saved securely")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving access key: ${e.message}", e)
            false
        }
    }

    /**
     * Retrieve the Porcupine access key securely
     */
    suspend fun getAccessKey(): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedKeyBase64 = sharedPreferences.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = sharedPreferences.getString(IV_PREF, null)
            
            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Log.d(TAG, "No encrypted access key found")
                return@withContext null
            }
            
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            
            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.DEFAULT)
            val decryptedKey = cipher.doFinal(encryptedKey)
            
            val accessKey = String(decryptedKey)
            Log.d(TAG, "Porcupine access key retrieved securely")
            accessKey
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving access key: ${e.message}", e)
            null
        }
    }

    /**
     * Check if an access key is stored
     */
    fun hasAccessKey(): Boolean {
        return sharedPreferences.contains(ENCRYPTED_KEY_PREF) && 
               sharedPreferences.contains(IV_PREF)
    }

    /**
     * Clear the stored access key
     */
    fun clearAccessKey() {
        sharedPreferences.edit()
            .remove(ENCRYPTED_KEY_PREF)
            .remove(IV_PREF)
            .apply()
        Log.d(TAG, "Porcupine access key cleared")
    }

    /**
     * Get or create a secret key in Android Keystore
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            // Key exists, retrieve it
            keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        } else {
            // Key doesn't exist, create it
            createSecretKey()
        }
    }

    /**
     * Create a new secret key in Android Keystore
     */
    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Validate if the provided access key format is correct
     */
    fun isValidAccessKeyFormat(accessKey: String): Boolean {
        // Porcupine access keys are typically 88 characters long and contain alphanumeric characters
        return accessKey.isNotBlank() && 
               accessKey.length >= 80 && 
               accessKey.matches(Regex("[A-Za-z0-9+/=]+"))
    }
}