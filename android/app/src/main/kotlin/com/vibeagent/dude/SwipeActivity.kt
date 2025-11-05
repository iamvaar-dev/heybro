package com.vibeagent.dude

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SwipeActivity {
    companion object {
        private const val TAG = "SwipeActivity"
    }

    /** Performs a swipe gesture from start coordinates to end coordinates */
    suspend fun performSwipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            duration: Long = 300L
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔄 Performing swipe from ($startX, $startY) to ($endX, $endY)")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for swipe")
                        return@withContext false
                    }

                    val success = service.performSwipe(startX, startY, endX, endY, duration)
                    if (success) {
                        Log.d(TAG, "✅ Swipe performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform swipe")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during swipe: ${e.message}", e)
                    return@withContext false
                }
            }

    /** Performs a scroll gesture in the specified direction */
    suspend fun performScroll(direction: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "📜 Performing scroll in direction: $direction")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for scroll")
                        return@withContext false
                    }

                    val success = service.performScroll(direction)
                    if (success) {
                        Log.d(TAG, "✅ Scroll $direction performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform scroll $direction")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during scroll: ${e.message}", e)
                    return@withContext false
                }
            }

    /** Performs a directional swipe gesture */
    suspend fun performDirectionalSwipe(direction: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔄 Performing directional swipe: $direction")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for directional swipe")
                        return@withContext false
                    }

                    // Get screen dimensions for calculating swipe coordinates
                    val screenWidth =
                            MainActivity.instance?.resources?.displayMetrics?.widthPixels ?: 1080
                    val screenHeight =
                            MainActivity.instance?.resources?.displayMetrics?.heightPixels ?: 1920

                    val centerX = screenWidth / 2f
                    val centerY = screenHeight / 2f
                    val swipeDistance = minOf(screenWidth, screenHeight) / 3f

                    val (startX, startY, endX, endY) =
                            when (direction.lowercase()) {
                                "up" ->
                                        arrayOf(
                                                centerX,
                                                centerY + swipeDistance,
                                                centerX,
                                                centerY - swipeDistance
                                        )
                                "down" ->
                                        arrayOf(
                                                centerX,
                                                centerY - swipeDistance,
                                                centerX,
                                                centerY + swipeDistance
                                        )
                                "left" ->
                                        arrayOf(
                                                centerX + swipeDistance,
                                                centerY,
                                                centerX - swipeDistance,
                                                centerY
                                        )
                                "right" ->
                                        arrayOf(
                                                centerX - swipeDistance,
                                                centerY,
                                                centerX + swipeDistance,
                                                centerY
                                        )
                                else -> {
                                    Log.e(TAG, "❌ Invalid swipe direction: $direction")
                                    return@withContext false
                                }
                            }

                    val success = service.performSwipe(startX, startY, endX, endY, 300L)
                    if (success) {
                        Log.d(TAG, "✅ Directional swipe $direction performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform directional swipe $direction")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during directional swipe: ${e.message}", e)
                    return@withContext false
                }
            }

    /** Performs a pinch gesture at the specified coordinates */
    suspend fun performPinch(
            centerX: Float,
            centerY: Float,
            scale: Float,
            duration: Long = 500L
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🤏 Performing pinch at ($centerX, $centerY) with scale $scale")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for pinch")
                        return@withContext false
                    }

                    // Convert scale to distance values for the service
                    val baseDistance = 200f
                    val startDistance = baseDistance
                    val endDistance = baseDistance * scale

                    val success = service.performPinch(centerX, centerY, startDistance, endDistance)
                    if (success) {
                        Log.d(TAG, "✅ Pinch performed successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to perform pinch")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during pinch: ${e.message}", e)
                    return@withContext false
                }
            }

    /** Performs a zoom in gesture at the center of the screen */
    suspend fun performZoomIn(): Boolean {
        val screenWidth = MainActivity.instance?.resources?.displayMetrics?.widthPixels ?: 1080
        val screenHeight = MainActivity.instance?.resources?.displayMetrics?.heightPixels ?: 1920
        return performPinch(screenWidth / 2f, screenHeight / 2f, 2.0f, 500L)
    }

    /** Performs a zoom out gesture at the center of the screen */
    suspend fun performZoomOut(): Boolean {
        val screenWidth = MainActivity.instance?.resources?.displayMetrics?.widthPixels ?: 1080
        val screenHeight = MainActivity.instance?.resources?.displayMetrics?.heightPixels ?: 1920
        return performPinch(screenWidth / 2f, screenHeight / 2f, 0.5f, 500L)
    }

    /** Validates swipe coordinates */
    fun validateSwipeCoordinates(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            screenWidth: Int,
            screenHeight: Int
    ): Boolean {
        val isValid =
                startX >= 0 &&
                        startX <= screenWidth &&
                        startY >= 0 &&
                        startY <= screenHeight &&
                        endX >= 0 &&
                        endX <= screenWidth &&
                        endY >= 0 &&
                        endY <= screenHeight

        if (!isValid) {
            Log.w(
                    TAG,
                    "⚠️ Invalid swipe coordinates: ($startX, $startY) to ($endX, $endY) for screen ${screenWidth}x${screenHeight}"
            )
        }
        return isValid
    }

    /** Calculates swipe distance */
    fun calculateSwipeDistance(startX: Float, startY: Float, endX: Float, endY: Float): Float {
        val deltaX = endX - startX
        val deltaY = endY - startY
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    /** Checks if AccessibilityService is available for swipe operations */
    fun isServiceAvailable(): Boolean {
        val available = MyAccessibilityService.instance != null
        Log.d(
                TAG,
                if (available) "✅ AccessibilityService available"
                else "❌ AccessibilityService not available"
        )
        return available
    }
}
