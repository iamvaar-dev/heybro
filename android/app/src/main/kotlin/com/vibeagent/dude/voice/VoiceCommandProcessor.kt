package com.vibeagent.dude.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import com.vibeagent.dude.AppManagementService
import com.vibeagent.dude.AutomationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceCommandProcessor(private val context: Context, private var appManagementService: AppManagementService?, private var automationService: AutomationService?) {

    private val processorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "VoiceCommandProcessor"
        
        // Command patterns
        private val OPEN_APP_PATTERNS = listOf(
            "open", "launch", "start", "run", "execute"
        )
        
        private val CLOSE_APP_PATTERNS = listOf(
            "close", "exit", "quit", "stop", "kill"
        )
        
        private val NAVIGATION_PATTERNS = listOf(
            "go to", "navigate to", "switch to", "show"
        )
        
        // Removed hardcoded SYSTEM_COMMANDS map to allow dynamic app discovery
        // This enables opening any installed app, not just predefined ones
        private val COMMON_APP_NAMES = listOf(
            "settings", "camera", "gallery", "phone", "messages", "contacts",
            "calculator", "calendar", "clock", "music", "browser", "chrome",
            "gmail", "maps", "youtube", "play store", "photos", "files"
        )
    }

    fun processCommand(command: String) {
        Log.d(TAG, "Processing voice command: $command")
        
        val normalizedCommand = command.lowercase(Locale.getDefault()).trim()
        
        processorScope.launch {
            try {
                // Send all voice commands directly to automation service
                val success = automationService?.executeUserTask(command) ?: false
                if (success) {
                    Log.d(TAG, "Voice command executed successfully: $command")
                } else {
                    Log.w(TAG, "Voice command execution failed: $command")
                    // Send error broadcast to trigger TTS notification and hide overlay
                    sendErrorBroadcast("Command execution failed: $command")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing voice command: $command", e)
                // Send error broadcast to trigger TTS notification and hide overlay
                sendErrorBroadcast("Error processing command: ${e.message}")
            }
        }
    }

    private fun isOpenCommand(command: String): Boolean {
        return OPEN_APP_PATTERNS.any { pattern -> command.startsWith(pattern) }
    }

    private fun isCloseCommand(command: String): Boolean {
        return CLOSE_APP_PATTERNS.any { pattern -> command.startsWith(pattern) }
    }

    private fun isNavigationCommand(command: String): Boolean {
        return NAVIGATION_PATTERNS.any { pattern -> command.startsWith(pattern) }
    }

    private fun isSystemCommand(command: String): Boolean {
        // Check if command contains common app names that should be handled as system commands
        return COMMON_APP_NAMES.any { appName -> command.contains(appName, ignoreCase = true) }
    }

    private suspend fun handleOpenCommand(command: String) {
        val appName = extractAppName(command, OPEN_APP_PATTERNS)
        if (appName.isNotEmpty()) {
            Log.d(TAG, "Opening app: $appName")
            
            // Use dynamic app discovery for all apps - no more hardcoded packages
            appManagementService?.openAppByName(appName) { success ->
                Log.d(TAG, "App open by name result for $appName: $success")
            }
        } else {
            Log.w(TAG, "Could not extract app name from open command: $command")
        }
    }

    private suspend fun handleCloseCommand(command: String) {
        val appName = extractAppName(command, CLOSE_APP_PATTERNS)
        if (appName.isNotEmpty()) {
            Log.d(TAG, "Closing app: $appName")
            
            // For now, we'll just log this as the AppManagementService doesn't have a close method
            // This could be implemented using accessibility service to go back or home
            Log.i(TAG, "Close command received for: $appName (not implemented yet)")
        } else {
            Log.w(TAG, "Could not extract app name from close command: $command")
        }
    }

    private suspend fun handleNavigationCommand(command: String) {
        val target = extractAppName(command, NAVIGATION_PATTERNS)
        if (target.isNotEmpty()) {
            Log.d(TAG, "Navigating to: $target")
            
            // Check if it's a system command first
            val systemPackage = findSystemPackage(target)
            if (systemPackage != null) {
                appManagementService?.openApp(systemPackage) { success ->
                    Log.d(TAG, "Navigation app open result for $systemPackage: $success")
                }
            } else {
                appManagementService?.openAppByName(target) { success ->
                    Log.d(TAG, "Navigation app open by name result for $target: $success")
                }
            }
        } else {
            Log.w(TAG, "Could not extract target from navigation command: $command")
        }
    }

    private suspend fun handleSystemCommand(command: String) {
        Log.d(TAG, "Handling system command: $command")
        
        // Instead of using hardcoded package names, use dynamic app discovery
        // This allows opening any installed app, not just hardcoded ones
        appManagementService?.openAppByName(command) { success ->
            Log.d(TAG, "System app open by name result for $command: $success")
        }
    }

    private suspend fun handleGenericCommand(command: String) {
        Log.d(TAG, "Handling generic command: $command")
        
        // Try to interpret as an app name directly
        if (command.length > 2) {
            Log.d(TAG, "Attempting to open app by name: $command")
            appManagementService?.openAppByName(command) { success ->
                Log.d(TAG, "Generic app open by name result for $command: $success")
            }
        } else {
            Log.w(TAG, "Command too short or not recognized: $command")
        }
    }

    private fun extractAppName(command: String, patterns: List<String>): String {
        for (pattern in patterns) {
            if (command.startsWith(pattern)) {
                val appName = command.substring(pattern.length).trim()
                // Remove common words that might be included
                return appName
                    .replace("app", "")
                    .replace("application", "")
                    .trim()
            }
        }
        return ""
    }

    private fun findSystemPackage(command: String): String? {
        // This method is now deprecated in favor of dynamic app discovery
        // Keeping for backward compatibility but will use openAppByName instead
        return null
    }

    fun updateAppManagementService(appManagementService: AppManagementService?) {
        this.appManagementService = appManagementService
        Log.d(TAG, "AppManagementService updated")
    }

    fun cleanup() {
        Log.d(TAG, "VoiceCommandProcessor cleaned up")
        // No specific cleanup needed for now
    }

    // Helper method to get supported commands for debugging
    fun getSupportedCommands(): List<String> {
        val commands = mutableListOf<String>()
        
        // Add pattern examples
        commands.addAll(OPEN_APP_PATTERNS.map { "$it [app name]" })
        commands.addAll(CLOSE_APP_PATTERNS.map { "$it [app name]" })
        commands.addAll(NAVIGATION_PATTERNS.map { "$it [app name]" })
        
        // Add common app names
        commands.addAll(COMMON_APP_NAMES)
        
        return commands
    }
    
    /**
     * Send error broadcast to trigger TTS notification and hide overlay
     */
    private fun sendErrorBroadcast(error: String) {
        try {
            Log.d(TAG, "Sending error broadcast: $error")
            val intent = Intent("com.vibeagent.dude.AUTOMATION_COMPLETE")
            val resultJson = "{\"task_completed\":true,\"success\":false,\"error\":\"$error\"}"
            intent.putExtra("result", resultJson)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            Log.d(TAG, "Error broadcast sent with result: $resultJson")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send error broadcast: ${e.message}", e)
        }
    }
}