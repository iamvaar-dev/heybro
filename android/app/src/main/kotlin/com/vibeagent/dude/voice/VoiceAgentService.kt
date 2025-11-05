package com.vibeagent.dude.voice

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import com.vibeagent.dude.AutomationForegroundService
import com.vibeagent.dude.voice.SpeechCoordinator
import com.vibeagent.dude.voice.overlay.VoiceOverlayManager
import com.vibeagent.dude.voice.SpeechToTextService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/**
 * VoiceAgentService handles voice interactions after wake word detection.
 * Inspired by the reference implementation but redesigned for our architecture.
 */
class VoiceAgentService : Service() {
    
    companion object {
        private const val TAG = "VoiceAgentService"
        const val ACTION_START_VOICE_INTERACTION = "com.vibeagent.dude.START_VOICE_INTERACTION"
        const val ACTION_STOP_VOICE_INTERACTION = "com.vibeagent.dude.STOP_VOICE_INTERACTION"
        const val EXTRA_WAKE_WORD_DETECTED = "wake_word_detected"
        
        private const val NOTIFICATION_CHANNEL_ID = "voice_agent_channel"
        private const val NOTIFICATION_ID = 2001
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var speechCoordinator: SpeechCoordinator
    private lateinit var overlayManager: VoiceOverlayManager
    private var isProcessingVoice = false
    private var automationCompleteReceiver: BroadcastReceiver? = null
    private var accumulatedTranscription = StringBuilder()
    private var lastPartialText = ""
    private var speechSegments = mutableListOf<String>()
    
    // Task queue management
    private val taskMutex = Mutex()
    private val taskQueue = mutableListOf<VoiceTask>()
    private var currentTask: VoiceTask? = null
    private var taskIdCounter = 0
    
    data class VoiceTask(
        val id: Int,
        val wakeWordDetected: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        var status: TaskStatus = TaskStatus.PENDING
    )
    
    enum class TaskStatus {
        PENDING, PROCESSING, COMPLETED, CANCELLED
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceAgentService created")
        
        // Check for required permissions before starting foreground service
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Required permissions not granted, stopping service")
            stopSelf()
            return
        }
        
        // Create notification channel and start as foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        speechCoordinator = SpeechCoordinator.getInstance(this)
        overlayManager = VoiceOverlayManager(this)
        
        // Register automation completion receivers
        registerAutomationReceivers()
        
        Log.d(TAG, "VoiceAgentService initialized successfully")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VOICE_INTERACTION -> {
                val wakeWordDetected = intent.getBooleanExtra(EXTRA_WAKE_WORD_DETECTED, false)
                serviceScope.launch {
                    enqueueVoiceTask(wakeWordDetected)
                }
            }
            ACTION_STOP_VOICE_INTERACTION -> {
                stopVoiceInteraction()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startVoiceInteraction(wakeWordDetected: Boolean, isNewSession: Boolean = true) {
        if (isProcessingVoice) {
            Log.w(TAG, "Already processing voice interaction")
            return
        }
        
        Log.d(TAG, "Starting voice interaction (wake word: $wakeWordDetected)")
        
        isProcessingVoice = true
        var showingTextInputFallback = false
        accumulatedTranscription.clear() // Clear any previous transcription
        lastPartialText = ""
        speechSegments.clear()
        
        serviceScope.launch {
            try {
                // Show voice overlay and start listening
                overlayManager.showVoiceOverlay()
                overlayManager.startAccumulatingTranscription()
                
                if (wakeWordDetected) {
                    // Provide audio feedback for wake word detection
                    speechCoordinator.speakText("Yes?")
                }
                
                // Start listening for voice command
                val speechResult = CompletableDeferred<String?>()
                
                val listener = object : SpeechToTextService.STTListener {
                    override fun onSpeechResult(text: String) {
                        Log.d(TAG, "Final speech result: $text")
                        
                        // Use the accumulated segments as the final task string
                        val finalTaskString = if (speechSegments.isNotEmpty()) {
                            speechSegments.joinToString(" ").trim()
                        } else if (accumulatedTranscription.isNotEmpty()) {
                            accumulatedTranscription.toString().trim()
                        } else {
                            text.trim()
                        }
                        
                        Log.d(TAG, "Final task string from segments: '$finalTaskString'")
                        overlayManager.finalizeAccumulatedTranscription(finalTaskString)
                        speechResult.complete(finalTaskString)
                    }
                    
                    override fun onSpeechError(error: String) {
                        Log.e(TAG, "Speech error: $error")
                        speechResult.complete(null)
                    }
                    
                    override fun onSpeechStarted() {
                        Log.d(TAG, "Speech started")
                        overlayManager.startAccumulatingTranscription(true)
                    }
                    
                    override fun onSpeechEnded() {
                        Log.d(TAG, "Speech ended")
                        // Don't complete here - wait for final result or timeout
                    }
                    
                    override fun onPartialResult(partialText: String) {
                        Log.d(TAG, "Partial result: $partialText")
                        
                        if (partialText.isNotEmpty()) {
                            val trimmedText = partialText.trim()
                            
                            if (lastPartialText.isEmpty()) {
                                // First partial result - start new segment
                                speechSegments.clear()
                                speechSegments.add(trimmedText)
                                Log.d(TAG, "First speech segment: '$trimmedText'")
                            } else if (trimmedText.startsWith(lastPartialText, ignoreCase = true)) {
                                // This is a refinement/extension of current speech
                                val newPart = trimmedText.substring(lastPartialText.length).trim()
                                if (newPart.isNotEmpty()) {
                                    // Replace the last segment with the extended version
                                    if (speechSegments.isNotEmpty()) {
                                        speechSegments[speechSegments.size - 1] = trimmedText
                                    } else {
                                        speechSegments.add(trimmedText)
                                    }
                                    Log.d(TAG, "Extended current segment to: '$trimmedText'")
                                }
                            } else if (!lastPartialText.startsWith(trimmedText, ignoreCase = true)) {
                                // This is a new speech segment after a gap
                                speechSegments.add(trimmedText)
                                Log.d(TAG, "New segment after gap: '$trimmedText'")
                            }
                            
                            lastPartialText = trimmedText
                            
                            // Build final accumulated text from segments
                            val finalText = speechSegments.joinToString(" ")
                            accumulatedTranscription.clear()
                            accumulatedTranscription.append(finalText)
                            
                            Log.d(TAG, "Current segments: ${speechSegments.joinToString(", ")}")
                            overlayManager.updateAccumulatedTranscription(finalText, false)
                        }
                    }
                }
                
                speechCoordinator.startListening(listener)
                
                // Wait for speech result with timeout
                val voiceCommand = withTimeoutOrNull(10.seconds) {
                    speechResult.await()
                }
                
                // Simple two-scenario logic
                val finalTranscription = accumulatedTranscription.toString().trim()
                val commandToProcess = voiceCommand ?: finalTranscription
                
                if (!commandToProcess.isNullOrEmpty()) {
                    // Scenario 1: We have transcribed text - send to automation service
                    Log.d(TAG, "Processing voice command: $commandToProcess")
                    
                    // Stop listening immediately to release microphone
                    speechCoordinator.stopListening()
                    
                    // Finalize transcription display
                    overlayManager.finalizeAccumulatedTranscription(commandToProcess)
                    overlayManager.hideTextInput()
                    overlayManager.hideTranscription()
                    
                    // Pause wake word service during automation
                    pauseWakeWordService()
                    
                    // Provide immediate feedback
                    val feedback = generateCommandFeedback(commandToProcess)
                    speechCoordinator.speakText(feedback)
                    overlayManager.hideErrorMessage()
                    
                    // Send to automation service
                    val intent = Intent("com.vibeagent.dude.VOICE_COMMAND")
                    intent.putExtra("command", commandToProcess)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    
                    Log.d(TAG, "Voice command sent to automation service: $commandToProcess")
                    
                } else {
                    // Scenario 2: No transcription - fallback to text input
                    Log.d(TAG, "No speech detected - showing text input fallback")
                    showingTextInputFallback = true
                    overlayManager.showErrorMessage("No voice detected")
                    Handler(Looper.getMainLooper()).postDelayed({
                        val textInputResult = CompletableDeferred<String?>()
                        showTextInputDialog(textInputResult)
                        
                        serviceScope.launch {
                            try {
                                val textCommand = textInputResult.await()
                                if (!textCommand.isNullOrEmpty()) {
                                    // Send text command to automation service
                                    val intent = Intent("com.vibeagent.dude.VOICE_COMMAND")
                                    intent.putExtra("command", textCommand)
                                    intent.setPackage(packageName)
                                    sendBroadcast(intent)
                                    
                                    Log.d(TAG, "Text command sent to automation service: $textCommand")
                                } else {
                                    isProcessingVoice = false
                                    overlayManager.hideVoiceOverlay()
                                    stopSelf()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing text input: ${e.message}", e)
                                isProcessingVoice = false
                                overlayManager.hideVoiceOverlay()
                                stopSelf()
                            }
                        }
                    }, 1500)
                    return@launch
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during voice interaction: ${e.message}", e)
                speechCoordinator.speakText("Sorry, there was an error processing your request.")
            } finally {
                // Simple cleanup - stop listening and clear state
                speechCoordinator.stopListening()
                
                // Only cleanup if not showing text input fallback
                if (!showingTextInputFallback) {
                    isProcessingVoice = false
                    overlayManager.finalizeAccumulatedTranscription("")
                    // Don't hide overlay here - let automation receivers handle it after TTS
                    accumulatedTranscription.clear()
                    // Don't stop service here - let automation completion handle it
                }
            }
        }
    }
    

    
    private fun generateCommandFeedback(command: String): String {
        return when {
            command.contains("turn on", ignoreCase = true) -> "Turning on"
            command.contains("turn off", ignoreCase = true) -> "Turning off"
            command.contains("open", ignoreCase = true) -> "Opening"
            command.contains("close", ignoreCase = true) -> "Closing"
            command.contains("start", ignoreCase = true) -> "Starting"
            command.contains("stop", ignoreCase = true) -> "Stopping"
            command.contains("set", ignoreCase = true) -> "Setting"
            command.contains("get", ignoreCase = true) || command.contains("what", ignoreCase = true) -> "Getting information"
            else -> "Processing your request"
        }
    }
    
    private fun showTextInputDialog(result: CompletableDeferred<String?>? = null) {
        // Show text input in the overlay area instead of separate dialog
        Handler(Looper.getMainLooper()).post {
            try {
                // Hide error message before showing text input
                overlayManager.hideErrorMessage()
                
                overlayManager.showTextInput { text ->
                    if (!text.isNullOrEmpty()) {
                        Log.d(TAG, "Text input received: $text")
                        result?.complete(text)
                        // Don't process here if result is provided - let caller handle it
                        if (result == null) {
                            // Send text command to automation service
                            val intent = Intent("com.vibeagent.dude.VOICE_COMMAND")
                            intent.putExtra("command", text)
                            intent.setPackage(packageName)
                            sendBroadcast(intent)
                            
                            Log.d(TAG, "Text command sent to automation service: $text")
                        }
                    } else {
                        result?.complete(null)
                        if (result == null) {
                            isProcessingVoice = false
                            overlayManager.hideVoiceOverlay()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing text input: ${e.message}", e)
                result?.complete(null)
                if (result == null) {
                    isProcessingVoice = false
                    overlayManager.hideVoiceOverlay()
                }
            }
        }
    }
    
    private fun stopVoiceInteraction() {
        Log.d(TAG, "Stopping voice interaction")
        
        serviceScope.launch {
            try {
                speechCoordinator.stopListening()
                overlayManager.finalizeAccumulatedTranscription("")
                overlayManager.hideVoiceOverlay()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice interaction: ${e.message}", e)
            } finally {
                // Reset session state
                isProcessingVoice = false
                accumulatedTranscription.clear()
                stopSelf()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceAgentService destroyed")
        
        // Cancel all pending tasks
        serviceScope.launch {
            taskMutex.withLock {
                taskQueue.forEach { task ->
                    if (task.status != TaskStatus.COMPLETED) {
                        task.status = TaskStatus.CANCELLED
                        Log.d(TAG, "Cancelled task ${task.id} during service destruction")
                    }
                }
                taskQueue.clear()
                currentTask = null
            }
        }
        
        // Clean up resources
        speechCoordinator.cleanup()
        overlayManager.cleanup()
        
        // Unregister receivers
        unregisterAutomationReceivers()
        
        // Cancel any ongoing coroutines
        serviceScope.cancel()
        
        Log.d(TAG, "VoiceAgentService cleanup completed")
    }
    
    private fun registerAutomationReceivers() {
        try {
            // Register automation completion receiver
            automationCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val resultJson = intent?.getStringExtra("result")
                    Log.d(TAG, "Automation completed with result: $resultJson")
                    serviceScope.launch {
                        // Parse result to determine success/failure
                        var isSuccess = true
                        try {
                            if (resultJson != null) {
                                val jsonObject = org.json.JSONObject(resultJson)
                                isSuccess = jsonObject.optBoolean("success", true)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse automation result JSON: ${e.message}")
                        }
                        
                        // Provide immediate voice feedback
                        val feedbackMessage = if (isSuccess) {
                            "Task completed successfully"
                        } else {
                            "Error occurred, please try again"
                        }
                        
                        // Provide TTS feedback and wait for completion before hiding overlay
                        speechCoordinator.speakText(feedbackMessage)
                        
                        // Wait a bit more to ensure TTS is fully complete
                        delay(500)
                        
                        // Hide overlay and stop service after TTS completes
                        withContext(Dispatchers.Main) {
                            overlayManager.hideVoiceOverlay()
                        }
                        completeCurrentTask()
                        
                        // Reset wake word detection to fresh state
                        resumeWakeWordService()
                        stopSelf()
                    }
                }
            }
            
            val completeFilter = IntentFilter("com.vibeagent.dude.AUTOMATION_COMPLETE")
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(automationCompleteReceiver, completeFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(automationCompleteReceiver, completeFilter)
            }
            
            Log.d(TAG, "Automation receivers registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering automation receivers: ${e.message}")
        }
    }
    
    private fun unregisterAutomationReceivers() {
        try {
            automationCompleteReceiver?.let { 
                unregisterReceiver(it)
                automationCompleteReceiver = null
            }
            Log.d(TAG, "Automation receivers unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering automation receivers: ${e.message}")
        }
    }
    
    private suspend fun completeCurrentTask() {
        taskMutex.withLock {
            currentTask?.let { task ->
                task.status = TaskStatus.COMPLETED
                Log.d(TAG, "Task ${task.id} completed")
                
                // Remove old completed tasks to prevent memory leaks
                val iterator = taskQueue.iterator()
                while (iterator.hasNext()) {
                    val queuedTask = iterator.next()
                    if (queuedTask.status == TaskStatus.COMPLETED && 
                        System.currentTimeMillis() - queuedTask.timestamp > 60000) { // Remove tasks older than 1 minute
                        iterator.remove()
                        Log.d(TAG, "Removed old completed task ${queuedTask.id}")
                    }
                }
            }
            
            // Reset speech components for reuse instead of full cleanup
            speechCoordinator.resetForNewTask()
            
            // Reset processing state - overlay hiding is handled by automation receivers after TTS
            withContext(Dispatchers.Main) {
                overlayManager.hideTranscription()
                isProcessingVoice = false
            }
            
            // Process next pending task if available
            processNextTask()
        }
    }
    
    private suspend fun enqueueVoiceTask(wakeWordDetected: Boolean) {
         taskMutex.withLock {
             // If this is a new wakeword detection, cancel all pending tasks
             if (wakeWordDetected) {
                 val cancelledCount = taskQueue.count { task ->
                     if (task.status == TaskStatus.PENDING) {
                         task.status = TaskStatus.CANCELLED
                         Log.d(TAG, "Cancelled pending task ${task.id} due to new wakeword")
                         true
                     } else false
                 }
                 if (cancelledCount > 0) {
                     Log.d(TAG, "Cancelled $cancelledCount pending tasks for new wakeword")
                 }
             }
             
             val task = VoiceTask(
                 id = ++taskIdCounter,
                 wakeWordDetected = wakeWordDetected
             )
             taskQueue.add(task)
             Log.d(TAG, "Enqueued voice task ${task.id} (wake word: $wakeWordDetected)")
             
             // Process immediately if no current task
             if (currentTask == null) {
                 processNextTask()
             }
         }
     }
    
    private suspend fun processNextTask() {
        // Find next pending task
        val nextTask = taskQueue.firstOrNull { it.status == TaskStatus.PENDING }
        if (nextTask != null) {
            currentTask = nextTask
            nextTask.status = TaskStatus.PROCESSING
            Log.d(TAG, "Processing task ${nextTask.id}")
            
            // Start voice interaction for this task
            withContext(Dispatchers.Main) {
                startVoiceInteraction(nextTask.wakeWordDetected)
            }
        } else {
            currentTask = null
            Log.d(TAG, "No pending tasks to process")
            
            // Stop service if no tasks remaining
            if (taskQueue.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED }) {
                stopSelf()
            }
         }
     }
     
     private fun createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             val channel = NotificationChannel(
                 NOTIFICATION_CHANNEL_ID,
                 "Voice Agent Service",
                 NotificationManager.IMPORTANCE_LOW
             ).apply {
                 description = "Voice interaction and automation service"
                 setShowBadge(false)
             }
             
             val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.createNotificationChannel(channel)
         }
     }
     
     private fun createNotification(): Notification {
         return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
             .setContentTitle("Voice Agent Active")
             .setContentText("Listening for voice commands")
             .setSmallIcon(android.R.drawable.ic_btn_speak_now)
             .setPriority(NotificationCompat.PRIORITY_LOW)
             .setOngoing(true)
             .setShowWhen(false)
             .build()
     }
     
     /**
      * Check if all required permissions are granted for microphone foreground service
      */
     private fun hasRequiredPermissions(): Boolean {
         val hasRecordAudio = ContextCompat.checkSelfPermission(
             this,
             Manifest.permission.RECORD_AUDIO
         ) == PackageManager.PERMISSION_GRANTED
         
         Log.d(TAG, "RECORD_AUDIO permission: $hasRecordAudio")
         return hasRecordAudio
     }
     
     /**
      * Pause the wake word service during automation to avoid microphone conflicts
      */
     private fun pauseWakeWordService() {
         try {
             val pauseIntent = Intent("com.vibeagent.dude.PAUSE_WAKE_WORD")
             sendBroadcast(pauseIntent)
             Log.d(TAG, "Sent pause broadcast to wake word service")
         } catch (e: Exception) {
             Log.e(TAG, "Failed to pause wake word service: ${e.message}", e)
         }
     }
     
     /**
      * Resume the wake word service after automation completes
      */
     private fun resumeWakeWordService() {
         try {
             // Send manual restart broadcast instead of resume to prevent continuous triggering
             val restartIntent = Intent("com.vibeagent.dude.RESTART_WAKE_WORD")
             sendBroadcast(restartIntent)
             Log.d(TAG, "Sent manual restart broadcast to wake word service")
         } catch (e: Exception) {
             Log.e(TAG, "Failed to restart wake word service: ${e.message}", e)
         }
     }
     
     /**
      * Calculate dynamic timeout based on speech activity
      * @param speechStarted Whether speech has been detected
      * @param hasPartialResults Whether partial results have been received
      * @param lastPartialTime Timestamp of last partial result
      * @return Timeout duration in milliseconds
      */
     private fun calculateDynamicTimeout(speechStarted: Boolean, hasPartialResults: Boolean, lastPartialTime: Long): kotlin.time.Duration {
         return when {
             // If no speech detected at all, use short timeout
             !speechStarted -> {
                 Log.d(TAG, "No speech detected - using short timeout (8s)")
                 8.seconds
             }
             // If speech started but no partial results, use medium timeout
             speechStarted && !hasPartialResults -> {
                 Log.d(TAG, "Speech started but no partial results - using medium timeout (12s)")
                 12.seconds
             }
             // If we have partial results, use longer timeout
             hasPartialResults -> {
                 val currentTime = System.currentTimeMillis()
                 val timeSinceLastPartial = currentTime - lastPartialTime
                 val timeout = when {
                     timeSinceLastPartial < 3000 -> 20.seconds
                     timeSinceLastPartial < 8000 -> 15.seconds
                     else -> 10.seconds
                 }
                 Log.d(TAG, "Partial results detected (${timeSinceLastPartial}ms ago) - using timeout: ${timeout.inWholeSeconds}s")
                 timeout
             }
             // Default fallback
             else -> {
                 Log.d(TAG, "Using default timeout (10s)")
                 10.seconds
             }
         }
     }
 }