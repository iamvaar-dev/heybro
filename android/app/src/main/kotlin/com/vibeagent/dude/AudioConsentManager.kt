package com.vibeagent.dude

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class AudioConsentManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioConsentManager"
        private const val PREFS_NAME = "audio_consent_prefs"
        private const val CONSENT_GRANTED_KEY = "audio_consent_granted"
        private const val CONSENT_TIMESTAMP_KEY = "audio_consent_timestamp"
        private const val USER_EXPLICITLY_GRANTED_KEY = "user_explicitly_granted_audio"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save user consent for audio permissions
     */
    fun saveAudioConsent(granted: Boolean) {
        try {
            sharedPreferences.edit()
                .putBoolean(CONSENT_GRANTED_KEY, granted)
                .putLong(CONSENT_TIMESTAMP_KEY, System.currentTimeMillis())
                .putBoolean(USER_EXPLICITLY_GRANTED_KEY, granted)
                .apply()
            
            Log.d(TAG, "Audio consent saved: $granted")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio consent: ${e.message}", e)
        }
    }
    
    /**
     * Check if user has granted audio consent
     */
    fun hasAudioConsent(): Boolean {
        return try {
            sharedPreferences.getBoolean(CONSENT_GRANTED_KEY, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking audio consent: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if user explicitly granted consent (not just system enabled)
     */
    fun hasUserExplicitlyGrantedConsent(): Boolean {
        return try {
            sharedPreferences.getBoolean(USER_EXPLICITLY_GRANTED_KEY, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking explicit audio consent: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get timestamp when consent was granted
     */
    fun getConsentTimestamp(): Long {
        return try {
            sharedPreferences.getLong(CONSENT_TIMESTAMP_KEY, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting consent timestamp: ${e.message}", e)
            0L
        }
    }
    
    /**
     * Clear all audio consent data
     */
    fun clearAudioConsent() {
        try {
            sharedPreferences.edit()
                .remove(CONSENT_GRANTED_KEY)
                .remove(CONSENT_TIMESTAMP_KEY)
                .remove(USER_EXPLICITLY_GRANTED_KEY)
                .apply()
            
            Log.d(TAG, "Audio consent data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio consent: ${e.message}", e)
        }
    }
}