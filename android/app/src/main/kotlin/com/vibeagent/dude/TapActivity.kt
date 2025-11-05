package com.vibeagent.dude

import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TapActivity {
    companion object {
        private const val TAG = "TapActivity"
    }

    /**
     * Performs an intelligent tap at the specified coordinates
     * First tries to find nearby clickable elements, then falls back to exact coordinates
     * @param x X coordinate for the tap
     * @param y Y coordinate for the tap
     * @return Boolean indicating success/failure
     */
    suspend fun performTap(x: Float, y: Float): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🖱️ Performing tap at coordinates: ($x, $y)")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for tap")
                        return@withContext false
                    }

                    // Show visual feedback for testing
                    showTapIndicator(x, y)

                    // MyAccessibilityService now handles smart targeting internally
                    val success = service.performTap(x, y)
                    if (success) {
                        Log.d(TAG, "✅ Tap performed successfully at ($x, $y)")
                    } else {
                        Log.e(TAG, "❌ Failed to perform tap at ($x, $y)")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during tap: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Performs a long press at the specified coordinates
     * @param x X coordinate for the long press
     * @param y Y coordinate for the long press
     * @param duration Duration of the long press in milliseconds
     * @return Boolean indicating success/failure
     */
    suspend fun performLongPress(x: Float, y: Float, duration: Long = 500L): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(
                            TAG,
                            "🖱️ Performing long press at coordinates: ($x, $y) for ${duration}ms"
                    )

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for long press")
                        return@withContext false
                    }

                    val success = service.performLongPress(x, y, duration)
                    if (success) {
                        Log.d(TAG, "✅ Long press performed successfully at ($x, $y)")
                    } else {
                        Log.e(TAG, "❌ Failed to perform long press at ($x, $y)")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during long press: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Performs a double click at the specified coordinates
     * @param x X coordinate for the double click
     * @param y Y coordinate for the double click
     * @return Boolean indicating success/failure
     */
    suspend fun performDoubleClick(x: Float, y: Float): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🖱️ Performing double click at coordinates: ($x, $y)")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for double click")
                        return@withContext false
                    }

                    val success = service.performDoubleClick(x, y)
                    if (success) {
                        Log.d(TAG, "✅ Double click performed successfully at ($x, $y)")
                    } else {
                        Log.e(TAG, "❌ Failed to perform double click at ($x, $y)")
                    }

                    return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during double click: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * Validates if coordinates are within screen bounds
     * @param x X coordinate
     * @param y Y coordinate
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return Boolean indicating if coordinates are valid
     */
    fun validateCoordinates(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Boolean {
        val isValid = x >= 0 && x <= screenWidth && y >= 0 && y <= screenHeight
        if (!isValid) {
            Log.w(
                    TAG,
                    "⚠️ Invalid coordinates: ($x, $y) for screen size: ${screenWidth}x${screenHeight}"
            )
        }
        return isValid
    }



    /**
     * Shows a visual indicator at tap location for testing purposes
     * @param x X coordinate
     * @param y Y coordinate
     */
    private suspend fun showTapIndicator(x: Float, y: Float) {
        try {
            val service = MyAccessibilityService.instance ?: return

            withContext(Dispatchers.Main) {
                val windowManager = service.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@withContext

                // Create bright white dot indicator with border for visibility
                val indicator = ImageView(service).apply {
                    setBackgroundColor(0xFFFFFFFF.toInt()) // Bright white
                    alpha = 0.9f
                    scaleType = ImageView.ScaleType.CENTER
                    // Add a black border for better visibility
                    setPadding(2, 2, 2, 2)
                }

                val size = 24 // Larger 24dp diameter for better visibility
                val params = WindowManager.LayoutParams(
                    size,
                    size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = (x - size / 2).toInt()
                    this.y = (y - size / 2).toInt()
                    // Add slight elevation for better visibility
                    dimAmount = 0.0f
                }

                try {
                    windowManager.addView(indicator, params)

                    // Remove after 6 seconds
                    GlobalScope.launch {
                        delay(6000)
                        withContext(Dispatchers.Main) {
                            try {
                                windowManager.removeView(indicator)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to remove tap indicator: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show tap indicator: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error showing tap indicator: ${e.message}")
        }
    }

    /**
     * Checks if AccessibilityService is available for tap operations
     * @return Boolean indicating service availability
     */
    fun isServiceAvailable(): Boolean {
        val available = MyAccessibilityService.instance != null
        Log.d(
                TAG,
                if (available) "✅ AccessibilityService available"
                else "❌ AccessibilityService not available"
        )
        return available
    }

    /**
     * Test method to verify tap accuracy and smart targeting
     * Performs a series of test taps to validate the targeting system
     * @param testCoordinates List of coordinates to test
     * @return Map with test results
     */
    suspend fun testTapAccuracy(testCoordinates: List<Pair<Float, Float>>): Map<String, Any> =
            withContext(Dispatchers.IO) {
                val results = mutableMapOf<String, Any>()
                val successfulTaps = mutableListOf<String>()
                val failedTaps = mutableListOf<String>()

                Log.d(TAG, "🧪 Starting tap accuracy test with ${testCoordinates.size} coordinates")

                try {
                    for ((index, coords) in testCoordinates.withIndex()) {
                        val (x, y) = coords
                        Log.d(TAG, "🧪 Test tap ${index + 1}: ($x, $y)")

                        val success = performTap(x, y)

                        if (success) {
                            successfulTaps.add("($x, $y)")
                            Log.d(TAG, "✅ Test tap ${index + 1} successful")
                        } else {
                            failedTaps.add("($x, $y)")
                            Log.e(TAG, "❌ Test tap ${index + 1} failed")
                        }

                        // Wait between taps to avoid interference
                        delay(1000)
                    }

                    val successRate = (successfulTaps.size.toFloat() / testCoordinates.size) * 100

                    results["total_tests"] = testCoordinates.size
                    results["successful_taps"] = successfulTaps.size
                    results["failed_taps"] = failedTaps.size
                    results["success_rate"] = successRate
                    results["successful_coordinates"] = successfulTaps
                    results["failed_coordinates"] = failedTaps

                    Log.d(TAG, "🧪 Tap accuracy test completed: ${successfulTaps.size}/${testCoordinates.size} successful (${successRate}%)")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Tap accuracy test failed: ${e.message}", e)
                    results["error"] = e.message ?: "Unknown error"
                }

                results.toMap()
            }

    /**
     * Quick test method for basic tap functionality
     * Tests taps at screen center and corners
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return Boolean indicating overall test success
     */
    suspend fun performQuickTapTest(screenWidth: Int, screenHeight: Int): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val centerX = screenWidth / 2f
                    val centerY = screenHeight / 2f

                    val testPoints = listOf(
                        Pair(centerX, centerY), // Center
                        Pair(100f, 100f), // Top-left area
                        Pair(screenWidth - 100f, 100f), // Top-right area
                        Pair(centerX, screenHeight - 100f) // Bottom-center area
                    )

                    Log.d(TAG, "🧪 Performing quick tap test at 4 locations")

                    val results = testTapAccuracy(testPoints)
                    val successRate = results["success_rate"] as? Float ?: 0f

                    val testPassed = successRate >= 75f
                    Log.d(TAG, "🧪 Quick tap test ${if (testPassed) "PASSED" else "FAILED"}: $successRate% success rate")

                    testPassed
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Quick tap test failed: ${e.message}", e)
                    false
                }
            }
}
