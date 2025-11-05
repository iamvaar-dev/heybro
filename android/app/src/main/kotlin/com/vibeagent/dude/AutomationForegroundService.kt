package com.vibeagent.dude

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import io.flutter.plugin.common.MethodChannel

class AutomationForegroundService : Service() {

    private val binder = AutomationBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isAutomating = false
    private var currentTask: String? = null
    private var automationJob: Job? = null
    
    // Method channel for communication with Flutter
    private var methodChannel: MethodChannel? = null

    companion object {
        private const val TAG = "AutomationForegroundService"
        const val NOTIFICATION_CHANNEL_ID = "AutomationChannel"
        const val NOTIFICATION_ID = 5
        var isRunning = false
        
        // Actions
        const val ACTION_START_AUTOMATION = "START_AUTOMATION"
        const val ACTION_STOP_AUTOMATION = "STOP_AUTOMATION"
        const val EXTRA_TASK = "task"
    }

    inner class AutomationBinder : Binder() {
        fun getService(): AutomationForegroundService = this@AutomationForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutomationForegroundService created")
        createNotificationChannel()
        isRunning = true
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "AutomationForegroundService bound")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AutomationForegroundService started")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Handle automation actions
        when (intent?.action) {
            ACTION_START_AUTOMATION -> {
                val task = intent.getStringExtra(EXTRA_TASK)
                if (task != null) {
                    startAutomationTask(task)
                }
            }
            ACTION_STOP_AUTOMATION -> {
                stopAutomationTask()
            }
        }
        
        return START_STICKY // Keep service running
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AutomationForegroundService destroyed")
        stopAutomationTask()
        serviceScope.cancel()
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Automation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles automation tasks in background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isAutomating) {
            "Running: ${currentTask ?: "Unknown task"}"
        } else {
            "Ready for automation"
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Agent Automation")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    fun setMethodChannel(channel: MethodChannel) {
        this.methodChannel = channel
        Log.d(TAG, "Method channel set for automation service")
    }

    fun startAutomationTask(task: String) {
        if (isAutomating) {
            Log.w(TAG, "Automation already running, stopping current task first")
            stopAutomationTask()
        }

        currentTask = task
        isAutomating = true
        updateNotification()
        
        Log.d(TAG, "Starting automation task: $task")
        
        automationJob = serviceScope.launch {
            try {
                executeAutomationTask(task)
            } catch (e: Exception) {
                Log.e(TAG, "Automation task failed", e)
                notifyAutomationError("Automation failed: ${e.message}")
            } finally {
                isAutomating = false
                currentTask = null
                updateNotification()
            }
        }
    }

    fun stopAutomationTask() {
        if (isAutomating) {
            Log.d(TAG, "Stopping automation task")
            automationJob?.cancel()
            isAutomating = false
            currentTask = null
            updateNotification()
            notifyAutomationStopped()
        }
    }

    private suspend fun executeAutomationTask(task: String) {
        Log.d(TAG, "Executing automation task: $task")
        
        // Check if we have accessibility service
        if (!isAccessibilityServiceEnabled()) {
            throw Exception("Accessibility service not enabled")
        }
        
        // Notify Flutter layer to start automation
        withContext(Dispatchers.Main) {
            methodChannel?.invokeMethod("startBackgroundAutomation", mapOf(
                "task" to task,
                "serviceMode" to true
            ))
        }
        
        // Monitor automation progress
        var stepCount = 0
        val maxSteps = 100 // Prevent infinite loops
        
        while (isAutomating && stepCount < maxSteps) {
            delay(2000) // Check every 2 seconds
            stepCount++
            
            // Update notification with progress
            updateNotification()
            
            // Check if automation is still active via Flutter
            val isActive = withContext(Dispatchers.Main) {
                try {
                    methodChannel?.invokeMethod("isAutomationActive", null)
                    true // Assume active if no exception
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check automation status", e)
                    false
                }
            }
            
            if (!isActive) {
                Log.d(TAG, "Automation completed or stopped by Flutter layer")
                break
            }
        }
        
        if (stepCount >= maxSteps) {
            Log.w(TAG, "Automation stopped due to step limit")
            notifyAutomationError("Automation stopped: Maximum steps reached")
        } else {
            Log.d(TAG, "Automation task completed successfully")
            notifyAutomationComplete()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return MyAccessibilityService.instance != null
    }

    private fun notifyAutomationComplete() {
        serviceScope.launch(Dispatchers.Main) {
            methodChannel?.invokeMethod("onAutomationComplete", null)
        }
        
        // Send broadcast intent for VoiceAgentService with success result
        val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
        val resultJson = "{\"task_completed\":true,\"success\":true}"
        intent.putExtra("result", resultJson)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Log.d(TAG, "Automation complete broadcast sent with result: $resultJson")
    }

    private fun notifyAutomationError(error: String) {
        serviceScope.launch(Dispatchers.Main) {
            methodChannel?.invokeMethod("onAutomationError", mapOf("error" to error))
        }
        
        // Send broadcast intent for VoiceAgentService with failure result
        val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
        val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"$error\"}"
        intent.putExtra("result", resultJson)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Log.d(TAG, "Automation error broadcast sent with result: $resultJson")
    }

    private fun notifyAutomationStopped() {
        serviceScope.launch(Dispatchers.Main) {
            methodChannel?.invokeMethod("onAutomationStopped", null)
        }
        
        // Send broadcast intent for VoiceAgentService (treat as completion)
        val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
        val resultJson = "{\"task_completed\":true,\"success\":true,\"stopped\":true}"
        intent.putExtra("result", resultJson)
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Log.d(TAG, "Automation stopped broadcast sent with result: $resultJson")
    }

    // Public methods for external control
    fun isAutomationRunning(): Boolean = isAutomating
    
    fun getCurrentTask(): String? = currentTask
    
    fun getAutomationStatus(): Map<String, Any> {
        return mapOf(
            "isRunning" to isAutomating,
            "currentTask" to (currentTask ?: ""),
            "serviceRunning" to isRunning
        )
    }
}