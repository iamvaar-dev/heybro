package com.vibeagent.dude

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vibeagent.dude.voice.EnhancedWakeWordService

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device boot completed, starting wake word service")
                startWakeWordService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Check if it's our package being replaced
                val packageName = intent.dataString
                if (packageName?.contains(context.packageName) == true) {
                    Log.i(TAG, "App updated, restarting wake word service")
                    startWakeWordService(context)
                }
            }
        }
    }
    
    private fun startWakeWordService(context: Context) {
        try {
            // Check if auto-start is enabled
            if (!EnhancedWakeWordService.isAutoStartEnabled(context)) {
                Log.i(TAG, "Auto-start is disabled, skipping wake word service start")
                return
            }
            
            val serviceIntent = Intent(context, EnhancedWakeWordService::class.java)
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "Wake word service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word service: ${e.message}", e)
        }
    }
}