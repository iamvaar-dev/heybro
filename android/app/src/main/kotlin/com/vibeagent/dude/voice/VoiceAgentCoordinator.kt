package com.vibeagent.dude.voice

import android.content.Context
import android.util.Log
import com.vibeagent.dude.AppManagementService
import com.vibeagent.dude.AutomationService
// Removed VoiceOverlayManager import - overlay is handled by VoiceAgentService
import com.vibeagent.dude.voice.PorcupineKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceAgentCoordinator(private val context: Context) {

    private val coordinatorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Removed voiceOverlayManager - overlay is handled by VoiceAgentService
    private var appManagementService: AppManagementService? = null
    private var automationService: AutomationService? = null
    private var isInitialized = false
    private var voiceCommandProcessor: VoiceCommandProcessor? = null
    private var speechCoordinator: SpeechCoordinator? = null

    companion object {
        private const val TAG = "VoiceAgentCoordinator"
        
        @Volatile
        private var INSTANCE: VoiceAgentCoordinator? = null
        
        fun getInstance(context: Context): VoiceAgentCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceAgentCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun initialize(appManagementService: AppManagementService?, automationService: AutomationService?) {
        if (isInitialized) {
            Log.w(TAG, "VoiceAgentCoordinator already initialized")
            return
        }

        Log.d(TAG, "Initializing VoiceAgentCoordinator")
        
        this.appManagementService = appManagementService
        this.automationService = automationService
        
        // Initialize speech coordinator for TTS and STT
        speechCoordinator = SpeechCoordinator.getInstance(context)
        Log.d(TAG, "SpeechCoordinator initialized")
        
        // Initialize voice command processor
        voiceCommandProcessor = VoiceCommandProcessor(context, appManagementService, automationService)
        
        // Voice overlay is handled by VoiceAgentService, not here
        
        isInitialized = true
        Log.d(TAG, "VoiceAgentCoordinator initialized successfully")
    }

    private fun processVoiceCommand(command: String) {
        coordinatorScope.launch {
            try {
                voiceCommandProcessor?.processCommand(command)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice command: ${e.message}", e)
            }
        }
    }

    fun isListening(): Boolean {
        // Voice listening state is managed by VoiceAgentService
        return false
    }

    fun startListening() {
        if (!isInitialized) {
            Log.w(TAG, "VoiceAgentCoordinator not initialized")
            return
        }
        
        Log.d(TAG, "Starting voice listening")
        // Voice listening is automatically started by VoiceOverlayManager
    }

    fun stopListening() {
        // Voice listening is managed by VoiceAgentService
        Log.d(TAG, "Voice listening stopped")
    }

    fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up VoiceAgentCoordinator")
            
            // Voice overlay cleanup is handled by VoiceAgentService
            voiceCommandProcessor?.cleanup()
            speechCoordinator?.cleanup()
            
            // No voiceOverlayManager to clean up
            voiceCommandProcessor = null
            speechCoordinator = null
            appManagementService = null
            isInitialized = false
            
            Log.d(TAG, "VoiceAgentCoordinator cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    fun updateAppManagementService(appManagementService: AppManagementService?) {
        this.appManagementService = appManagementService
        voiceCommandProcessor?.updateAppManagementService(appManagementService)
        Log.d(TAG, "AppManagementService updated")
    }

    fun getStatus(): String {
        return when {
            !isInitialized -> "Not initialized"
            isListening() -> "Listening for voice commands"
            else -> "Ready"
        }
    }

    suspend fun setAccessKey(accessKey: String): Boolean {
        Log.d(TAG, "Setting Porcupine access key")
        return try {
            // Get the current EnhancedWakeWordService instance
            val wakeWordService = EnhancedWakeWordService.getInstance()
            if (wakeWordService != null) {
                wakeWordService.setAccessKey(accessKey)
            } else {
                // If service is not running, save the key directly using PorcupineKeyManager
                val keyManager = PorcupineKeyManager(context)
                keyManager.saveAccessKey(accessKey)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set access key: ${e.message}", e)
            false
        }
    }

    suspend fun testAccessKey(accessKey: String): Boolean {
        Log.d(TAG, "Testing Porcupine access key")
        return try {
            // Get the current EnhancedWakeWordService instance
            val wakeWordService = EnhancedWakeWordService.getInstance()
            if (wakeWordService != null) {
                wakeWordService.testAccessKey(accessKey)
            } else {
                // If service is not running, test the key format using PorcupineKeyManager
                val keyManager = PorcupineKeyManager(context)
                val isValidFormat = keyManager.isValidAccessKeyFormat(accessKey)
                Log.d(TAG, "Access key format validation: $isValidFormat")
                isValidFormat
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to test access key: ${e.message}", e)
            false
        }
    }
}