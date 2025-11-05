package com.vibeagent.dude

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class AccessibilityConsentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AccessibilityConsentManager"
        private const val SHARED_PREFS_NAME = "accessibility_consent_prefs"
        private const val CONSENT_GRANTED_KEY = "accessibility_consent_granted"
        private const val CONSENT_TIMESTAMP_KEY = "accessibility_consent_timestamp"
        private const val USER_EXPLICITLY_GRANTED_KEY = "user_explicitly_granted"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save user consent for accessibility service
     */
    fun saveAccessibilityConsent(granted: Boolean) {
        try {
            sharedPreferences.edit()
                .putBoolean(CONSENT_GRANTED_KEY, granted)
                .putLong(CONSENT_TIMESTAMP_KEY, System.currentTimeMillis())
                .putBoolean(USER_EXPLICITLY_GRANTED_KEY, granted)
                .apply()
            
            Log.d(TAG, "Accessibility consent saved: $granted")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving accessibility consent: ${e.message}", e)
        }
    }
    
    /**
     * Check if user has granted accessibility consent
     */
    fun hasAccessibilityConsent(): Boolean {
        return try {
            sharedPreferences.getBoolean(CONSENT_GRANTED_KEY, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility consent: ${e.message}", e)
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
            Log.e(TAG, "Error checking explicit consent: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the timestamp when consent was granted
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
     * Clear all consent data
     */
    fun clearConsent() {
        try {
            sharedPreferences.edit()
                .remove(CONSENT_GRANTED_KEY)
                .remove(CONSENT_TIMESTAMP_KEY)
                .remove(USER_EXPLICITLY_GRANTED_KEY)
                .apply()
            
            Log.d(TAG, "Accessibility consent cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing accessibility consent: ${e.message}", e)
        }
    }
    
    /**
     * Get consent information as a map
     */
    fun getConsentInfo(): Map<String, Any> {
        return mapOf(
            "hasConsent" to hasAccessibilityConsent(),
            "userExplicitlyGranted" to hasUserExplicitlyGrantedConsent(),
            "timestamp" to getConsentTimestamp()
        )
    }
}