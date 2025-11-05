package com.vibeagent.dude

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.util.Log
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.os.Binder

class FloatingOverlayService : Service() {
    
    inner class OverlayBinder : Binder() {
        fun getService(): FloatingOverlayService = this@FloatingOverlayService
    }
    
    private val binder = OverlayBinder()
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val TAG = "FloatingOverlayService"
        
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingOverlayService created")
        
        if (!hasOverlayPermission(this)) {
            Log.e(TAG, "Overlay permission not granted")
            stopSelf()
            return
        }
        
        createFloatingView()
    }

    private fun createFloatingView() {
        // FloatingOverlayService now only manages service lifecycle
        // Visual overlay is handled by VoiceWaveView in the main app
        Log.d(TAG, "FloatingOverlayService initialized - visual overlay handled by VoiceWaveView")
    }
    
    // Removed expansion logic - overlay is now non-interactive
    
    // App opening logic removed - overlay is now purely visual

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingOverlayService started")
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
    
    fun showOverlay() {
        // Visual overlay is now handled by VoiceWaveView
        Log.d(TAG, "Overlay show request - handled by VoiceWaveView")
    }
    
    fun hideOverlay() {
        // Visual overlay is now handled by VoiceWaveView
        Log.d(TAG, "Overlay hide request - handled by VoiceWaveView")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingOverlayService destroyed")
        // No visual overlay to remove - handled by VoiceWaveView
    }
}