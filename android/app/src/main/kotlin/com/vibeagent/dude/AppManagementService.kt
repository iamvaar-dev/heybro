package com.vibeagent.dude

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.provider.Settings
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppManagementService : Service() {

    private val binder = AppManagementBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var toolActivityManager: ToolActivityManager

    companion object {
        private const val TAG = "AppManagementService"
        const val NOTIFICATION_CHANNEL_ID = "AppManagementChannel"
        const val NOTIFICATION_ID = 4
        var isRunning = false
    }

    inner class AppManagementBinder : Binder() {
        fun getService(): AppManagementService = this@AppManagementService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppManagementService created")
        
        // Initialize ToolActivityManager
        toolActivityManager = ToolActivityManager(this)
        
        createNotificationChannel()
        isRunning = true
        
        // Start floating overlay service if permission is granted
        startFloatingOverlayIfPermitted()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AppManagementService started")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_STICKY // Keep service running
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Open an app by its package name
     * Uses accessibility service if available for background launching
     */
    fun openApp(packageName: String, callback: (Boolean) -> Unit) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Opening app: $packageName")
                
                // Store this request for floating overlay quick access
                storeAppRequestInOverlay(packageName, null)
                
                // Try accessibility service first (works in background)
                val accessibilityService = MyAccessibilityService.instance
                val success = if (accessibilityService != null) {
                    Log.d(TAG, "Using accessibility service to launch app: $packageName")
                    accessibilityService.launchApp(packageName)
                } else {
                    Log.d(TAG, "Accessibility service not available, using ToolActivityManager")
                    toolActivityManager.openApp(packageName)
                }
                
                Log.d(
                    TAG,
                    if (success) "✅ App opened: $packageName"
                    else "❌ App open failed: $packageName"
                )
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Open app error: ${e.message}", e)
                // Send error broadcast to hide overlay
                val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
                val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"Failed to open app: ${e.message}\"}"
                intent.putExtra("result", resultJson)
                intent.setPackage(packageName)
                sendBroadcast(intent)
                Log.d(TAG, "App management error broadcast sent to hide overlay")
                callback(false)
            }
        }
    }

    /**
     * Open an app by its name
     * Uses accessibility service if available for background launching
     */
    fun openAppByName(appName: String, callback: (Boolean) -> Unit) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Opening app by name: $appName")
                
                // Store this request for floating overlay quick access
                storeAppRequestInOverlay(null, appName)
                
                // Try accessibility service first (works in background)
                val accessibilityService = MyAccessibilityService.instance
                val success = if (accessibilityService != null) {
                    Log.d(TAG, "Using accessibility service to launch app by name: $appName")
                    accessibilityService.launchAppByName(appName)
                } else {
                    Log.d(TAG, "Accessibility service not available, using ToolActivityManager")
                    toolActivityManager.openAppByName(appName)
                }
                
                Log.d(
                    TAG,
                    if (success) "✅ App opened by name: $appName"
                    else "❌ App open by name failed: $appName"
                )
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Open app by name error: ${e.message}", e)
                // Send error broadcast to hide overlay
                val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
                val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"Failed to open app by name: ${e.message}\"}"
                intent.putExtra("result", resultJson)
                intent.setPackage(packageName)
                sendBroadcast(intent)
                Log.d(TAG, "App management error broadcast sent to hide overlay")
                callback(false)
            }
        }
    }

    /**
     * Get launchable apps
     */
    fun getLaunchableApps(callback: (List<Map<String, Any?>>) -> Unit) {
        serviceScope.launch {
            try {
                val apps = toolActivityManager.getLaunchableApps()
                callback(apps)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Get launchable apps error: ${e.message}", e)
                callback(emptyList())
            }
        }
    }

    /**
     * Get installed apps
     */
    fun getInstalledApps(callback: (List<Map<String, Any?>>) -> Unit) {
        serviceScope.launch {
            try {
                val apps = toolActivityManager.getInstalledApps()
                callback(apps)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Get installed apps error: ${e.message}", e)
                callback(emptyList())
            }
        }
    }

    /**
     * Find matching apps by name
     */
    fun findMatchingApps(appName: String, callback: (List<Map<String, Any?>>) -> Unit) {
        serviceScope.launch {
            try {
                val apps = toolActivityManager.findMatchingApps(appName)
                callback(apps)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Find matching apps error: ${e.message}", e)
                callback(emptyList())
            }
        }
    }

    /**
     * Search apps by query
     */
    fun searchApps(query: String, callback: (List<Map<String, Any?>>) -> Unit) {
        serviceScope.launch {
            try {
                val apps = toolActivityManager.searchApps(query)
                callback(apps)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Search apps error: ${e.message}", e)
                callback(emptyList())
            }
        }
    }

    /**
     * Get best matching app by name
     */
    fun getBestMatchingApp(appName: String, callback: (Map<String, Any?>?) -> Unit) {
        serviceScope.launch {
            try {
                val app = toolActivityManager.getBestMatchingApp(appName)
                callback(app)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Get best matching app error: ${e.message}", e)
                callback(null)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Management Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles app opening operations in the background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Management Service")
            .setContentText("Ready to handle app operations")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Start floating overlay service if permission is granted
     */
    private fun startFloatingOverlayIfPermitted() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "Starting FloatingOverlayService")
                    val overlayIntent = Intent(this, FloatingOverlayService::class.java)
                    startService(overlayIntent)
                } else {
                    Log.w(TAG, "Overlay permission not granted, cannot start FloatingOverlayService")
                }
            } else {
                // For API < 23, permission is granted by default
                Log.d(TAG, "Starting FloatingOverlayService (API < 23)")
                val overlayIntent = Intent(this, FloatingOverlayService::class.java)
                startService(overlayIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting FloatingOverlayService: ${e.message}", e)
        }
    }

    /**
     * Store app request in overlay for quick access
     */
    private fun storeAppRequestInOverlay(packageName: String?, appName: String?) {
        try {
            val sharedPrefs = getSharedPreferences("floating_overlay_prefs", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            
            if (packageName != null) {
                editor.putString("last_package_name", packageName)
                editor.remove("last_app_name")
            } else if (appName != null) {
                editor.putString("last_app_name", appName)
                editor.remove("last_package_name")
            }
            
            editor.apply()
            Log.d(TAG, "Stored app request - package: $packageName, name: $appName")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing app request in overlay: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppManagementService destroyed")
        isRunning = false
    }
}