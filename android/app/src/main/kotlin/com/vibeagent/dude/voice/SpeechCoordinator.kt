package com.vibeagent.dude.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class SpeechCoordinator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SpeechCoordinator"

        @Volatile
        private var INSTANCE: SpeechCoordinator? = null

        fun getInstance(context: Context): SpeechCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val coordinatorScope = CoroutineScope(Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var sttListener: SpeechToTextService.STTListener? = null

    // Mutex to ensure only one speech operation at a time
    private val speechMutex = Mutex()
    private var ttsPlaybackJob: Job? = null
    
    // State tracking
    private var isSpeaking = false
    private var isListening = false
    private var isTtsInitialized = false
    private var lastPartialResult: String? = null
    private var hasPartialResults = false

    init {
        initializeTextToSpeech()
        initializeSpeechRecognizer()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported for TTS")
                } else {
                    // Configure audio stream for proper playback
                    val audioParams = Bundle()
                    audioParams.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
                    audioParams.putString(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0")
                    
                    isTtsInitialized = true
                    Log.d(TAG, "TextToSpeech initialized successfully with audio stream configuration")
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        Log.d(TAG, "Speech recognizer initialized successfully")
    }

    /**
     * Wait for TTS initialization with timeout
     */
    private suspend fun waitForTtsInitialization(timeoutMs: Long = 3000): Boolean {
        val startTime = System.currentTimeMillis()
        while (!isTtsInitialized && (System.currentTimeMillis() - startTime) < timeoutMs) {
            delay(100)
        }
        return isTtsInitialized
    }

    /**
     * Speak text using TTS, ensuring STT is not listening
     * @param text The text to speak
     */
    suspend fun speakText(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking: $text")
                    stopListening()
                    delay(250) // Brief pause to ensure STT is fully stopped
                }

                // Wait for TTS initialization if not ready
                if (!isTtsInitialized) {
                    Log.d(TAG, "TTS not ready, waiting for initialization...")
                    val ttsReady = waitForTtsInitialization()
                    if (!ttsReady) {
                        Log.w(TAG, "TTS initialization timeout, cannot speak: $text")
                        return@withLock
                    }
                    Log.d(TAG, "TTS initialization complete")
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS: $text")

                // Check audio volume and ensure it's audible
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                Log.d(TAG, "Current STREAM_MUSIC volume: $currentVolume/$maxVolume")
                
                if (currentVolume == 0) {
                    Log.w(TAG, "STREAM_MUSIC volume is 0, TTS may not be audible")
                }

                withContext(Dispatchers.Main) {
                    val audioParams = Bundle()
                    audioParams.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
                    audioParams.putString(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0")
                    
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, audioParams, "speech_id")
                    Log.d(TAG, "TTS speak called with audio stream: STREAM_MUSIC")
                }

                // Wait for TTS to complete
                waitForTtsCompletion()

                Log.d(TAG, "TTS completed: $text")

            } finally {
                // Ensure the speaking flag is always reset
                isSpeaking = false
            }
        }
    }

    /**
     * Start listening for speech input, ensuring TTS is not speaking
     * @param listener Callback for speech recognition results
     */
    suspend fun startListening(listener: SpeechToTextService.STTListener) {
        speechMutex.withLock {
            try {
                if (isSpeaking) {
                    Log.d(TAG, "Waiting for TTS to finish before starting STT")
                    waitForTtsCompletion()
                }

                if (isListening) {
                    Log.d(TAG, "Already listening, stopping previous session")
                    stopListening()
                    delay(100)
                }

                if (speechRecognizer == null) {
                    Log.e(TAG, "Speech recognizer not initialized")
                    listener.onSpeechError("Speech recognizer not available")
                    return@withLock
                }

                sttListener = listener
                isListening = true
                Log.d(TAG, "Starting STT listening")
                
                // Ensure SpeechRecognizer operations run on main thread
                withContext(Dispatchers.Main) {
                    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(TAG, "Ready for speech")
                            sttListener?.onSpeechStarted()
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d(TAG, "Beginning of speech detected")
                        }

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            Log.d(TAG, "End of speech")
                        }

                        override fun onError(error: Int) {
                            val errorMessage = getErrorMessage(error)
                            Log.e(TAG, "Speech recognition error: $errorMessage")
                            isListening = false
                            sttListener?.onSpeechError(errorMessage)
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                Log.d(TAG, "Speech result: $text")
                                isListening = false
                                hasPartialResults = false
                                lastPartialResult = null
                                sttListener?.onSpeechResult(text)
                            } else {
                                Log.w(TAG, "No speech results")
                                isListening = false
                                // If we had partial results, use the last one as final result
                                if (hasPartialResults && !lastPartialResult.isNullOrEmpty()) {
                                    Log.d(TAG, "Using last partial result as final: $lastPartialResult")
                                    sttListener?.onSpeechResult(lastPartialResult!!)
                                } else {
                                    sttListener?.onSpeechError("No speech match found")
                                }
                                hasPartialResults = false
                                lastPartialResult = null
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val partialText = matches[0]
                                Log.d(TAG, "Partial speech result: $partialText")
                                lastPartialResult = partialText
                                hasPartialResults = true
                                sttListener?.onPartialResult(partialText)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                    }

                    speechRecognizer?.startListening(intent)
                    Log.d(TAG, "Started listening for speech")
                }

            } catch (e: Exception) {
                isListening = false
                Log.e(TAG, "Error starting STT: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Stop listening for speech input
     */
    fun stopListening() {
        if (isListening) {
            Log.d(TAG, "Stopping STT listening")
            coordinatorScope.launch {
                withContext(Dispatchers.Main) {
                    speechRecognizer?.stopListening()
                }
            }
            isListening = false
            sttListener = null
        }
    }

    /**
     * Wait for TTS to complete speaking
     */
    private suspend fun waitForTtsCompletion() {
        var attempts = 0
        val maxAttempts = 100 // 10 seconds max wait
        
        while (textToSpeech?.isSpeaking == true && attempts < maxAttempts) {
            delay(100)
            attempts++
        }
        
        if (attempts >= maxAttempts) {
            Log.w(TAG, "TTS completion wait timed out")
        }
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * Check if currently listening
     */
    fun isListening(): Boolean = isListening

    /**
     * Check if TTS is initialized
     */
    fun isTtsReady(): Boolean = isTtsInitialized

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

    /**
     * Reset for new task without full cleanup
     */
    fun resetForNewTask() {
        Log.d(TAG, "Resetting SpeechCoordinator for new task")
        
        // Stop any ongoing operations but keep components alive
        stopListening()
        ttsPlaybackJob?.cancel()
        
        // Stop TTS if speaking but don't shutdown
        textToSpeech?.let {
            if (it.isSpeaking) {
                it.stop()
            }
        }
        
        // Reset state for new task
        isSpeaking = false
        isListening = false
        sttListener = null
        lastPartialResult = null
        hasPartialResults = false
        
        Log.d(TAG, "SpeechCoordinator reset completed")
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up SpeechCoordinator")
        
        ttsPlaybackJob?.cancel()
        stopListening()
        
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        
        speechRecognizer?.destroy()
        speechRecognizer = null
        sttListener = null
        
        isTtsInitialized = false
        isSpeaking = false
        isListening = false
        
        INSTANCE = null
    }
}