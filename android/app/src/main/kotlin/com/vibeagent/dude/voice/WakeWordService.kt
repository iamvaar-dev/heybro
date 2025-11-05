package com.vibeagent.dude.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class WakeWordService : Service() {

    private val binder = WakeWordBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false
    private var wakeWordListener: WakeWordListener? = null
    private lateinit var keyManager: PorcupineKeyManager

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "WakeWordServiceChannel"
        var isRunning = false
        const val EXTRA_START_LISTENING = "start_listening"
    }

    interface WakeWordListener {
        fun onWakeWordDetected(keywordIndex: Int)
        fun onError(error: String)
        fun onApiFailure()
    }

    inner class WakeWordBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService created")
        isRunning = true
        keyManager = PorcupineKeyManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shouldStartListening = intent?.getBooleanExtra(EXTRA_START_LISTENING, true) ?: true
        
        if (shouldStartListening && !isListening) {
            initializePorcupine()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun initializePorcupine() {
        serviceScope.launch {
            try {
                val accessKey = keyManager.getAccessKey()
                if (accessKey == null) {
                    Log.w(TAG, "No Porcupine access key found")
                    wakeWordListener?.onError("Porcupine access key not configured")
                    wakeWordListener?.onApiFailure()
                    return@launch
                }

                // Copy hey-bro.ppn from assets if it doesn't exist
                val keywordPath = File(applicationContext.filesDir, "hey-bro.ppn")
                if (!keywordPath.exists()) {
                    copyAssetToFile("hey-bro.ppn", keywordPath)
                }

                if (!keywordPath.exists()) {
                    Log.e(TAG, "hey-bro.ppn model file not found")
                    wakeWordListener?.onError("Wake word model file not found")
                    wakeWordListener?.onApiFailure()
                    return@launch
                }

                startPorcupineWithKey(accessKey, keywordPath)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Porcupine: ${e.message}", e)
                wakeWordListener?.onError("Wake word service error: ${e.message}")
                wakeWordListener?.onApiFailure()
            }
        }
    }

    private suspend fun startPorcupineWithKey(accessKey: String, keywordPath: File) = withContext(Dispatchers.Main) {
        try {
            // Create the wake word callback
            val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
                Log.d(TAG, "Wake word 'Hey Droid' detected! Keyword index: $keywordIndex")
                wakeWordListener?.onWakeWordDetected(keywordIndex)
                // Start voice interaction service
                startVoiceInteraction()
                // PorcupineManager automatically continues listening after detection
            }

            // Create error callback
            val errorCallback = PorcupineManagerErrorCallback { error ->
                Log.e(TAG, "Porcupine error: ${error.message}")
                if (isListening) {
                    Log.d(TAG, "Porcupine error occurred, triggering API failure callback")
                    wakeWordListener?.onError("Porcupine error: ${error.message}")
                    wakeWordListener?.onApiFailure()
                }
            }

            // Build and start PorcupineManager
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath.absolutePath)
                .setSensitivity(0.5f) // Set sensitivity for better detection
                .setErrorCallback(errorCallback)
                .build(applicationContext, wakeWordCallback)

            porcupineManager?.start()
            isListening = true
            Log.d(TAG, "Porcupine wake word detection started successfully for 'Hey Droid'")
            
            // Update notification to show listening status
            updateNotification("Listening for 'Hey Droid'...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}", e)
            wakeWordListener?.onError("Failed to start wake word detection: ${e.message}")
            wakeWordListener?.onApiFailure()
        }
    }

    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening for wake word")
            return
        }
        
        if (porcupineManager == null) {
            Log.d(TAG, "Porcupine not initialized, initializing now")
            initializePorcupine()
        } else {
            serviceScope.launch {
                try {
                    porcupineManager?.start()
                    isListening = true
                    Log.d(TAG, "Started listening for wake word")
                    updateNotification("Listening for 'Hey Droid'...")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start listening: ${e.message}", e)
                    wakeWordListener?.onError("Failed to start listening: ${e.message}")
                }
            }
        }
    }

    fun stopListening() {
        if (!isListening) {
            Log.w(TAG, "Not currently listening for wake word")
            return
        }
        
        serviceScope.launch {
            try {
                porcupineManager?.stop()
                isListening = false
                Log.d(TAG, "Stopped listening for wake word")
                updateNotification("Wake word detection stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop listening: ${e.message}", e)
            }
        }
    }

    fun setWakeWordListener(listener: WakeWordListener?) {
        this.wakeWordListener = listener
    }

    fun isCurrentlyListening(): Boolean {
        return isListening
    }

    private fun cleanup() {
        try {
            stopListening()
            porcupineManager?.delete()
            porcupineManager = null
            isListening = false
            Log.d(TAG, "Wake word service cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WakeWordService destroyed")
        cleanup()
        isRunning = false
    }

    fun getAvailableKeywords(): Array<String> {
        return arrayOf("hey droid")
    }

    fun updateSensitivity(sensitivity: Float) {
        if (sensitivity < 0.0f || sensitivity > 1.0f) {
            Log.w(TAG, "Invalid sensitivity value: $sensitivity. Must be between 0.0 and 1.0")
            return
        }
        Log.d(TAG, "Sensitivity update requested: $sensitivity (requires restart)")
        // Note: Sensitivity changes require recreating PorcupineManager
        // This would need to be implemented if dynamic sensitivity is needed
    }

    suspend fun setAccessKey(accessKey: String): Boolean {
        return if (keyManager.saveAccessKey(accessKey)) {
            Log.d(TAG, "Access key updated successfully")
            // Restart Porcupine with new key
            cleanup()
            initializePorcupine()
            true
        } else {
            Log.e(TAG, "Failed to save access key")
            false
        }
    }

    suspend fun testAccessKey(accessKey: String): Boolean {
        return try {
            val keywordPath = File(filesDir, "hey-bro.ppn")
            if (!keywordPath.exists()) {
                copyAssetToFile("hey-bro.ppn", keywordPath)
            }
            
            // Test by creating a temporary PorcupineManager
            val testCallback = PorcupineManagerCallback { }
            val testManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath.absolutePath)
                .build(this, testCallback)
            
            testManager.delete()
            Log.d(TAG, "Access key test successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Access key test failed: ${e.message}")
            false
        }
    }

    private fun copyAssetToFile(assetName: String, targetFile: File) {
        try {
            assets.open(assetName).use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Copied asset $assetName to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset $assetName: ${e.message}", e)
            throw e
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice agent wake word detection"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String = "Listening for wake word"): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Voice Agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startVoiceInteraction() {
        try {
            val voiceIntent = Intent(this, VoiceAgentService::class.java).apply {
                action = VoiceAgentService.ACTION_START_VOICE_INTERACTION
                putExtra(VoiceAgentService.EXTRA_WAKE_WORD_DETECTED, true)
            }
            startService(voiceIntent)
            Log.d(TAG, "Started VoiceAgentService for voice interaction")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice interaction: ${e.message}", e)
        }
    }
}