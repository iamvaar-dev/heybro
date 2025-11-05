package com.vibeagent.dude.voice

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechToTextService : Service() {

    private val binder = STTBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var sttListener: STTListener? = null
    private var timeoutJob: kotlinx.coroutines.Job? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioManager.OnAudioFocusChangeListener? = null
    private var hasAudioFocus = false

    companion object {
        private const val TAG = "SpeechToTextService"
        private const val LISTENING_TIMEOUT_MS = 10000L // 10 seconds
        var isRunning = false
    }

    interface STTListener {
        fun onSpeechResult(text: String)
        fun onSpeechError(error: String)
        fun onSpeechStarted()
        fun onSpeechEnded()
        fun onPartialResult(partialText: String)
    }

    inner class STTBinder : Binder() {
        fun getService(): SpeechToTextService = this@SpeechToTextService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SpeechToTextService created")
        isRunning = true
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        initializeAudioFocus()
        initializeSpeechRecognizer()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available on this device")
            sttListener?.onSpeechError("Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                sttListener?.onSpeechStarted()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech detected")
                cancelTimeout()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - can be used for visual feedback
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech detected")
                isListening = false
                sttListener?.onSpeechEnded()
            }

            override fun onError(errorCode: Int) {
                val errorMessage = when (errorCode) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech input"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error: $errorCode"
                }
                
                Log.e(TAG, "Speech recognition error: $errorMessage (code: $errorCode)")
                isListening = false
                cancelTimeout()
                releaseAudioFocus()
                
                // Handle recoverable errors with retry logic
                when (errorCode) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_AUDIO -> {
                        Log.d(TAG, "Recoverable error detected, attempting recovery")
                        handleRecoverableError(errorMessage)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Log.e(TAG, "Critical error: Missing microphone permissions")
                        sttListener?.onSpeechError("Microphone permission required")
                    }
                    else -> {
                        sttListener?.onSpeechError(errorMessage)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d(TAG, "Speech recognition result: $recognizedText")
                    sttListener?.onSpeechResult(recognizedText)
                } else {
                    Log.w(TAG, "No speech recognition results")
                    sttListener?.onSpeechError("No speech detected")
                }
                isListening = false
                cancelTimeout()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d(TAG, "Partial speech result: $partialText")
                    sttListener?.onPartialResult(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Speech recognition event
            }
        })

        Log.d(TAG, "Speech recognizer initialized")
    }

    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening for speech")
            return
        }

        // Check microphone permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Microphone permission not granted")
            sttListener?.onSpeechError("Microphone permission required")
            return
        }

        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer not initialized")
            sttListener?.onSpeechError("Speech recognizer not available")
            return
        }

        // Request audio focus before starting
        if (!requestAudioFocus()) {
            Log.e(TAG, "Failed to gain audio focus")
            sttListener?.onSpeechError("Audio focus unavailable")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            startTimeout()
            Log.d(TAG, "Started listening for speech")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            sttListener?.onSpeechError("Failed to start speech recognition: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isListening) {
            Log.w(TAG, "Not currently listening for speech")
            return
        }

        try {
            speechRecognizer?.stopListening()
            isListening = false
            cancelTimeout()
            releaseAudioFocus()
            Log.d(TAG, "Stopped listening for speech")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition: ${e.message}", e)
        }
    }

    fun cancelListening() {
        if (!isListening) {
            return
        }

        try {
            speechRecognizer?.cancel()
            isListening = false
            cancelTimeout()
            Log.d(TAG, "Cancelled speech recognition")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition: ${e.message}", e)
        }
    }

    fun setSTTListener(listener: STTListener?) {
        this.sttListener = listener
    }

    fun isCurrentlyListening(): Boolean {
        return isListening
    }

    /**
     * Start single-shot listening for wake word detection
     * This method will NOT automatically restart - caller must handle restart logic
     */
    fun startWakeWordListening(onWakeWordDetected: (String) -> Unit) {
        Log.d(TAG, "Starting single-shot wake word listening")
        
        setSTTListener(object : STTListener {
            override fun onSpeechResult(text: String) {
                Log.d(TAG, "STT result: $text")
                
                // Check if wake word is detected
                if (text.lowercase().contains("hey droid") || 
                    text.lowercase().contains("hey dude") ||
                    text.lowercase().contains("droid")) {
                    Log.d(TAG, "Wake word detected: $text")
                    onWakeWordDetected(text)
                } else {
                    Log.d(TAG, "No wake word detected in: $text")
                }
            }

            override fun onSpeechError(error: String) {
                Log.w(TAG, "STT error: $error")
                // Do not automatically restart - let caller handle
            }

            override fun onSpeechStarted() {
                Log.d(TAG, "STT started")
            }

            override fun onSpeechEnded() {
                Log.d(TAG, "STT ended")
            }

            override fun onPartialResult(partialText: String) {
                // Check partial results for wake word as well
                if (partialText.lowercase().contains("hey droid") || 
                    partialText.lowercase().contains("hey dude") ||
                    partialText.lowercase().contains("droid")) {
                    Log.d(TAG, "Wake word detected in partial result: $partialText")
                    onWakeWordDetected(partialText)
                }
            }
        })
        
        // Start the listening session
        startListening()
    }

    /**
     * Stop wake word listening and clear listener
     */
    fun stopWakeWordListening() {
        Log.d(TAG, "Stopping wake word listening")
        stopListening()
        setSTTListener(null)
    }

    private fun startTimeout() {
        cancelTimeout()
        timeoutJob = serviceScope.launch {
            delay(LISTENING_TIMEOUT_MS)
            if (isListening) {
                Log.w(TAG, "Speech recognition timeout")
                stopListening()
                sttListener?.onSpeechError("Speech recognition timeout")
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun handleRecoverableError(errorMessage: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Attempting to recover from error: $errorMessage")
                
                // Clean up current recognizer
                speechRecognizer?.destroy()
                
                // Wait a bit before retry
                delay(1000)
                
                // Reinitialize speech recognizer
                initializeSpeechRecognizer()
                
                Log.d(TAG, "Speech recognizer recovery completed")
                sttListener?.onSpeechError("$errorMessage - Recovery attempted")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover from speech recognition error: ${e.message}", e)
                sttListener?.onSpeechError("$errorMessage - Recovery failed")
            }
        }
    }

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error: $error"
        }
    }

    private fun initializeAudioFocus() {
        audioFocusRequest = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.d(TAG, "Audio focus gained")
                    hasAudioFocus = true
                }
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Log.d(TAG, "Audio focus lost")
                    hasAudioFocus = false
                    if (isListening) {
                        stopListening()
                        sttListener?.onSpeechError("Audio focus lost")
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(TAG, "Audio focus lost (can duck)")
                    // Continue listening but at lower volume
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            val result = audioManager?.requestAudioFocus(
                audioFocusRequest,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "Audio focus request result: $hasAudioFocus")
            hasAudioFocus
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}", e)
            false
        }
    }

    private fun releaseAudioFocus() {
        try {
            audioFocusRequest?.let { listener ->
                audioManager?.abandonAudioFocus(listener)
            }
            hasAudioFocus = false
            Log.d(TAG, "Audio focus released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio focus: ${e.message}", e)
        }
    }

    private fun cleanup() {
        try {
            cancelTimeout()
            releaseAudioFocus()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            Log.d(TAG, "Speech recognizer resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SpeechToTextService destroyed")
        cleanup()
        isRunning = false
    }
}