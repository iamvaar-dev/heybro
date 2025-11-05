package com.vibeagent.dude.voice.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import com.vibeagent.dude.R

/**
 * VoiceOverlayManager handles the visual overlay during voice interactions.
 * Shows a floating overlay with voice status and wave animation.
 */
class VoiceOverlayManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceOverlayManager"
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private var overlayView: View? = null
    private var statusText: TextView? = null
    private var waveAnimation: VoiceWaveView? = null
    private var textInputContainer: LinearLayout? = null
    private var textInputField: EditText? = null
    private var transcriptionContainer: LinearLayout? = null
    private var transcriptionText: TextView? = null
    private val overlayScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isShowing = false
    private var isTextInputMode = false
    private var textInputCallback: ((String?) -> Unit)? = null
    private var accumulatedTranscription = StringBuilder()
    private var isAccumulatingTranscription = false
    
    fun showVoiceOverlay() {
        if (isShowing) {
            Log.w(TAG, "Overlay already showing")
            return
        }
        
        try {
            overlayScope.launch {
                createOverlayView()
                addOverlayToWindow()
                startWaveAnimation()
                isShowing = true
                Log.d(TAG, "Voice overlay shown")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show voice overlay: ${e.message}", e)
        }
    }
    
    fun hideVoiceOverlay() {
        if (!isShowing) {
            return
        }
        
        try {
            // Force immediate hiding on main thread to prevent stuck overlays
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // Already on main thread, execute immediately
                stopWaveAnimation()
                removeOverlayFromWindow()
                isShowing = false
                Log.d(TAG, "Voice overlay hidden immediately")
            } else {
                // Post to main thread and wait for completion
                val handler = Handler(Looper.getMainLooper())
                val latch = java.util.concurrent.CountDownLatch(1)
                handler.post {
                    try {
                        stopWaveAnimation()
                        removeOverlayFromWindow()
                        isShowing = false
                        Log.d(TAG, "Voice overlay hidden on main thread")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error hiding overlay on main thread: ${e.message}", e)
                    } finally {
                        latch.countDown()
                    }
                }
                // Wait for completion with timeout
                try {
                    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Timeout waiting for overlay hide", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide voice overlay: ${e.message}", e)
            // Force reset state even if hiding failed
            isShowing = false
        }
    }
    
    fun showErrorMessage(errorText: String) {
        overlayScope.launch {
            waveAnimation?.showError(errorText)
        }
    }
    
    fun hideErrorMessage() {
        overlayScope.launch {
            waveAnimation?.hideError()
        }
    }
    
    fun showTranscription(text: String) {
        overlayScope.launch {
            transcriptionText?.text = text
            transcriptionContainer?.visibility = View.VISIBLE
        }
    }
    
    fun updateTranscription(text: String) {
        overlayScope.launch {
            transcriptionText?.text = text
        }
    }
    
    fun startAccumulatingTranscription(clearPrevious: Boolean = true) {
        overlayScope.launch {
            isAccumulatingTranscription = true
            if (clearPrevious) {
                accumulatedTranscription.clear()
                transcriptionText?.text = "Listening..."
            } else {
                // Continue from previous transcription for speech gaps
                val currentText = accumulatedTranscription.toString()
                if (currentText.isNotEmpty()) {
                    transcriptionText?.text = "$currentText (continuing...)"
                } else {
                    transcriptionText?.text = "Listening..."
                }
            }
            transcriptionContainer?.visibility = View.VISIBLE
        }
    }
    
    fun updateAccumulatedTranscription(partialText: String, isContinuation: Boolean = false) {
        overlayScope.launch {
            if (isAccumulatingTranscription) {
                // Clean the partial text
                val cleanText = partialText.trim()
                if (cleanText.isNotEmpty()) {
                    if (isContinuation && accumulatedTranscription.isNotEmpty()) {
                        // For continuation, check if the new text extends the previous text
                        val currentText = accumulatedTranscription.toString()
                        if (!cleanText.startsWith(currentText)) {
                            // Add space and append new text
                            if (!currentText.endsWith(" ") && !cleanText.startsWith(" ")) {
                                accumulatedTranscription.append(" ")
                            }
                            accumulatedTranscription.append(cleanText)
                        } else {
                            // Replace with the longer version if it extends current text
                            accumulatedTranscription.clear()
                            accumulatedTranscription.append(cleanText)
                        }
                    } else {
                        // For new partial results, simply replace the accumulated text with the latest partial result
                        // This prevents redundant accumulation like "open open chrome open chrome"
                        accumulatedTranscription.clear()
                        accumulatedTranscription.append(cleanText)
                    }
                    transcriptionText?.text = accumulatedTranscription.toString()
                }
            }
        }
    }
    
    fun finalizeAccumulatedTranscription(finalText: String) {
        overlayScope.launch {
            if (isAccumulatingTranscription) {
                isAccumulatingTranscription = false
                val cleanFinalText = finalText.trim()
                if (cleanFinalText.isNotEmpty()) {
                    accumulatedTranscription.clear()
                    accumulatedTranscription.append(cleanFinalText)
                    transcriptionText?.text = cleanFinalText
                }
            }
        }
    }
    
    fun hideTranscription() {
        overlayScope.launch {
            isAccumulatingTranscription = false
            accumulatedTranscription.clear()
            transcriptionContainer?.visibility = View.GONE
            transcriptionText?.text = ""
        }
    }
    
    private fun createOverlayView() {
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.voice_overlay_layout, null)
        
        waveAnimation = overlayView?.findViewById(R.id.wave_animation)
        textInputContainer = overlayView?.findViewById(R.id.text_input_container)
        textInputField = overlayView?.findViewById(R.id.text_input_field)
        transcriptionContainer = overlayView?.findViewById(R.id.transcription_container)
        transcriptionText = overlayView?.findViewById(R.id.transcription_text)
        
        setupTextInputListeners()
    }
    
    private fun addOverlayToWindow() {
        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            flags = if (isTextInputMode) {
                // Allow focus for text input mode (tap-to-type)
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            } else {
                // Non-focusable for voice mode
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
            
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.BOTTOM
        }
        
        overlayView?.let { view ->
            windowManager.addView(view, layoutParams)
        }
    }
    
    private fun removeOverlayFromWindow() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}", e)
            }
        }
        overlayView = null
        statusText = null
        waveAnimation = null
        textInputContainer = null
        textInputField = null
        transcriptionContainer = null
        transcriptionText = null
    }
    
    private fun startWaveAnimation() {
        waveAnimation?.startAnimation()
    }
    
    private fun stopWaveAnimation() {
        waveAnimation?.stopAnimation()
    }
    
    fun showTextInput(callback: (String?) -> Unit) {
        overlayScope.launch {
            textInputCallback = callback
            isTextInputMode = true
            
            if (!isShowing) {
                showVoiceOverlay()
            } else {
                // Recreate overlay with proper focus flags for text input
                removeOverlayFromWindow()
                createOverlayView()
                addOverlayToWindow()
            }
            
            // Hide wave animation and show text input
            waveAnimation?.visibility = View.GONE
            textInputContainer?.visibility = View.VISIBLE
            
            // Ensure text input is visible and focused with delay for proper rendering
            Handler(Looper.getMainLooper()).postDelayed({
                textInputField?.let { field ->
                    field.requestFocus()
                    field.isFocusableInTouchMode = true
                    field.isFocusable = true
                    // Force show keyboard
                    inputMethodManager.showSoftInput(field, InputMethodManager.SHOW_FORCED)
                    Log.d(TAG, "Text input focused and keyboard shown")
                }
            }, 100) // Small delay to ensure proper rendering
        }
    }
    
    fun hideTextInput() {
        overlayScope.launch {
            Log.d(TAG, "Hiding text input - current mode: $isTextInputMode")
            isTextInputMode = false
            textInputCallback = null
            
            // Hide keyboard
            textInputField?.let { field ->
                inputMethodManager.hideSoftInputFromWindow(field.windowToken, 0)
            }
            
            // Clear text field
            textInputField?.text?.clear()
            
            // Only recreate overlay if it's currently showing
            if (isShowing) {
                // Recreate overlay with non-focusable flags for voice mode
                removeOverlayFromWindow()
                createOverlayView()
                addOverlayToWindow()
                
                // Show wave animation and hide text input
                textInputContainer?.visibility = View.GONE
                waveAnimation?.visibility = View.VISIBLE
                
                // Start wave animation to show we're back in voice mode
                startWaveAnimation()
            }
        }
    }
    
    private fun setupTextInputListeners() {
        // Handle Enter key press to submit
        textInputField?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                val text = textInputField?.text?.toString()?.trim()
                if (!text.isNullOrEmpty() && isValidCommand(text)) {
                    textInputCallback?.invoke(text)
                    hideTextInput()
                } else if (!text.isNullOrEmpty()) {
                    textInputField?.error = "Please enter a valid voice command"
                }
                true
            } else {
                false
            }
        }
        
        // Add outside tap dismissal
        overlayView?.setOnTouchListener { view, event ->
            if (isTextInputMode && event.action == android.view.MotionEvent.ACTION_DOWN) {
                val textInputRect = android.graphics.Rect()
                textInputContainer?.getGlobalVisibleRect(textInputRect)
                
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                
                // If tap is outside the text input container, dismiss properly
                if (!textInputRect.contains(x, y)) {
                    Log.d(TAG, "Outside tap detected - dismissing text input")
                    // Hide keyboard first
                    textInputField?.let { field ->
                        inputMethodManager.hideSoftInputFromWindow(field.windowToken, 0)
                    }
                    // Properly dismiss text input
                    textInputCallback?.invoke(null)
                    hideTextInput()
                    // Don't hide the entire overlay immediately - let the service handle it
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
    
    private fun isValidCommand(text: String): Boolean {
        // Basic validation for voice commands
        if (text.length < 3) return false
        if (text.length > 200) return false
        
        // Check for common voice command patterns
        val validPatterns = listOf(
            "open", "launch", "start", "go to", "navigate", "search", "find", "call", "send", "play",
            "stop", "pause", "resume", "next", "previous", "volume", "brightness", "settings",
            "weather", "time", "date", "alarm", "timer", "reminder", "note", "message", "email",
            "take", "capture", "record", "turn on", "turn off", "enable", "disable", "close", "exit"
        )
        
        val lowerText = text.lowercase()
        return validPatterns.any { pattern -> lowerText.contains(pattern) }
    }
    
    fun cleanup() {
        overlayScope.launch {
            hideTextInput()
            hideVoiceOverlay()
            overlayScope.cancel()
        }
    }
}