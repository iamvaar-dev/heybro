package com.vibeagent.dude

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import com.vibeagent.dude.voice.VoiceAgentCoordinator
import com.vibeagent.dude.voice.EnhancedWakeWordService

class MainActivity : FlutterActivity() {
    companion object {
        private const val TAG = "AIAgent"
        private const val TOOLS_CHANNEL = "com.vibeagent.dude/tools"
        private const val AGENT_CHANNEL = "com.vibeagent.dude/agent"
        private const val AUTOMATION_CHANNEL = "com.vibeagent.dude/automation"
        private const val VOICE_CHANNEL = "com.vibeagent.dude/voice"
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1001
        var instance: MainActivity? = null
    }

    private var toolsChannel: MethodChannel? = null
    private var agentChannel: MethodChannel? = null
    private var automationChannel: MethodChannel? = null
    private var voiceChannel: MethodChannel? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var toolActivityManager: ToolActivityManager
    private lateinit var automationService: AutomationService
    private lateinit var accessibilityConsentManager: AccessibilityConsentManager
    
    // App Management Service
    private var appManagementService: AppManagementService? = null
    private var isAppServiceBound = false
    
    // Automation Foreground Service
    private var automationForegroundService: AutomationForegroundService? = null
    private var isAutomationServiceBound = false
    
    // Voice Agent
    private var voiceAgentCoordinator: VoiceAgentCoordinator? = null
    private var voiceCommandReceiver: BroadcastReceiver? = null
    private var audioConsentManager: AudioConsentManager? = null
    
    private val appServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AppManagementService.AppManagementBinder
            appManagementService = binder.getService()
            isAppServiceBound = true
            Log.d(TAG, "AppManagementService connected")
            
