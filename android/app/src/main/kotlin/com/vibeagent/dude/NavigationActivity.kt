package com.vibeagent.dude

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NavigationActivity {
    companion object {
        private const val TAG = "NavigationActivity"
    }

    /**
     * Performs a back navigation action
     * @return Boolean indicating success/failure
     */
    suspend fun performBack(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔙 Performing back navigation")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for back navigation")
                        return@withContext false
                    }

                    val success =
                            service.performGlobalAction(
                                    android.accessibilityservice.AccessibilityService
                                            .GLOBAL_ACTION_BACK
                            )
                    if (success) {
                        Log.d(TAG, "✅ Back navigation performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform back navigation")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during back navigation: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Performs a home navigation action
     * @return Boolean indicating success/failure
     */
    suspend fun performHome(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🏠 Performing home navigation")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for home navigation")
                        return@withContext false
                    }

                    val success =
                            service.performGlobalAction(
                                    android.accessibilityservice.AccessibilityService
                                            .GLOBAL_ACTION_HOME
                            )
                    if (success) {
                        Log.d(TAG, "✅ Home navigation performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform home navigation")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during home navigation: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Performs a recent apps navigation action
     * @return Boolean indicating success/failure
     */
    suspend fun performRecents(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "📱 Performing recent apps navigation")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(
                                TAG,
                                "❌ AccessibilityService not available for recent apps navigation"
                        )
                        return@withContext false
                    }

                    val success =
                            service.performGlobalAction(
                                    android.accessibilityservice.AccessibilityService
                                            .GLOBAL_ACTION_RECENTS
                            )
                    if (success) {
                        Log.d(TAG, "✅ Recent apps navigation performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform recent apps navigation")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during recent apps navigation: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Opens notification panel
     * @return Boolean indicating success/failure
     */
    suspend fun openNotifications(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔔 Opening notification panel")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for notifications")
                        return@withContext false
                    }

                    val success =
                            service.performGlobalAction(
                                    android.accessibilityservice.AccessibilityService
                                            .GLOBAL_ACTION_NOTIFICATIONS
                            )
                    if (success) {
                        Log.d(TAG, "✅ Notification panel opened successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to open notification panel")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception opening notifications: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Opens quick settings panel
     * @return Boolean indicating success/failure
     */
    suspend fun openQuickSettings(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "⚙️ Opening quick settings panel")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for quick settings")
                        return@withContext false
                    }

                    val success =
                            service.performGlobalAction(
                                    android.accessibilityservice.AccessibilityService
                                            .GLOBAL_ACTION_QUICK_SETTINGS
                            )
                    if (success) {
                        Log.d(TAG, "✅ Quick settings panel opened successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to open quick settings panel")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception opening quick settings: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Opens power dialog
     * @return Boolean indicating success/failure
     */
    suspend fun openPowerDialog(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "⚡ Opening power dialog")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for power dialog")
                        return@withContext false
                    }

                    val success =
                            service.performGlobalAction(
                                    android.accessibilityservice.AccessibilityService
                                            .GLOBAL_ACTION_POWER_DIALOG
                            )
                    if (success) {
                        Log.d(TAG, "✅ Power dialog opened successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to open power dialog")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception opening power dialog: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Opens accessibility settings
     * @param context The context to use for launching settings
     * @return Boolean indicating success/failure
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        return try {
            Log.d(TAG, "♿ Opening accessibility settings")

            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "✅ Accessibility settings opened successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open accessibility settings: ${e.message}", e)
            false
        }
    }

    /**
     * Opens device settings
     * @param context The context to use for launching settings
     * @return Boolean indicating success/failure
     */
    fun openSettings(context: Context): Boolean {
        return try {
            Log.d(TAG, "⚙️ Opening device settings")

            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "✅ Device settings opened successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open device settings: ${e.message}", e)
            false
        }
    }

    /**
     * Opens WiFi settings
     * @param context The context to use for launching settings
     * @return Boolean indicating success/failure
     */
    fun openWifiSettings(context: Context): Boolean {
        return try {
            Log.d(TAG, "📶 Opening WiFi settings")

            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "✅ WiFi settings opened successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open WiFi settings: ${e.message}", e)
            false
        }
    }

    /**
     * Opens Bluetooth settings
     * @param context The context to use for launching settings
     * @return Boolean indicating success/failure
     */
    fun openBluetoothSettings(context: Context): Boolean {
        return try {
            Log.d(TAG, "🔵 Opening Bluetooth settings")

            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "✅ Bluetooth settings opened successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open Bluetooth settings: ${e.message}", e)
            false
        }
    }

    /**
     * Navigates to a specific app settings page
     * @param packageName The package name of the app
     * @param context The context to use for launching settings
     * @return Boolean indicating success/failure
     */
    fun openAppSettings(packageName: String, context: Context): Boolean {
        return try {
            Log.d(TAG, "📱 Opening app settings for: $packageName")

            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "✅ App settings opened successfully for: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open app settings for $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * Performs a navigation action based on direction
     * @param direction The navigation direction ("back", "home", "recents", etc.)
     * @return Boolean indicating success/failure
     */
    suspend fun performNavigationAction(direction: String): Boolean {
        return when (direction.lowercase()) {
            "back" -> performBack()
            "home" -> performHome()
            "recents", "recent", "recent_apps" -> performRecents()
            "notifications" -> openNotifications()
            "quick_settings" -> openQuickSettings()
            "power" -> openPowerDialog()
            else -> {
                Log.e(TAG, "❌ Unknown navigation direction: $direction")
                false
            }
        }
    }

    /**
     * Checks if AccessibilityService is available for navigation operations
     * @return Boolean indicating service availability
     */
    fun isServiceAvailable(): Boolean {
        val available = MyAccessibilityService.instance != null
        Log.d(
                TAG,
                if (available) "✅ AccessibilityService available for navigation"
                else "❌ AccessibilityService not available for navigation"
        )
        return available
    }

    /**
     * Gets available navigation actions
     * @return List of available navigation action names
     */
    fun getAvailableNavigationActions(): List<String> {
        return if (isServiceAvailable()) {
            listOf("back", "home", "recents", "notifications", "quick_settings", "power")
        } else {
            emptyList()
        }
    }

    /**
     * Validates if a navigation action is supported
     * @param action The navigation action to validate
     * @return Boolean indicating if the action is supported
     */
    fun isNavigationActionSupported(action: String): Boolean {
        val supportedActions = getAvailableNavigationActions()
        val isSupported = supportedActions.contains(action.lowercase())

        Log.d(
                TAG,
                if (isSupported) "✅ Navigation action '$action' is supported"
                else "❌ Navigation action '$action' is not supported"
        )

        return isSupported
    }
}
