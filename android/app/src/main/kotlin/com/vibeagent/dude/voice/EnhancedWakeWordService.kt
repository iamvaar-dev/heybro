package com.vibeagent.dude.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import com.vibeagent.dude.FloatingOverlayService
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.Manifest

class EnhancedWakeWordService : Service() {

    private val binder = WakeWordBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var porcupineManager: PorcupineManager? = null
    private var sttService: SpeechToTextService? = null
    private var sttServiceBound = false
    private var isListening = false
    private var wakeWordListener: WakeWordListener? = null
    private lateinit var keyManager: PorcupineKeyManager
    private var currentEngine = WakeWordEngine.PORCUPINE
    private var fallbackAttempts = 0
    private val maxFallbackAttempts = 3
    private val porcupineMutex = Mutex()
    private var isCleaningUp = false
    private var isPaused = false
    private var wasListeningBeforePause = false
    private var pauseResumeReceiver: BroadcastReceiver? = null
    
    private val sttServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SpeechToTextService.STTBinder
            sttService = binder.getService()
            sttServiceBound = true
            Log.d(TAG, "STT service connected and bound")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            sttService = null
            sttServiceBound = false
            Log.d(TAG, "STT service disconnected")
        }
    }



    enum class WakeWordEngine {
        PORCUPINE,
        STT_FALLBACK,
        FLOATING_BUTTON_EMERGENCY
    }

    interface WakeWordListener {
        fun onWakeWordDetected(keywordIndex: Int)
        fun onError(error: String)
        fun onApiFailure()
    }

    inner class WakeWordBinder : Binder() {
        fun getService(): EnhancedWakeWordService = this@EnhancedWakeWordService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EnhancedWakeWordService created with dual engine support")
        
        // Check if another instance is already running
        if (INSTANCE != null && INSTANCE != this) {
            Log.w(TAG, "Another EnhancedWakeWordService instance is already running, stopping this one")
            stopSelf()
            return
        }
        
        // Check for required permissions before starting foreground service
        if (!hasAudioPermissions()) {
            Log.e(TAG, "Audio permissions not granted, stopping service")
            stopSelf()
            return
        }
        
        INSTANCE = this
        isRunning = true
        keyManager = PorcupineKeyManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize STT service for fallback
        initializeSTTFallback()
        
        // Setup pause/resume broadcast receiver
        setupPauseResumeReceiver()
        
        // Start listening immediately with available engine
        startWakeWordDetection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "EnhancedWakeWordService onStartCommand")
        
        // Check audio permissions before proceeding
        if (!hasAudioPermissions()) {
            Log.e(TAG, "Audio permissions not granted, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        val shouldStartListening = intent?.getBooleanExtra(EXTRA_START_LISTENING, true) ?: true
        
        // Ensure service starts listening even when called independently
        if (shouldStartListening && !isListening) {
            // Try Porcupine first, fallback to STT if it fails
            serviceScope.launch {
                try {
                    initializePorcupine()
                } catch (e: Exception) {
                    Log.w(TAG, "Porcupine initialization failed, falling back to STT: ${e.message}")
                    switchToSTTFallback()
                }
            }
        }
        
        // Make service persistent - it will restart if killed by system
        return START_STICKY
    }
    
    companion object {
        private const val TAG = "EnhancedWakeWordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "WakeWordServiceChannel"
        var isRunning = false
        const val EXTRA_START_LISTENING = "start_listening"
        
        @Volatile
        private var INSTANCE: EnhancedWakeWordService? = null
        
        fun getInstance(): EnhancedWakeWordService? = INSTANCE
        
        fun isServiceRunning(): Boolean = INSTANCE != null
        
        /**
          * Starts the EnhancedWakeWordService independently, even when the main app is closed.
          * This method can be called from any context to ensure wake word detection is active.
          */
         fun startIndependentService(context: Context) {
             val intent = Intent(context, EnhancedWakeWordService::class.java)
             ContextCompat.startForegroundService(context, intent)
             Log.d(TAG, "EnhancedWakeWordService started independently")
         }
         
         fun setAutoStartEnabled(context: Context, enabled: Boolean) {
             val prefs = context.getSharedPreferences("wake_word_prefs", Context.MODE_PRIVATE)
             prefs.edit().putBoolean("auto_start_enabled", enabled).apply()
             Log.d(TAG, "Auto-start ${if (enabled) "enabled" else "disabled"}")
         }
         
         fun isAutoStartEnabled(context: Context): Boolean {
             val prefs = context.getSharedPreferences("wake_word_prefs", Context.MODE_PRIVATE)
             return prefs.getBoolean("auto_start_enabled", true) // Default to true
         }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun initializePorcupine() {
        serviceScope.launch {
            try {
                val accessKey = keyManager.getAccessKey()
                if (accessKey == null) {
                    Log.w(TAG, "No Porcupine access key found, falling back to STT")
                    handlePorcupineFailure("No access key available")
                    return@launch
                }

                // Copy hey-bro.ppn from assets if it doesn't exist
                val keywordPath = File(applicationContext.filesDir, "hey-bro.ppn")
                if (!keywordPath.exists()) {
                    try {
                        copyAssetToFile("hey-bro.ppn", keywordPath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy keyword model file: ${e.message}")
                        handlePorcupineFailure("Model file copy failed: ${e.message}")
                        return@launch
                    }
                }

                if (!keywordPath.exists()) {
                    Log.w(TAG, "hey-bro.ppn model file not found, falling back to STT")
                    handlePorcupineFailure("Model file not available")
                    return@launch
                }

                startPorcupineWithKey(accessKey, keywordPath)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Porcupine: ${e.message}", e)
                handlePorcupineFailure("Initialization failed: ${e.message}")
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
                
                // Force reset after 10 seconds to prevent getting stuck in listening state
                serviceScope.launch {
                    delay(10000) // 10 second timeout
                    if (isListening) {
                        Log.d(TAG, "Forcing wake word service reset after timeout")
                        resetWakeWordService()
                    }
                }
            }

            // Create error callback with fallback logic
            val errorCallback = PorcupineManagerErrorCallback { error ->
                serviceScope.launch {
                    porcupineMutex.withLock {
                        if (isCleaningUp || porcupineManager == null) {
                            Log.d(TAG, "Ignoring Porcupine error during cleanup: ${error.message}")
                            return@withLock
                        }
                        
                        Log.e(TAG, "Porcupine error: ${error.message}")
                        if (isListening) {
                            Log.d(TAG, "Porcupine error occurred, attempting STT fallback")
                            fallbackAttempts++
                            if (fallbackAttempts <= maxFallbackAttempts) {
                                switchToSTTFallback()
                            } else {
                                Log.e(TAG, "Max fallback attempts reached, starting emergency floating button")
                                startEmergencyFloatingButton()
                            }
                            wakeWordListener?.onError("Porcupine error: ${error.message}")
                            wakeWordListener?.onApiFailure()
                        }
                    }
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
            
            // Update notification to show listening status with engine info
            updateNotification("Listening for 'Hey Droid' with Porcupine engine")
            
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
        
        // Check audio permissions first
        if (!hasAudioPermissions()) {
            Log.e(TAG, "Audio permissions not granted")
            wakeWordListener?.onError("Audio permissions required")
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
            porcupineMutex.withLock {
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
    }

    fun setWakeWordListener(listener: WakeWordListener?) {
        this.wakeWordListener = listener
    }

    fun isCurrentlyListening(): Boolean {
        return isListening
    }

    private fun cleanup() {
        serviceScope.launch {
            porcupineMutex.withLock {
                try {
                    isCleaningUp = true
                    Log.d(TAG, "Starting cleanup process")
                    
                    if (isListening) {
                        porcupineManager?.stop()
                        sttService?.stopWakeWordListening()
                        isListening = false
                    }
                    
                    porcupineManager?.delete()
                    porcupineManager = null
                    
                    // Unbind STT service
                    if (sttServiceBound) {
                        try {
                            unbindService(sttServiceConnection)
                            sttServiceBound = false
                            sttService = null
                        } catch (e: Exception) {
                            Log.w(TAG, "Error unbinding STT service: ${e.message}")
                        }
                    }
                    
                    Log.d(TAG, "Wake word service cleaned up")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during cleanup: ${e.message}", e)
                } finally {
                    isCleaningUp = false
                }
            }
        }
    }
    
    /**
     * Resets the wake word service by stopping and restarting it
     * This helps prevent the service from getting stuck in listening state
     */
    private fun resetWakeWordService() {
        serviceScope.launch {
            porcupineMutex.withLock {
                try {
                    Log.d(TAG, "Resetting wake word service")
                    
                    // Stop current listening
                    if (isListening) {
                        porcupineManager?.stop()
                        isListening = false
                    }
                    
                    // Short delay before restarting
                    delay(500)
                    
                    // Restart listening
                    porcupineManager?.start()
                    isListening = true
                    
                    Log.d(TAG, "Wake word service reset complete")
                    updateNotification("Listening for 'Hey Droid' with Porcupine engine")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during wake word service reset: ${e.message}", e)
                    // Try to reinitialize completely if reset fails
                    porcupineManager?.delete()
                    porcupineManager = null
                    initializePorcupine()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WakeWordService destroyed")
        
        // Unregister pause/resume receiver
        try {
            pauseResumeReceiver?.let {
                unregisterReceiver(it)
                pauseResumeReceiver = null
                Log.d(TAG, "Pause/resume broadcast receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister pause/resume receiver: ${e.message}", e)
        }
        
        cleanup()
        INSTANCE = null
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
            porcupineMutex.withLock {
                isCleaningUp = true
                try {
                    if (isListening) {
                        porcupineManager?.stop()
                        isListening = false
                    }
                    porcupineManager?.delete()
                    porcupineManager = null
                } finally {
                    isCleaningUp = false
                }
            }
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
                description = "Voice agent wake word detection - All processing done on-device for privacy"
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String = "Listening for wake word"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heybro Voice Assistant")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val privacyText = "$text\n\n${getPrivacyComplianceMessage()}"
        val notification = createNotification(privacyText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startVoiceInteraction() {
        try {
            // For Android 14+, we need to bring the app to foreground first
            // to meet the "eligible state" requirement for microphone foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Start MainActivity to bring app to foreground
                val mainActivityIntent = Intent(this, com.vibeagent.dude.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("start_voice_interaction", true)
                }
                startActivity(mainActivityIntent)
                
                // Small delay to ensure MainActivity is in foreground
                serviceScope.launch {
                    delay(500)
                    startVoiceAgentService()
                }
            } else {
                // For older Android versions, start service directly
                startVoiceAgentService()
            }
            
            Log.d(TAG, "Voice interaction initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice interaction: ${e.message}", e)
        }
    }
    
    private fun startVoiceAgentService() {
        try {
            val voiceIntent = Intent(this, VoiceAgentService::class.java).apply {
                action = VoiceAgentService.ACTION_START_VOICE_INTERACTION
                putExtra(VoiceAgentService.EXTRA_WAKE_WORD_DETECTED, true)
            }
            startService(voiceIntent)
            Log.d(TAG, "Started VoiceAgentService for voice interaction")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VoiceAgentService: ${e.message}", e)
        }
    }
    
    /**
     * Initializes the STT service for fallback wake word detection
     */
    private fun initializeSTTFallback() {
        try {
            // Bind to STT service
            val sttIntent = Intent(this, SpeechToTextService::class.java)
            bindService(sttIntent, sttServiceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "STT fallback service binding initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize STT fallback: ${e.message}", e)
        }
    }

    /**
     * Start wake word detection with the best available engine
     * Tries Porcupine first, falls back to STT if Porcupine is not available
     */
    private fun startWakeWordDetection() {
        serviceScope.launch {
            if (isListening) {
                Log.d(TAG, "Already listening for wake words")
                return@launch
            }

            Log.d(TAG, "Starting wake word detection with automatic engine selection")
            
            // Try Porcupine first
            try {
                val accessKey = keyManager.getAccessKey()
                if (!accessKey.isNullOrEmpty()) {
                    val keywordPath = File(applicationContext.filesDir, "hey-bro.ppn")
                    if (!keywordPath.exists()) {
                        copyAssetToFile("hey-bro.ppn", keywordPath)
                    }
                    if (keywordPath.exists()) {
                        Log.d(TAG, "Porcupine available, using Porcupine engine")
                        currentEngine = WakeWordEngine.PORCUPINE
                        initializePorcupine()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Porcupine not available: ${e.message}")
            }
            
            // Fallback to STT
            Log.d(TAG, "Porcupine not available, using STT fallback engine")
            switchToSTTFallback()
        }
    }
    
    /**
     * Returns privacy policy compliance message for on-device processing
     */
    private fun getPrivacyComplianceMessage(): String {
        return "Privacy: This processing is done entirely on your device by the Picovoice Porcupine engine; your ambient audio is not sent to the cloud for wake word detection"
    }
    
    /**
     * Handles Porcupine initialization failures with retry logic and fallback
     */
    private fun handlePorcupineFailure(reason: String) {
        serviceScope.launch {
            try {
                Log.w(TAG, "Porcupine failure: $reason")
                fallbackAttempts++
                
                if (fallbackAttempts <= maxFallbackAttempts) {
                    Log.d(TAG, "Attempting Porcupine retry ${fallbackAttempts}/${maxFallbackAttempts}")
                    
                    // Wait before retry with exponential backoff
                    val retryDelay = 1000L * fallbackAttempts
                    delay(retryDelay)
                    
                    // Clean up any existing Porcupine instance
                    porcupineMutex.withLock {
                        porcupineManager?.delete()
                        porcupineManager = null
                        isListening = false
                    }
                    
                    // Retry initialization
                    initializePorcupine()
                } else {
                    Log.w(TAG, "Max Porcupine retry attempts reached, switching to STT fallback")
                    switchToSTTFallback()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in Porcupine failure handler: ${e.message}", e)
                switchToSTTFallback()
            }
        }
    }
    
    private fun switchToSTTFallback() {
        serviceScope.launch {
            try {
                // Stop Porcupine if running
                porcupineManager?.stop()
                currentEngine = WakeWordEngine.STT_FALLBACK
                
                Log.d(TAG, "Switching to STT fallback engine")
                updateNotification("Listening for 'Hey Droid' with STT fallback engine")
                
                // Start STT-based wake word detection
                startSTTWakeWordDetection()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch to STT fallback: ${e.message}", e)
                startEmergencyFloatingButton()
            }
        }
    }
    
    private fun startSTTWakeWordDetection() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting STT wake word detection for 'Hey Droid'")
                
                // Initialize STT service if not already done
                if (!sttServiceBound) {
                    initializeSTTFallback()
                    // Wait for service to bind with timeout
                    var retries = 0
                    while (!sttServiceBound && retries < 10) {
                        delay(500)
                        retries++
                    }
                    
                    if (!sttServiceBound) {
                        Log.e(TAG, "STT service failed to bind after retries")
                        handleSTTFailure("Service binding failed")
                        return@launch
                    }
                }
                
                // Verify STT service is available
                if (sttService == null) {
                    Log.e(TAG, "STT service is null after binding")
                    handleSTTFailure("Service unavailable")
                    return@launch
                }
                
                // Start single-shot STT listening for wake word
                sttService?.startWakeWordListening { recognizedText ->
                    serviceScope.launch {
                        Log.d(TAG, "STT recognized: $recognizedText")
                        
                        // Wake word detected - trigger voice interaction
                        Log.d(TAG, "Wake word detected via STT: $recognizedText")
                        wakeWordListener?.onWakeWordDetected(0)
                        startVoiceInteraction()
                        
                        // Stop listening after detection - no auto-restart
                        isListening = false
                        updateNotification("Wake word detected - processing...")
                        
                        // Note: Manual restart will be handled by external trigger
                        // No automatic restart to prevent continuous triggering
                    }
                }
                
                isListening = true
                updateNotification("Listening for 'Hey Droid' with STT engine")
                Log.d(TAG, "STT wake word detection started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start STT wake word detection: ${e.message}", e)
                handleSTTFailure("Initialization failed: ${e.message}")
            }
        }
    }
    
    /**
     * Handles STT service failures with fallback to emergency mode
     */
    private fun handleSTTFailure(reason: String) {
        serviceScope.launch {
            try {
                Log.e(TAG, "STT failure: $reason")
                
                // Clean up STT service
                if (sttServiceBound) {
                    try {
                        unbindService(sttServiceConnection)
                        sttServiceBound = false
                        sttService = null
                    } catch (e: Exception) {
                        Log.w(TAG, "Error unbinding STT service: ${e.message}")
                    }
                }
                
                // Try to reinitialize STT once more
                if (fallbackAttempts < maxFallbackAttempts) {
                    fallbackAttempts++
                    Log.d(TAG, "Attempting STT recovery ${fallbackAttempts}/${maxFallbackAttempts}")
                    
                    delay(2000) // Wait before retry
                    initializeSTTFallback()
                    
                    // Wait for binding and retry
                    delay(1000)
                    if (sttServiceBound) {
                        startSTTWakeWordDetection()
                        return@launch
                    }
                }
                
                // If all else fails, start emergency floating button
                Log.e(TAG, "All wake word detection methods failed, starting emergency mode")
                startEmergencyFloatingButton()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in STT failure handler: ${e.message}", e)
                startEmergencyFloatingButton()
            }
        }
    }
    
    private fun restartSTTWakeWordDetection() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Restarting STT wake word detection")
                
                // Ensure we're still in STT fallback mode and should be listening
                if (currentEngine != WakeWordEngine.STT_FALLBACK || !isListening) {
                    Log.d(TAG, "Not restarting - engine changed or stopped listening")
                    return@launch
                }
                
                // Small delay to ensure previous session is fully closed
                delay(500)
                
                // Restart STT listening - single shot only
                sttService?.startWakeWordListening { recognizedText ->
                    serviceScope.launch {
                        Log.d(TAG, "STT recognized (restart): $recognizedText")
                        
                        // Wake word detected - trigger voice interaction
                        Log.d(TAG, "Wake word detected via STT (restart): $recognizedText")
                        wakeWordListener?.onWakeWordDetected(0)
                        startVoiceInteraction()
                        
                        // Stop listening after detection - no auto-restart
                        isListening = false
                        updateNotification("Wake word detected - processing...")
                        
                        // No automatic restart to prevent continuous triggering
                    }
                }
                
                Log.d(TAG, "STT wake word detection restarted successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart STT wake word detection: ${e.message}", e)
                handleSTTFailure("Restart failed: ${e.message}")
            }
        }
    }
    
    private fun startEmergencyFloatingButton() {
        try {
            currentEngine = WakeWordEngine.FLOATING_BUTTON_EMERGENCY
            Log.d(TAG, "Starting emergency floating button service")
            updateNotification("Wake word engines failed - using manual floating button")
            
            // Start floating button service as emergency fallback
            val floatingIntent = Intent(this, FloatingOverlayService::class.java)
            startService(floatingIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency floating button: ${e.message}", e)
        }
    }
    
    private fun hasAudioPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Restarts STT listening after wake word detection with proper safeguards
     */
    private fun restartSTTListeningAfterDetection() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Restarting STT listening after wake word detection")
                
                // Check if we should still be listening
                if (currentEngine != WakeWordEngine.STT_FALLBACK) {
                    Log.d(TAG, "Engine changed, not restarting STT")
                    return@launch
                }
                
                // Check audio permissions
                if (!hasAudioPermissions()) {
                    Log.e(TAG, "Audio permissions lost, cannot restart STT")
                    return@launch
                }
                
                // Ensure STT service is still bound
                if (!sttServiceBound || sttService == null) {
                    Log.w(TAG, "STT service not available, reinitializing")
                    initializeSTTFallback()
                    delay(1000) // Wait for binding
                }
                
                // Start fresh STT listening session - single shot only
                sttService?.startWakeWordListening { recognizedText ->
                    serviceScope.launch {
                        Log.d(TAG, "STT recognized (after restart): $recognizedText")
                        
                        // Wake word detected - trigger voice interaction
                        Log.d(TAG, "Wake word detected via STT (after restart): $recognizedText")
                        wakeWordListener?.onWakeWordDetected(0)
                        startVoiceInteraction()
                        
                        // Stop listening after detection - no auto-restart
                        isListening = false
                        updateNotification("Wake word detected - processing...")
                        
                        // No automatic restart to prevent continuous triggering
                    }
                }
                
                isListening = true
                updateNotification("Listening for 'Hey Droid' with STT engine")
                Log.d(TAG, "STT listening restarted successfully after detection")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart STT listening after detection: ${e.message}", e)
                handleSTTFailure("Restart after detection failed: ${e.message}")
            }
        }
    }
    
    /**
     * Setup broadcast receiver for pause/resume commands from VoiceAgentService
     */
    private fun setupPauseResumeReceiver() {
        pauseResumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.vibeagent.dude.PAUSE_WAKE_WORD" -> {
                        Log.d(TAG, "Received pause wake word broadcast")
                        pauseWakeWordDetection()
                    }
                    "com.vibeagent.dude.RESUME_WAKE_WORD" -> {
                        Log.d(TAG, "Received resume wake word broadcast")
                        resumeWakeWordDetection()
                    }
                    "com.vibeagent.dude.RESTART_WAKE_WORD" -> {
                        Log.d(TAG, "Received manual restart wake word broadcast")
                        restartWakeWordDetection()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction("com.vibeagent.dude.PAUSE_WAKE_WORD")
            addAction("com.vibeagent.dude.RESUME_WAKE_WORD")
            addAction("com.vibeagent.dude.RESTART_WAKE_WORD")
        }
        
        try {
            registerReceiver(pauseResumeReceiver, filter)
            Log.d(TAG, "Pause/resume/restart broadcast receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register pause/resume/restart receiver: ${e.message}", e)
        }
    }
    
    /**
     * Pause wake word detection during automation tasks
     */
    private fun pauseWakeWordDetection() {
        if (isPaused) {
            Log.d(TAG, "Wake word detection already paused")
            return
        }
        
        wasListeningBeforePause = isListening
        isPaused = true
        
        if (isListening) {
            stopListening()
            updateNotification("Paused during automation task")
            Log.d(TAG, "Wake word detection paused for automation task")
        }
    }
    
    /**
     * Resume wake word detection after automation completes
     */
    private fun resumeWakeWordDetection() {
        if (!isPaused) {
            Log.d(TAG, "Wake word detection not paused")
            return
        }
        
        isPaused = false
        
        if (wasListeningBeforePause && !isListening) {
            serviceScope.launch {
                delay(1000) // Small delay to ensure automation cleanup is complete
                startWakeWordDetection()
                Log.d(TAG, "Wake word detection resumed after automation task")
            }
        }
        
        wasListeningBeforePause = false
    }
    
    /**
     * Manually restart wake word detection after voice interaction completes
     * This replaces the automatic restart to prevent continuous triggering
     */
    fun restartWakeWordDetection() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Manually restarting wake word detection")
                
                // Check if we should restart
                if (isPaused) {
                    Log.d(TAG, "Service is paused, not restarting")
                    return@launch
                }
                
                // Check audio permissions
                if (!hasAudioPermissions()) {
                    Log.e(TAG, "Audio permissions lost, cannot restart")
                    return@launch
                }
                
                // Small delay to ensure previous session is fully closed
                delay(1000)
                
                // Start fresh wake word detection
                startWakeWordDetection()
                
                Log.d(TAG, "Wake word detection restarted successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart wake word detection: ${e.message}", e)
                handleSTTFailure("Manual restart failed: ${e.message}")
            }
        }
    }
    
    /**
     * Override startListening to respect pause state
     */
    private fun startListeningIfNotPaused() {
        if (!isPaused) {
            startListening()
        } else {
            Log.d(TAG, "Skipping start listening - service is paused")
        }
    }
}