            // Initialize voice agent after app service is connected
            initializeVoiceAgent()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            appManagementService = null
            isAppServiceBound = false
            Log.d(TAG, "AppManagementService disconnected")
        }
    }
    
    private val automationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AutomationForegroundService.AutomationBinder
            automationForegroundService = binder.getService()
            isAutomationServiceBound = true
            
            // Set method channel for communication
            automationChannel?.let { channel ->
                automationForegroundService?.setMethodChannel(channel)
            }
            
            Log.d(TAG, "AutomationForegroundService connected")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            automationForegroundService = null
            isAutomationServiceBound = false
            Log.d(TAG, "AutomationForegroundService disconnected")
        }
    }
    
    private fun initializeVoiceAgent() {
        try {
            voiceAgentCoordinator?.initialize(appManagementService, automationService)
            Log.d(TAG, "Voice agent initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize voice agent: ${e.message}", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        initializeComponents()
        
        // Handle voice interaction intent if app was started for that purpose
        handleVoiceInteractionIntent(intent)
        
        Log.d(TAG, "🚀 AI Agent MainActivity initialized")
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle voice interaction intent when app is already running
        handleVoiceInteractionIntent(intent)
    }
    
    private fun handleVoiceInteractionIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("start_voice_interaction", false) == true) {
            Log.d(TAG, "App brought to foreground for voice interaction")
            
            // Start VoiceAgentService now that app is in foreground
            coroutineScope.launch {
                delay(200) // Small delay to ensure app is fully in foreground
                try {
                    val voiceIntent = Intent(this@MainActivity, com.vibeagent.dude.voice.VoiceAgentService::class.java).apply {
                        action = com.vibeagent.dude.voice.VoiceAgentService.ACTION_START_VOICE_INTERACTION
                        putExtra(com.vibeagent.dude.voice.VoiceAgentService.EXTRA_WAKE_WORD_DETECTED, true)
                    }
                    startService(voiceIntent)
                    Log.d(TAG, "VoiceAgentService started from MainActivity")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start VoiceAgentService from MainActivity: ${e.message}", e)
                }
            }
        }
    }

    private fun initializeComponents() {
        toolActivityManager = ToolActivityManager(this)
        automationService = AutomationService(this)
        accessibilityConsentManager = AccessibilityConsentManager(this)
        audioConsentManager = AudioConsentManager(this)

        // Start and bind to AppManagementService
        startAppManagementService()
        
        // Start and bind to AutomationForegroundService
        startAutomationForegroundService()

        // Check and request accessibility permission if needed
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "⚠️ Accessibility service not enabled")
        }
        
        // Check and request overlay permission if needed
        if (!isOverlayPermissionGranted()) {
            Log.w(TAG, "⚠️ Overlay permission not granted, requesting...")
            requestOverlayPermission()
        }
        
        // Check and request audio permissions before initializing voice features
        if (!hasAudioPermissions()) {
            Log.w(TAG, "⚠️ Audio permissions not granted, requesting...")
            requestAudioPermissions()
        } else {
            // Initialize voice features only if permissions are granted
            initializeVoiceFeatures()
        }
    }
    
    private fun startAppManagementService() {
        try {
            val serviceIntent = Intent(this, AppManagementService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // Bind to the service
            bindService(serviceIntent, appServiceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "AppManagementService started and binding initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AppManagementService", e)
        }
    }
    
    private fun startAutomationForegroundService() {
        try {
            val serviceIntent = Intent(this, AutomationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // Bind to the service
            bindService(serviceIntent, automationServiceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "AutomationForegroundService started and binding initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AutomationForegroundService", e)
        }
    }
    
    private fun initializeVoiceFeatures() {
        try {
            Log.d(TAG, "Initializing voice features with audio permissions")
            
            // Initialize voice agent coordinator
            voiceAgentCoordinator = VoiceAgentCoordinator.getInstance(this)
            
            // Start wake word service for "Hey Droid" detection
            startWakeWordService()
            
            // Register voice command receiver
            registerVoiceCommandReceiver()
            
            Log.d(TAG, "Voice features initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize voice features", e)
        }
    }
    
    private fun startWakeWordService() {
        try {
            Log.d(TAG, "Starting Enhanced Wake Word Service for 'Hey Droid' detection")
            
            // Enable auto-start when user manually starts the service
            EnhancedWakeWordService.setAutoStartEnabled(this, true)
            
            // Check if service is already running and stop it first
            if (EnhancedWakeWordService.isServiceRunning()) {
                Log.d(TAG, "Existing Enhanced Wake Word Service found, stopping it first")
                val stopIntent = Intent(this, EnhancedWakeWordService::class.java)
                stopService(stopIntent)
                
                // Small delay to ensure service is fully stopped
                Thread.sleep(500)
            }
            
            EnhancedWakeWordService.startIndependentService(this)
            Log.d(TAG, "Enhanced Wake Word Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Enhanced Wake Word Service", e)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        toolsChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, TOOLS_CHANNEL)
        toolsChannel?.setMethodCallHandler { call, result -> handleToolCall(call, result) }

        agentChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AGENT_CHANNEL)
        agentChannel?.setMethodCallHandler { call, result -> handleAgentCall(call, result) }

        automationChannel =
                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AUTOMATION_CHANNEL)
        automationChannel?.setMethodCallHandler { call, result ->
            handleAutomationCall(call, result)
        }

        voiceChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, VOICE_CHANNEL)
        voiceChannel?.setMethodCallHandler { call, result -> handleVoiceCall(call, result) }

        Log.d(TAG, "📡 Tools, Agent, Automation and Voice channels configured")
    }
    
    /**
     * Check if all required audio permissions are granted
     */
    private fun hasAudioPermissions(): Boolean {
        // RECORD_AUDIO is the only permission needed for continuous wake word detection
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "🎤 RECORD_AUDIO permission: $hasRecordAudio")
        
        return hasRecordAudio
    }
    
    /**
     * Request audio permissions from user
     */
    private fun requestAudioPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
        
        Log.d(TAG, "Requesting audio permission: RECORD_AUDIO")
        ActivityCompat.requestPermissions(
            this,
            permissions,
            AUDIO_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            AUDIO_PERMISSION_REQUEST_CODE -> {
                // Check if RECORD_AUDIO permission was granted
                val recordAudioGranted = grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                
                Log.d(TAG, "Audio permission result: RECORD_AUDIO granted=$recordAudioGranted")
                
                // Save consent based on permission result
                audioConsentManager?.saveAudioConsent(recordAudioGranted)
                
                if (recordAudioGranted) {
                    Log.d(TAG, "✅ Audio permission granted, initializing voice features for continuous wake word detection")
                    initializeVoiceFeatures()
                } else {
                    Log.w(TAG, "⚠️ Audio permission denied, voice features will not be available")
                    showAudioPermissionDeniedMessage()
                }
            }
        }
    }
    
    /**
     * Show message when audio permissions are denied
     */
    private fun showAudioPermissionDeniedMessage() {
        Log.w(TAG, "Audio permissions denied - voice features disabled")
        // You could show a dialog or toast here if needed
        // For now, just log the message
    }

    private fun handleAgentCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "🤖 Agent call: ${call.method}")

        try {
            when (call.method) {
                "checkAccessibilityPermission" -> {
                    val isEnabled = isAccessibilityServiceEnabled()
                    Log.d(TAG, "✅ Accessibility permission check: $isEnabled")
                    result.success(isEnabled)
                }
                "openAccessibilitySettings" -> {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        Log.d(TAG, "📱 Opened accessibility settings")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to open accessibility settings", e)
                        result.error(
                                "SETTINGS_ERROR",
                                "Failed to open accessibility settings",
                                e.message
                        )
                    }
                }
                "checkOverlayPermission" -> {
                    val hasPermission = isOverlayPermissionGranted()
                    Log.d(TAG, "✅ Overlay permission check: $hasPermission")
                    result.success(hasPermission)
                }
                "requestOverlayPermission" -> {
                    try {
                        requestOverlayPermission()
                        Log.d(TAG, "📱 Requesting overlay permission")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to request overlay permission", e)
                        result.error(
                                "PERMISSION_ERROR",
                                "Failed to request overlay permission",
                                e.message
                        )
                    }
                }
                "openBatterySettings" -> {
                    try {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.data = Uri.fromParts("package", packageName, null)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        Log.d(TAG, "📱 Opened battery settings for app")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to open battery settings", e)
                        result.error(
                                "SETTINGS_ERROR",
                                "Failed to open battery settings",
                                e.message
                        )
                    }
                }
                "saveAccessibilityConsent" -> {
                    try {
                        val granted = call.argument<Boolean>("granted") ?: false
                        accessibilityConsentManager.saveAccessibilityConsent(granted)
                        Log.d(TAG, "💾 Accessibility consent saved: $granted")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error saving accessibility consent: ${e.message}", e)
                        result.error("CONSENT_ERROR", e.message, null)
                    }
                }
                "hasAccessibilityConsent" -> {
                    try {
                        val hasConsent = accessibilityConsentManager.hasAccessibilityConsent()
                        Log.d(TAG, "✅ Accessibility consent check: $hasConsent")
                        result.success(hasConsent)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error checking accessibility consent: ${e.message}", e)
                        result.error("CONSENT_ERROR", e.message, null)
                    }
                }
                "getConsentInfo" -> {
                    try {
                        val consentInfo = accessibilityConsentManager.getConsentInfo()
                        Log.d(TAG, "📋 Consent info retrieved: $consentInfo")
                        result.success(consentInfo)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error getting consent info: ${e.message}", e)
                        result.error("CONSENT_ERROR", e.message, null)
                    }
                }
                "checkAudioPermissions" -> {
                    try {
                        val hasPermissions = hasAudioPermissions()
                        Log.d(TAG, "🎤 Audio permissions check: $hasPermissions")
                        result.success(hasPermissions)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error checking audio permissions: ${e.message}", e)
                        result.error("PERMISSION_ERROR", e.message, null)
                    }
                }
                "requestAudioPermissions" -> {
                    try {
                        requestAudioPermissions()
                        Log.d(TAG, "🎤 Requesting audio permissions")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error requesting audio permissions: ${e.message}", e)
                        result.error("PERMISSION_ERROR", e.message, null)
                    }
                }
                "saveAudioConsent" -> {
                    try {
                        val granted = call.argument<Boolean>("granted") ?: false
                        audioConsentManager?.saveAudioConsent(granted)
                        Log.d(TAG, "💾 Audio consent saved: $granted")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error saving audio consent: ${e.message}", e)
                        result.error("CONSENT_ERROR", e.message, null)
                    }
                }
                "hasAudioConsent" -> {
                    try {
                        val hasConsent = audioConsentManager?.hasAudioConsent() ?: false
                        Log.d(TAG, "🎤 Audio consent check: $hasConsent")
                        result.success(hasConsent)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error checking audio consent: ${e.message}", e)
                        result.error("CONSENT_ERROR", e.message, null)
                    }
                }
                "getAudioConsentInfo" -> {
                    try {
                        val consentInfo = mapOf(
                            "hasConsent" to (audioConsentManager?.hasAudioConsent() ?: false),
                            "hasExplicitConsent" to (audioConsentManager?.hasUserExplicitlyGrantedConsent() ?: false),
                            "timestamp" to (audioConsentManager?.getConsentTimestamp() ?: 0L)
                        )
                        Log.d(TAG, "🎤 Audio consent info retrieved: $consentInfo")
                        result.success(consentInfo)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error getting audio consent info: ${e.message}", e)
                        result.error("CONSENT_ERROR", e.message, null)
                    }
                }
                else -> {
                    Log.w(TAG, "❓ Unknown agent method: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling agent call: ${call.method}", e)
            result.error("AGENT_ERROR", "Error handling agent call", e.message)
        }
    }

    private fun handleAutomationCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "🤖 Automation call: ${call.method}")

        try {
            when (call.method) {
                "executeUserTask" -> {
                    val userTask = call.argument<String>("user_task") ?: ""
                    coroutineScope.launch {
                        try {
                            val success = automationService.executeUserTask(userTask)
                            result.success(success)
                            Log.d(TAG, "✅ User task execution: $success")
                            
                            // Note: Completion/error broadcasts will be sent by Flutter automation service
                            // via notifyAutomationComplete/notifyAutomationError method calls
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ User task execution failed", e)
                            result.error("AUTOMATION_ERROR", e.message, null)
                            // Send error broadcast immediately for execution failures
                            notifyVoiceServiceAutomationError(e.message ?: "Unknown error")
                        }
                    }
                }
                "isAutomating" -> {
                    val isRunning = automationService.isAutomating()
                    result.success(isRunning)
                    Log.d(TAG, "✅ Automation status: $isRunning")
                }
                "stopAutomation" -> {
                    automationService.stopAutomation()
                    result.success(true)
                    Log.d(TAG, "✅ Automation stopped")
                }
                "startForegroundAutomation" -> {
                    val userTask = call.argument<String>("user_task") ?: ""
                    if (isAutomationServiceBound && automationForegroundService != null) {
                        automationForegroundService?.startAutomationTask(userTask)
                        result.success(true)
                        Log.d(TAG, "✅ Foreground automation started: $userTask")
                    } else {
                        result.error("SERVICE_ERROR", "Automation foreground service not bound", null)
                        Log.e(TAG, "❌ Automation foreground service not bound")
                    }
                }
                "stopForegroundAutomation" -> {
                    if (isAutomationServiceBound && automationForegroundService != null) {
                        automationForegroundService?.stopAutomationTask()
                        result.success(true)
                        Log.d(TAG, "✅ Foreground automation stopped")
                    } else {
                        result.error("SERVICE_ERROR", "Automation foreground service not bound", null)
                        Log.e(TAG, "❌ Automation foreground service not bound")
                    }
                }
                "notifyAutomationComplete" -> {
                    Log.d(TAG, "Flutter notified automation completion")
                    notifyVoiceServiceAutomationComplete()
                    result.success(true)
                }
                "notifyAutomationError" -> {
                    val error = call.argument<String>("error") ?: "Unknown error"
                    Log.d(TAG, "Flutter notified automation error: $error")
                    notifyVoiceServiceAutomationError(error)
                    result.success(true)
                }
                "getTaskHistory" -> {
                    val history = automationService.getTaskHistory()
                    val historyList =
                            history.map { step ->
                                mapOf(
                                        "action" to step.action,
                                        "parameters" to step.parameters,
                                        "result" to step.result,
                                        "timestamp" to step.timestamp,
                                        "preCondition" to step.preCondition
                                )
                            }
                    result.success(historyList)
                    Log.d(TAG, "✅ Task history retrieved: ${history.size} steps")
                }
                "clearTaskHistory" -> {
                    automationService.clearTaskHistory()
                    result.success(true)
                    Log.d(TAG, "✅ Task history cleared")
                }
                "getMemory" -> {
                    val memory = automationService.getMemory()
                    result.success(memory)
                    Log.d(TAG, "✅ Memory retrieved: ${memory.size} items")
                }
                "clearMemory" -> {
                    automationService.clearMemory()
                    result.success(true)
                    Log.d(TAG, "✅ Memory cleared")
                }
                else -> {
                    Log.w(TAG, "❓ Unknown automation method: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling automation call: ${call.method}", e)
            result.error("AUTOMATION_ERROR", "Error handling automation call", e.message)
        }
    }

    private fun handleVoiceCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "🎤 Voice call: ${call.method}")
        try {
            when (call.method) {
                "setPorcupineAccessKey" -> {
                    val accessKey = call.argument<String>("accessKey")
                    if (accessKey != null) {
                        coroutineScope.launch {
                            try {
                                val success = voiceAgentCoordinator?.setAccessKey(accessKey) ?: false
                                result.success(success)
                            } catch (e: Exception) {
                                result.error("ACCESS_KEY_SAVE_ERROR", "Failed to save access key", e.message)
                            }
                        }
                    } else {
                        result.error("INVALID_ACCESS_KEY", "Access key cannot be null", null)
                    }
                }
                "testPorcupineKey" -> {
                    val accessKey = call.argument<String>("accessKey")
                    if (accessKey != null) {
                        coroutineScope.launch {
                            try {
                                val isValid = voiceAgentCoordinator?.testAccessKey(accessKey) ?: false
                                if (isValid) {
                                    result.success(mapOf("success" to true))
                                } else {
                                    result.success(mapOf(
                                        "success" to false,
                                        "error" to "Invalid API key: Authentication failed. Please verify your Picovoice Console key."
                                    ))
                                }
                            } catch (e: Exception) {
                                val errorMessage = when {
                                    e.message?.contains("00000136") == true -> "Invalid API key: Authentication failed with Picovoice service"
                                    e.message?.contains("PorcupineActivationRefusedException") == true -> "API key activation refused by Picovoice"
                                    else -> "Failed to test access key: ${e.message}"
                                }
                                result.success(mapOf(
                                    "success" to false,
                                    "error" to errorMessage
                                ))
                            }
                        }
                    } else {
                        result.error("INVALID_ACCESS_KEY", "Access key cannot be null", null)
                    }
                }
                // Legacy support for old method names
                "setPorcupineApiKey" -> {
                    val apiKey = call.argument<String>("apiKey")
                    if (apiKey != null) {
                        coroutineScope.launch {
                            try {
                                val success = voiceAgentCoordinator?.setAccessKey(apiKey) ?: false
                                result.success(success)
                            } catch (e: Exception) {
                                result.error("ACCESS_KEY_SAVE_ERROR", "Failed to save access key", e.message)
                            }
                        }
                    } else {
                        result.error("INVALID_API_KEY", "API key cannot be null", null)
                    }
                }
                "testPorcupineApiKey" -> {
                    val apiKey = call.argument<String>("apiKey")
                    if (apiKey != null) {
                        coroutineScope.launch {
                            try {
                                val isValid = voiceAgentCoordinator?.testAccessKey(apiKey) ?: false
                                result.success(mapOf("success" to isValid))
                            } catch (e: Exception) {
                                result.error("API_KEY_TEST_ERROR", "Failed to test API key", e.message)
                            }
                        }
                    } else {
                        result.error("INVALID_API_KEY", "API key cannot be null", null)
                    }
                }
                "setAutoStartEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    EnhancedWakeWordService.setAutoStartEnabled(this@MainActivity, enabled)
                    result.success(mapOf("success" to true, "autoStartEnabled" to enabled))
                }
                "isAutoStartEnabled" -> {
                    val enabled = EnhancedWakeWordService.isAutoStartEnabled(this@MainActivity)
                    result.success(mapOf("autoStartEnabled" to enabled))
                }
                "stopWakeWordService" -> {
                    try {
                        // Disable auto-start when user manually stops the service
                        EnhancedWakeWordService.setAutoStartEnabled(this@MainActivity, false)
                        val stopIntent = Intent(this@MainActivity, EnhancedWakeWordService::class.java)
                        stopService(stopIntent)
                        result.success(mapOf("success" to true, "message" to "Wake word service stopped and auto-start disabled"))
                    } catch (e: Exception) {
                        result.error("STOP_SERVICE_ERROR", "Failed to stop wake word service", e.message)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling voice call: ${call.method}", e)
            result.error("VOICE_ERROR", "Error handling voice call", e.message)
        }
    }

    private fun handleToolCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "🔧 Tool call: ${call.method}")

        try {
            when (call.method) {
                // Screen capture and analysis
                "takeScreenshot" -> handleTakeScreenshot(result)
                "getAccessibilityTree" -> handleGetAccessibilityTree(result)
                "getScreenElements" -> handleGetAccessibilityTree(result)
                "analyzeScreen" -> handleAnalyzeScreen(result)
                "getCurrentApp" -> handleGetCurrentApp(result)
                "performOcr" -> handlePerformOcr(call, result)

                // Touch operations
                "performTap" -> handlePerformTap(call, result)
                "performLongPress" -> handlePerformLongPress(call, result)
                "performDoubleClick" -> handlePerformDoubleClick(call, result)

                // Gesture operations
                "performSwipe" -> handlePerformSwipe(call, result)
                "performScroll" -> handlePerformScroll(call, result)
                "performPinch" -> handlePerformPinch(call, result)
                "performZoomIn" -> handlePerformZoomIn(result)
                "performZoomOut" -> handlePerformZoomOut(result)

                // Text operations
                "typeText" -> handleTypeText(call, result)
                "performAdvancedType" -> handleTypeText(call, result)
                "advancedTypeText" -> handleAdvancedTypeText(call, result)
                "clearText" -> handleClearText(result)
                "nonTapTextInput" -> handleNonTapTextInput(call, result)
                "getFocusedInputInfo" -> handleGetFocusedInputInfo(result)
                "getAllInputFields" -> handleGetAllInputFields(result)
                // Removed text input methods
                "typeTextSlowly" -> handleTypeTextSlowly(call, result)
                "insertText" -> handleInsertText(call, result)

                // KeyEvent support
                "performEnter" -> handlePerformEnter(result)
                "performBackspace" -> handlePerformBackspace(result)
                "performDelete" -> handlePerformDelete(result)
                "performBackKey" -> handlePerformBackKey(result)
                "performHomeKey" -> handlePerformHomeKey(result)
                "sendKeyEvent" -> handleSendKeyEvent(call, result)

                // UI interaction
                "findAndClick" -> handleFindAndClick(call, result)
                "performBack" -> handlePerformBack(result)
                // Removed text input methods



                // App management
                "openApp" -> handleOpenApp(call, result)
                "openAppByName" -> handleOpenAppByName(call, result)
                "getLaunchableApps" -> handleGetLaunchableApps(result)
                "getInstalledApps" -> handleGetInstalledApps(result)
                "findMatchingApps" -> handleFindMatchingApps(call, result)
                "searchApps" -> handleSearchApps(call, result)
                "getBestMatchingApp" -> handleGetBestMatchingApp(call, result)

                // Navigation
                "performHome" -> handlePerformHome(result)
                "performRecents" -> handlePerformRecents(result)
                "openSettings" -> handleOpenSettings(result)
                "openNotifications" -> handleOpenNotifications(result)
                "openQuickSettings" -> handleOpenQuickSettings(result)

                else -> {
                    Log.w(TAG, "⚠️ Unknown tool: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling tool call: ${e.message}", e)
            result.error("TOOL_ERROR", e.message, null)
        }
    }

    // ==================== SCREEN CAPTURE & ANALYSIS ====================

    private fun handleTakeScreenshot(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val screenshot = toolActivityManager.takeScreenshot()
                result.success(screenshot)
                Log.d(TAG, "✅ Screenshot captured")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Screenshot error: ${e.message}", e)
                result.error("SCREENSHOT_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformOcr(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val screenshotBase64 = call.argument<String>("screenshot")
                if (screenshotBase64.isNullOrEmpty()) {
                    Log.w(TAG, "⚠️ performOcr called without screenshot data")
                    result.success(mapOf(
                        "success" to false,
                        "error" to "Missing screenshot"
                    ))
                    return@launch
                }

                val ocr = OcrProcessor()
                val ocrResult = ocr.extractTextFromBase64Screenshot(screenshotBase64)
                if (ocrResult.success) {
                    Log.d(TAG, "✅ OCR success: ${ocrResult.fullText.length} chars, ${ocrResult.blocks.size} blocks")
                } else {
                    Log.e(TAG, "❌ OCR failed: ${ocrResult.error}")
                }

                result.success(mapOf(
                        "success" to ocrResult.success,
                        "text" to ocrResult.fullText,
                        "blocks" to ocrResult.blocks,
                        "imageWidth" to ocrResult.imageWidth,
                        "imageHeight" to ocrResult.imageHeight,
                        "error" to ocrResult.error
                    ))
            } catch (e: Exception) {
                Log.e(TAG, "❌ performOcr error: ${e.message}", e)
                result.success(mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error")
                ))
            }
        }
    }

    private fun handleGetAccessibilityTree(result: MethodChannel.Result) {
        try {
            if (!isAccessibilityServiceEnabled()) {
                Log.w(TAG, "⚠️ Accessibility service not enabled")
                result.success(emptyList<Map<String, Any?>>())
                return
            }

            val service = MyAccessibilityService.instance
            if (service == null) {
                Log.w(TAG, "⚠️ Accessibility service instance not available")
                result.success(emptyList<Map<String, Any?>>())
                return
            }

            val elements = getAccessibilityElements()

            // Ensure we always return a valid list
            val safeElements = elements ?: emptyList()

            result.success(safeElements)
            Log.d(TAG, "✅ Accessibility tree retrieved: ${safeElements.size} elements")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Accessibility tree error: ${e.message}", e)
            // Return empty list instead of error to prevent type casting issues
            result.success(emptyList<Map<String, Any?>>())
        }
    }

    private fun handleAnalyzeScreen(result: MethodChannel.Result) {
        try {
            val service = MyAccessibilityService.instance
            if (service != null) {
                val packageName = service.getCurrentAppPackage()
                val screenElements = service.getScreenElements()
                val analysis =
                        mapOf(
                                "screen_elements" to screenElements,
                                "current_app" to
                                        mapOf(
                                                "packageName" to (packageName ?: ""),
                                                "appName" to (packageName ?: ""),
                                                "timestamp" to System.currentTimeMillis()
                                        ),
                                "total_elements" to screenElements.size,
                                "timestamp" to System.currentTimeMillis()
                        )
                result.success(analysis)
                Log.d(TAG, "✅ Screen analyzed successfully")
            } else {
                Log.w(TAG, "⚠️ Accessibility service not available for screen analysis")
                result.success(
                        mapOf(
                                "screen_elements" to emptyList<Map<String, Any?>>(),
                                "current_app" to
                                        mapOf(
                                                "packageName" to "",
                                                "appName" to "",
                                                "timestamp" to System.currentTimeMillis()
                                        ),
                                "total_elements" to 0,
                                "timestamp" to System.currentTimeMillis()
                        )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Screen analysis error: ${e.message}", e)
            result.success(
                    mapOf(
                            "screen_elements" to emptyList<Map<String, Any?>>(),
                            "current_app" to
                                    mapOf(
                                            "packageName" to "",
                                            "appName" to "",
                                            "timestamp" to System.currentTimeMillis()
                                    ),
                            "total_elements" to 0,
                            "timestamp" to System.currentTimeMillis()
                    )
            )
        }
    }

    private fun handleGetCurrentApp(result: MethodChannel.Result) {
        try {
            val service = MyAccessibilityService.instance
            if (service != null) {
                val packageName = service.getCurrentAppPackage()
                val appInfo =
                        mapOf(
                                "packageName" to (packageName ?: ""),
                                "appName" to (packageName ?: ""),
                                "timestamp" to System.currentTimeMillis()
                        )
                result.success(appInfo)
                Log.d(TAG, "✅ Current app: $packageName")
            } else {
                Log.w(TAG, "⚠️ Accessibility service not available for getCurrentApp")
                result.success(
                        mapOf(
                                "packageName" to "",
                                "appName" to "",
                                "timestamp" to System.currentTimeMillis()
                        )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get current app error: ${e.message}", e)
            result.success(
                    mapOf(
                            "packageName" to "",
                            "appName" to "",
                            "timestamp" to System.currentTimeMillis()
                    )
            )
        }
    }

    // ==================== TOUCH OPERATIONS ====================

    private fun handlePerformTap(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val x = convertToFloat(call.argument<Any>("x"))
                val y = convertToFloat(call.argument<Any>("y"))
                val success = toolActivityManager.performTap(x, y)
                result.success(success)
                Log.d(
                        TAG,
                        if (success) "✅ Tap performed at ($x, $y)" else "❌ Tap failed at ($x, $y)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Tap error: ${e.message}", e)
                result.error("TAP_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformLongPress(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val x = convertToFloat(call.argument<Any>("x"))
                val y = convertToFloat(call.argument<Any>("y"))
                val duration = convertToLong(call.argument<Any>("duration"))
                val success = toolActivityManager.performLongPress(x, y, duration)
                result.success(success)
                Log.d(TAG, if (success) "✅ Long press performed" else "❌ Long press failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Long press error: ${e.message}", e)
                result.error("LONGPRESS_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformDoubleClick(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val x = convertToFloat(call.argument<Any>("x"))
                val y = convertToFloat(call.argument<Any>("y"))
                val success = toolActivityManager.performDoubleClick(x, y)
                result.success(success)
                Log.d(TAG, if (success) "✅ Double click performed" else "❌ Double click failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Double click error: ${e.message}", e)
                result.error("DOUBLECLICK_ERROR", e.message, null)
            }
        }
    }

    // ==================== GESTURE OPERATIONS ====================

    private fun handlePerformSwipe(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val startX = convertToFloat(call.argument<Any>("startX"))
                val startY = convertToFloat(call.argument<Any>("startY"))
                val endX = convertToFloat(call.argument<Any>("endX"))
                val endY = convertToFloat(call.argument<Any>("endY"))
                val duration = convertToLong(call.argument<Any>("duration"), 300L)
                val success = toolActivityManager.performSwipe(startX, startY, endX, endY, duration)
                result.success(success)
                Log.d(TAG, if (success) "✅ Swipe performed" else "❌ Swipe failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Swipe error: ${e.message}", e)
                result.error("SWIPE_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformScroll(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val direction = call.argument<String>("direction") ?: "down"
                val success = toolActivityManager.performScroll(direction)
                result.success(success)
                Log.d(
                        TAG,
                        if (success) "✅ Scroll $direction performed"
                        else "❌ Scroll $direction failed"
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Scroll error: ${e.message}", e)
                result.error("SCROLL_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformPinch(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val centerX = convertToFloat(call.argument<Any>("centerX"))
                val centerY = convertToFloat(call.argument<Any>("centerY"))
                val scale = convertToFloat(call.argument<Any>("scale"), 1f)

                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.performPinch(centerX, centerY, 100f, 100f * scale)
                    result.success(success)
                    Log.d(TAG, if (success) "✅ Pinch performed" else "❌ Pinch failed")
                } else {
                    result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Pinch error: ${e.message}", e)
                result.error("PINCH_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformZoomIn(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.performPinch(500f, 500f, 100f, 200f)
                    result.success(success)
                    Log.d(TAG, if (success) "✅ Zoom in performed" else "❌ Zoom in failed")
                } else {
                    result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Zoom in error: ${e.message}", e)
                result.error("ZOOMIN_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformZoomOut(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.performPinch(500f, 500f, 200f, 100f)
                    result.success(success)
                    Log.d(TAG, if (success) "✅ Zoom out performed" else "❌ Zoom out failed")
                } else {
                    result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Zoom out error: ${e.message}", e)
                result.error("ZOOMOUT_ERROR", e.message, null)
            }
        }
    }

    // ==================== TEXT OPERATIONS ====================

    private fun handleTypeText(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val text = call.argument<String>("text") ?: ""
                val success = toolActivityManager.performAdvancedType(text)
                result.success(success)
                Log.d(TAG, if (success) "✅ Text typed: '$text'" else "❌ Text typing failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Type text error: ${e.message}", e)
                result.error("TYPE_ERROR", e.message, null)
            }
        }
    }

    private fun handleAdvancedTypeText(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val text = call.argument<String>("text") ?: ""
                val clearFirst = call.argument<Boolean>("clearFirst") ?: false
                val delayMs = call.argument<Int>("delayMs") ?: 0
                val ocrBounds = call.argument<Map<String, Any>>("ocrBounds")
                val expectedText = call.argument<String>("expectedText")
                
                Log.d(TAG, "🔧 Advanced type text: '$text', clearFirst: $clearFirst, delayMs: $delayMs, ocrBounds: $ocrBounds, expectedText: $expectedText")
                
                // Clear field first if requested
                if (clearFirst) {
                    toolActivityManager.clearTextField()
                    delay(100) // Small delay after clearing
                }
                
                // Add delay if specified
                if (delayMs > 0) {
                    delay(delayMs.toLong())
                }
                
                val success = toolActivityManager.performAdvancedType(text, ocrBounds, expectedText)
                result.success(success)
                Log.d(TAG, if (success) "✅ Advanced text typed: '$text'" else "❌ Advanced text typing failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Advanced type text error: ${e.message}", e)
                result.error("ADVANCED_TYPE_ERROR", e.message, null)
            }
        }
    }

    private fun handleClearText(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.clearTextField()
                result.success(success)
                Log.d(TAG, if (success) "✅ Text cleared" else "❌ Text clear failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Clear text error: ${e.message}", e)
                result.error("CLEAR_ERROR", e.message, null)
            }
        }
    }

    private fun handleNonTapTextInput(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val text = call.argument<String>("text") ?: ""
                val fieldId = call.argument<String>("fieldId")
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.injectTextWithoutTap(text, fieldId)
                    result.success(success)
                    Log.d(TAG, if (success) "✅ Non-tap text input successful: '$text'" else "❌ Non-tap text input failed: '$text'")
                } else {
                    result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Non-tap text input error: ${e.message}", e)
                result.error("NON_TAP_INPUT_ERROR", e.message, null)
            }
        }
    }

    private fun handleGetFocusedInputInfo(result: MethodChannel.Result) {
        try {
            val service = MyAccessibilityService.instance
            if (service != null) {
                val focusedInfo = service.getFocusedInputInfo()
                result.success(focusedInfo)
                Log.d(TAG, "✅ Focused input info retrieved: $focusedInfo")
            } else {
                result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get focused input info error: ${e.message}", e)
            result.error("FOCUSED_INPUT_ERROR", e.message, null)
        }
    }

    private fun handleGetAllInputFields(result: MethodChannel.Result) {
        try {
            val service = MyAccessibilityService.instance
            if (service != null) {
                val inputFields = service.getAllInputFields()
                result.success(inputFields)
                Log.d(TAG, "✅ All input fields retrieved: ${inputFields.size} fields found")
            } else {
                result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get all input fields error: ${e.message}", e)
            result.error("INPUT_FIELDS_ERROR", e.message, null)
        }
    }

    // Removed handleSelectAllText, handleCopyText, and handlePasteText methods

    // Removed handleReplaceText method

    private fun handleTypeTextSlowly(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val text = call.argument<String>("text") ?: ""
                val delayMs = convertToLong(call.argument<Any>("delayMs"), 50L)
                val success = toolActivityManager.typeTextSlowly(text, delayMs)
                result.success(success)
                Log.d(
                        TAG,
                        if (success) "✅ Type text slowly performed" else "❌ Type text slowly failed"
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Type text slowly error: ${e.message}", e)
                result.error("TYPESLOW_ERROR", e.message, null)
            }
        }
    }

    private fun handleInsertText(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val text = call.argument<String>("text") ?: ""
                val success = toolActivityManager.performAdvancedType(text)
                result.success(success)
                Log.d(TAG, if (success) "✅ Insert text performed" else "❌ Insert text failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Insert text error: ${e.message}", e)
                result.error("INSERT_ERROR", e.message, null)
            }
        }
    }

    // ==================== KEYEVENT SUPPORT ====================

    private fun handlePerformEnter(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.sendKeyEvent(android.view.KeyEvent.KEYCODE_ENTER)
                result.success(success)
                Log.d(TAG, if (success) "✅ Enter key sent" else "❌ Enter key failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Enter key error: ${e.message}", e)
                result.error("ENTER_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformBackspace(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.sendKeyEvent(android.view.KeyEvent.KEYCODE_DEL)
                result.success(success)
                Log.d(TAG, if (success) "✅ Backspace key sent" else "❌ Backspace key failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Backspace key error: ${e.message}", e)
                result.error("BACKSPACE_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformDelete(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success =
                        toolActivityManager.sendKeyEvent(android.view.KeyEvent.KEYCODE_FORWARD_DEL)
                result.success(success)
                Log.d(TAG, if (success) "✅ Delete key sent" else "❌ Delete key failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Delete key error: ${e.message}", e)
                result.error("DELETE_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformBackKey(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.sendKeyEvent(android.view.KeyEvent.KEYCODE_BACK)
                result.success(success)
                Log.d(TAG, if (success) "✅ Back key sent" else "❌ Back key failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Back key error: ${e.message}", e)
                result.error("BACK_KEY_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformHomeKey(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.sendKeyEvent(android.view.KeyEvent.KEYCODE_HOME)
                result.success(success)
                Log.d(TAG, if (success) "✅ Home key sent" else "❌ Home key failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Home key error: ${e.message}", e)
                result.error("HOME_KEY_ERROR", e.message, null)
            }
        }
    }

    private fun handleSendKeyEvent(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val keyCode = call.argument<Int>("keyCode") ?: android.view.KeyEvent.KEYCODE_UNKNOWN
                val success = toolActivityManager.sendKeyEvent(keyCode)
                result.success(success)
                Log.d(
                        TAG,
                        if (success) "✅ Key event sent: $keyCode"
                        else "❌ Key event failed: $keyCode"
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Key event error: ${e.message}", e)
                result.error("KEYEVENT_ERROR", e.message, null)
            }
        }
    }

    // Removed handleDetectCursorLocation, handleValidateInputFieldPosition, and handleFocusInputField methods

    // ==================== UI INTERACTION ====================

    private fun handleFindAndClick(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val text = call.argument<String>("text") ?: ""
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.findAndClick(text)
                    result.success(success)
                    Log.d(
                            TAG,
                            if (success) "✅ Found and clicked: '$text'"
                            else "❌ Find and click failed: '$text'"
                    )
                } else {
                    result.error("SERVICE_UNAVAILABLE", "Accessibility service not available", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Find and click error: ${e.message}", e)
                result.error("FINDCLICK_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformBack(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.performBack()
                result.success(success)
                Log.d(TAG, if (success) "✅ Back performed" else "❌ Back failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Back error: ${e.message}", e)
                result.error("BACK_ERROR", e.message, null)
            }
        }
    }

    // ==================== APP MANAGEMENT ====================

    private fun handleOpenApp(call: MethodCall, result: MethodChannel.Result) {
        try {
            val packageName = call.argument<String>("package") ?: ""
            
            if (isAppServiceBound && appManagementService != null) {
                appManagementService!!.openApp(packageName) { success ->
                    result.success(success)
                }
            } else {
                // Fallback to direct method if service not available
                coroutineScope.launch {
                    try {
                        val success = toolActivityManager.openApp(packageName)
                        result.success(success)
                        Log.d(
                            TAG,
                            if (success) "✅ App opened (fallback): $packageName"
                            else "❌ App open failed (fallback): $packageName"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Open app fallback error: ${e.message}", e)
                        // Send error broadcast to hide overlay
                        val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
                        val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"Failed to open app: ${e.message}\"}"
                        intent.putExtra("result", resultJson)
                        intent.setPackage(packageName)
                        sendBroadcast(intent)
                        Log.d(TAG, "App management error broadcast sent to hide overlay")
                        result.error("OPENAPP_ERROR", e.message, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Open app error: ${e.message}", e)
            // Send error broadcast to hide overlay
            val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
            val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"Failed to open app: ${e.message}\"}"
            intent.putExtra("result", resultJson)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "App management error broadcast sent to hide overlay")
            result.error("OPENAPP_ERROR", e.message, null)
        }
    }

    private fun handleOpenAppByName(call: MethodCall, result: MethodChannel.Result) {
        try {
            val appName = call.argument<String>("app_name") ?: ""
            
            if (isAppServiceBound && appManagementService != null) {
                appManagementService!!.openAppByName(appName) { success ->
                    result.success(success)
                }
            } else {
                // Fallback to direct method if service not available
                try {
                    val success = toolActivityManager.openAppByName(appName)
                    result.success(success)
                    Log.d(
                        TAG,
                        if (success) "✅ App opened by name (fallback): $appName"
                        else "❌ App open by name failed (fallback): $appName"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Open app by name fallback error: ${e.message}", e)
                    // Send error broadcast to hide overlay
                    val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
                    val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"Failed to open app by name: ${e.message}\"}"
                    intent.putExtra("result", resultJson)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    Log.d(TAG, "App management error broadcast sent to hide overlay")
                    result.error("OPENAPPNAME_ERROR", e.message, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Open app by name error: ${e.message}", e)
            // Send error broadcast to hide overlay
            val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
            val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"Failed to open app by name: ${e.message}\"}"
            intent.putExtra("result", resultJson)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "App management error broadcast sent to hide overlay")
            result.error("OPENAPPNAME_ERROR", e.message, null)
        }
    }

    private fun handleGetLaunchableApps(result: MethodChannel.Result) {
        try {
            if (isAppServiceBound && appManagementService != null) {
                appManagementService!!.getLaunchableApps { apps ->
                    result.success(apps)
                }
            } else {
                // Fallback to direct method if service not available
                val apps = toolActivityManager.getLaunchableApps()
                result.success(apps)
                Log.d(TAG, "✅ Retrieved ${apps.size} launchable apps (fallback)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get launchable apps error: ${e.message}", e)
            result.error("APPS_ERROR", e.message, null)
        }
    }

    private fun handleGetInstalledApps(result: MethodChannel.Result) {
        try {
            if (isAppServiceBound && appManagementService != null) {
                appManagementService!!.getInstalledApps { apps ->
                    result.success(apps)
                }
            } else {
                // Fallback to direct method if service not available
                val apps = toolActivityManager.getInstalledApps()
                result.success(apps)
                Log.d(TAG, "✅ Retrieved ${apps.size} installed apps (fallback)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get installed apps error: ${e.message}", e)
            result.error("INSTALLEDAPPS_ERROR", e.message, null)
        }
    }

    private fun handleFindMatchingApps(call: MethodCall, result: MethodChannel.Result) {
        try {
            val appName = call.argument<String>("app_name") ?: ""
            if (isAppServiceBound && appManagementService != null) {
                appManagementService!!.findMatchingApps(appName) { apps ->
                    result.success(apps)
                }
            } else {
                // Fallback to direct method if service not available
                val apps = toolActivityManager.findMatchingApps(appName)
                result.success(apps)
                Log.d(TAG, "✅ Found ${apps.size} matching apps for: $appName (fallback)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Find matching apps error: ${e.message}", e)
            result.error("FINDAPPS_ERROR", e.message, null)
        }
    }

    private fun handleSearchApps(call: MethodCall, result: MethodChannel.Result) {
        try {
            val keyword = call.argument<String>("keyword") ?: ""
            if (isAppServiceBound && appManagementService != null) {
                appManagementService!!.searchApps(keyword) { apps ->
                    result.success(apps)
                }
            } else {
                // Fallback to direct method if service not available
                val apps = toolActivityManager.searchApps(keyword)
                result.success(apps)
                Log.d(TAG, "✅ Found ${apps.size} apps for keyword: $keyword (fallback)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Search apps error: ${e.message}", e)
            result.error("SEARCHAPPS_ERROR", e.message, null)
        }
    }

    private fun handleGetBestMatchingApp(call: MethodCall, result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val appName = call.argument<String>("app_name") ?: ""
                if (isAppServiceBound && appManagementService != null) {
                    appManagementService!!.getBestMatchingApp(appName) { app ->
                        result.success(app)
                    }
                } else {
                    // Fallback to direct method if service not available
                    val app = toolActivityManager.getBestMatchingApp(appName)
                    result.success(app)
                    Log.d(TAG, "✅ Best matching app found for: $appName (fallback)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Get best matching app error: ${e.message}", e)
                result.error("BESTAPP_ERROR", e.message, null)
            }
        }
    }

    // ==================== NAVIGATION ====================

    private fun handlePerformHome(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.performHome()
                result.success(success)
                Log.d(TAG, if (success) "✅ Home performed" else "❌ Home failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Home error: ${e.message}", e)
                result.error("HOME_ERROR", e.message, null)
            }
        }
    }

    private fun handlePerformRecents(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.performRecents()
                result.success(success)
                Log.d(TAG, if (success) "✅ Recents performed" else "❌ Recents failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Recents error: ${e.message}", e)
                result.error("RECENTS_ERROR", e.message, null)
            }
        }
    }

    private fun handleOpenSettings(result: MethodChannel.Result) {
        try {
            val success = toolActivityManager.openSettings()
            result.success(success)
            Log.d(TAG, if (success) "✅ Settings opened" else "❌ Settings failed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Settings error: ${e.message}", e)
            result.error("SETTINGS_ERROR", e.message, null)
        }
    }

    private fun handleOpenNotifications(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.openNotifications()
                result.success(success)
                Log.d(TAG, if (success) "✅ Notifications opened" else "❌ Notifications failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Notifications error: ${e.message}", e)
                result.error("NOTIFICATIONS_ERROR", e.message, null)
            }
        }
    }

    private fun handleOpenQuickSettings(result: MethodChannel.Result) {
        coroutineScope.launch {
            try {
                val success = toolActivityManager.openQuickSettings()
                result.success(success)
                Log.d(TAG, if (success) "✅ Quick settings opened" else "❌ Quick settings failed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Quick settings error: ${e.message}", e)
                result.error("QUICKSETTINGS_ERROR", e.message, null)
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private fun getAccessibilityElements(): List<Map<String, Any?>> {
        return try {
            val service = MyAccessibilityService.instance
            if (service == null) {
                Log.w(TAG, "⚠️ AccessibilityService instance is null")
                return emptyList()
            }

            val elements = service.getScreenElements()
            Log.d(TAG, "🔍 Retrieved ${elements.size} accessibility elements")
            elements
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting accessibility elements: ${e.message}", e)
            emptyList()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices =
                Settings.Secure.getString(
                        contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
        val serviceName = ComponentName(this, MyAccessibilityService::class.java).flattenToString()
        return enabledServices?.contains(serviceName) == true
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Permission not required on older versions
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        coroutineScope.cancel()
        
        // Cleanup voice agent
        try {
            voiceAgentCoordinator?.cleanup()
            Log.d(TAG, "🎤 Voice agent cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up voice agent: ${e.message}")
        }
        
        // Unregister voice command receiver
        try {
            voiceCommandReceiver?.let {
                unregisterReceiver(it)
                voiceCommandReceiver = null
                Log.d(TAG, "🎤 Voice command receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering voice command receiver: ${e.message}")
        }
        
        // Unbind from AppManagementService
        if (isAppServiceBound) {
            try {
                unbindService(appServiceConnection)
                isAppServiceBound = false
                Log.d(TAG, "📱 AppManagementService unbound")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error unbinding AppManagementService: ${e.message}")
            }
        }
        
        // Stop and unbind from AutomationForegroundService
        if (isAutomationServiceBound) {
            try {
                // Stop any running automation before unbinding
                automationForegroundService?.stopAutomationTask()
                
                unbindService(automationServiceConnection)
                isAutomationServiceBound = false
                Log.d(TAG, "🤖 AutomationForegroundService stopped and unbound")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping/unbinding AutomationForegroundService: ${e.message}")
            }
        }
        
        // Also stop regular automation service
        try {
            automationService.stopAutomation()
            Log.d(TAG, "🤖 Regular automation service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping regular automation service: ${e.message}")
        }
        
        // Clean up notification channels to ensure overlay closes
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Send broadcast to ensure voice overlay closes
            val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
            val resultJson = "{\"task_completed\":true,\"success\":true,\"app_destroyed\":true}"
            intent.putExtra("result", resultJson)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "🔔 Notification cleanup and overlay close broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up notifications: ${e.message}")
        }
        
        Log.d(TAG, "🔴 AI Agent MainActivity destroyed")
    }

    private fun registerVoiceCommandReceiver() {
        try {
            Log.d(TAG, "🎤 Starting voice command receiver registration...")
            voiceCommandReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(TAG, "🎤 Broadcast received: ${intent?.action}")
                    if (intent?.action == "com.vibeagent.dude.VOICE_COMMAND") {
                        val command = intent.getStringExtra("command")
                        Log.d(TAG, "🎤 Voice command extracted: '$command'")
                        if (!command.isNullOrEmpty()) {
                            Log.d(TAG, "🎤 Received voice command: $command")
                            handleVoiceCommand(command)
                        } else {
                            Log.w(TAG, "⚠️ Voice command is null or empty")
                        }
                    } else {
                        Log.d(TAG, "🎤 Ignoring broadcast with action: ${intent?.action}")
                    }
                }
            }
            
            val filter = IntentFilter("com.vibeagent.dude.VOICE_COMMAND")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(voiceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(voiceCommandReceiver, filter)
            }
            Log.d(TAG, "✅ Voice command receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering voice command receiver: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleVoiceCommand(command: String) {
        try {
            Log.d(TAG, "🎤 Processing voice command: '$command'")
            if (automationChannel == null) {
                Log.e(TAG, "❌ Automation channel is null!")
                return
            }
            
            Log.d(TAG, "🎤 Invoking method channel with executeUserTask...")
            // Send command to automation service via method channel
            automationChannel?.invokeMethod("executeUserTask", mapOf("user_task" to command))
            Log.d(TAG, "✅ Voice command sent to automation service: $command")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling voice command: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun notifyVoiceServiceAutomationComplete() {
        Log.d(TAG, "Notifying voice service of automation completion")
        val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
        val resultJson = "{\"task_completed\":true,\"success\":true}"
        intent.putExtra("result", resultJson)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }
    
    private fun notifyVoiceServiceAutomationError(error: String) {
        Log.d(TAG, "Notifying voice service of automation error: $error")
        val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
        val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"$error\"}"
        intent.putExtra("result", resultJson)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    // ==================== UTILITY FUNCTIONS ====================

    /** Robustly converts various numeric types to Float for coordinate values */
    private fun convertToFloat(value: Any?, defaultValue: Float = 0f): Float {
        return when (value) {
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is Float -> value
            is String -> value.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    /** Robustly converts various numeric types to Long for duration values */
    private fun convertToLong(value: Any?, defaultValue: Long = 500L): Long {
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    /** Robustly converts various numeric types to Int for general integer values */
    private fun convertToInt(value: Any?): Int {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    // ==================== INPUT CHIP OPERATIONS ====================


}
