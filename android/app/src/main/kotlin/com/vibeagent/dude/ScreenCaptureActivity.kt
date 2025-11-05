package com.vibeagent.dude

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenCaptureActivity {
    companion object {
        private const val TAG = "ScreenCaptureActivity"
    }

    /**
     * Takes a screenshot using AccessibilityService
     * @return Base64 encoded screenshot string or null if failed
     */
    suspend fun takeScreenshot(): String? =
            withContext(Dispatchers.IO) {
                try {


                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for screenshot")
                        return@withContext takeScreenshotFallback()
                    }

                    val screenshot = service.takeScreenshot()
                    if (screenshot != null) {

                        return@withContext screenshot
                    } else {
                        Log.e(TAG, "❌ Screenshot is null, trying fallback")
                        return@withContext takeScreenshotFallback()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during screenshot: ${e.message}", e)
                    return@withContext takeScreenshotFallback()
                }
            }

    /**
     * Takes a high-quality screenshot specifically for analysis
     * @return Base64 encoded screenshot string or null if failed
     */
    suspend fun takeRealScreenshot(): String? {
        return try {


            val service = MyAccessibilityService.instance
            if (service == null) {
                Log.e(TAG, "❌ AccessibilityService not available for real screenshot")
                return null
            }

            val screenshot = service.takeScreenshot()
            if (screenshot != null) {

                screenshot
            } else {
                Log.e(TAG, "❌ Failed to take real screenshot")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during real screenshot: ${e.message}", e)
            null
        }
    }

    /**
     * Fallback screenshot method using AccessibilityService
     * @return Base64 encoded screenshot string or null if failed
     */
    suspend fun takeScreenshotFallback(): String? {
        return try {


            val service = MyAccessibilityService.instance
            if (service == null) {
                Log.e(TAG, "❌ AccessibilityService not available for fallback screenshot")
                return null
            }

            val screenshot = service.takeScreenshot()
            if (screenshot != null) {

                screenshot
            } else {
                Log.e(TAG, "❌ Fallback screenshot failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during fallback screenshot: ${e.message}", e)
            null
        }
    }

    /**
     * Gets screen elements using AccessibilityService
     * @return List of screen elements with their properties
     */
    suspend fun getScreenElements(): List<Map<String, Any>> =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔍 Getting screen elements")

                    val service = MyAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "❌ AccessibilityService not available for screen elements")
                        return@withContext emptyList()
                    }

                    val elements = service.getScreenElements()
                    Log.d(TAG, "✅ Found ${elements.size} screen elements")

                    // Convert nullable Any? to Any
                    elements.mapNotNull { element ->
                        try {
                            element
                                    .mapNotNull { (key, value) ->
                                        if (value != null) key to value else null
                                    }
                                    .toMap()
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping invalid element: ${e.message}")
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception getting screen elements: ${e.message}", e)
                    return@withContext emptyList()
                }
            }

    /**
     * Analyzes the current screen for interactive elements
     * @return Map containing screen analysis results
     */
    suspend fun analyzeScreen(): Map<String, Any> =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔬 Analyzing current screen")

                    val elements = getScreenElements()
                    val screenshot = takeRealScreenshot()

                    val analysis = mutableMapOf<String, Any>()
                    analysis["timestamp"] = System.currentTimeMillis()
                    analysis["elementCount"] = elements.size
                    analysis["hasScreenshot"] = screenshot != null

                    if (screenshot != null) {
                        analysis["screenshot"] = screenshot
                    }

                    // Categorize elements
                    val clickableElements = elements.filter { it["clickable"] as? Boolean ?: false }
                    val scrollableElements =
                            elements.filter { it["scrollable"] as? Boolean ?: false }
                    val textElements =
                            elements.filter { !((it["text"] as? String)?.isEmpty() ?: true) }

                    analysis["clickableCount"] = clickableElements.size
                    analysis["scrollableCount"] = scrollableElements.size
                    analysis["textElementCount"] = textElements.size
                    analysis["elements"] = elements

                    Log.d(TAG, "✅ Screen analysis completed:")
                    Log.d(TAG, "  - Total elements: ${elements.size}")
                    Log.d(TAG, "  - Clickable: ${clickableElements.size}")
                    Log.d(TAG, "  - Scrollable: ${scrollableElements.size}")
                    Log.d(TAG, "  - Text elements: ${textElements.size}")

                    analysis.toMap()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during screen analysis: ${e.message}", e)
                    mapOf(
                            "error" to (e.message ?: "Unknown error"),
                            "timestamp" to System.currentTimeMillis(),
                            "elementCount" to 0,
                            "hasScreenshot" to false
                    )
                }
            }

    /**
     * Converts a bitmap to base64 string
     * @param bitmap The bitmap to convert
     * @param quality JPEG quality (0-100)
     * @return Base64 encoded string or null if conversion failed
     */
    private fun convertBitmapToBase64(bitmap: Bitmap, quality: Int = 20): String? {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()

            // Resize bitmap to reduce AI token usage
            val resizedBitmap = resizeBitmapForAI(bitmap)

            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            // Clean up resized bitmap if different from original
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }


            base64String
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error converting bitmap to base64: ${e.message}", e)
            null
        }
    }

    /**
     * Resize bitmap to maximum 720p to reduce AI token usage
     */
    private fun resizeBitmapForAI(bitmap: Bitmap): Bitmap {
        val maxWidth = 480
        val maxHeight = 854

        val width = bitmap.width
        val height = bitmap.height

        // If already small enough, return original
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        // Calculate scale factor
        val scaleWidth = maxWidth.toFloat() / width
        val scaleHeight = maxHeight.toFloat() / height
        val scale = minOf(scaleWidth, scaleHeight)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()


        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false)
    }

    /**
     * Validates screenshot data
     * @param base64Screenshot Base64 encoded screenshot
     * @return Boolean indicating if screenshot is valid
     */
    fun validateScreenshot(base64Screenshot: String?): Boolean {
        if (base64Screenshot.isNullOrEmpty()) {
            Log.w(TAG, "⚠️ Screenshot is null or empty")
            return false
        }

        return try {
            val decodedBytes = Base64.decode(base64Screenshot, Base64.NO_WRAP)
            val isValid = decodedBytes.isNotEmpty()



            isValid
        } catch (e: Exception) {
            Log.e(TAG, "❌ Screenshot validation error: ${e.message}", e)
            false
        }
    }

    /**
     * Gets screen dimensions
     * @return Map containing screen width and height
     */
    fun getScreenDimensions(): Map<String, Any> {
        return try {
            val metrics = MainActivity.instance?.resources?.displayMetrics
            val width = metrics?.widthPixels ?: 1080
            val height = metrics?.heightPixels ?: 1920
            val density = metrics?.densityDpi ?: 420



            mapOf("width" to width, "height" to height, "density" to density)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting screen dimensions: ${e.message}", e)
            mapOf("width" to 1080, "height" to 1920, "density" to 420)
        }
    }

    /**
     * Checks if screenshot capability is available
     * @return Boolean indicating if screenshots can be taken
     */
    fun isScreenshotCapable(): Boolean {
        val available = MyAccessibilityService.instance != null

        return available
    }

    /** Clears any cached screenshot data */
    fun clearCache() {
        try {
            // Clear any cached screenshot data
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing screenshot cache: ${e.message}", e)
        }
    }

    /**
     * Gets screenshot metadata
     * @param base64Screenshot Base64 encoded screenshot
     * @return Map containing screenshot metadata
     */
    fun getScreenshotMetadata(base64Screenshot: String?): Map<String, Any> {
        return try {
            if (base64Screenshot.isNullOrEmpty()) {
                return mapOf("valid" to false, "error" to "Screenshot is null or empty")
            }

            val decodedBytes = Base64.decode(base64Screenshot, Base64.NO_WRAP)
            val screenDimensions = getScreenDimensions()

            mapOf(
                    "valid" to true,
                    "sizeBytes" to decodedBytes.size,
                    "sizeKB" to (decodedBytes.size / 1024),
                    "timestamp" to System.currentTimeMillis(),
                    "screenWidth" to screenDimensions["width"]!!,
                    "screenHeight" to screenDimensions["height"]!!,
                    "density" to screenDimensions["density"]!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting screenshot metadata: ${e.message}", e)
            mapOf("valid" to false, "error" to (e.message ?: "Unknown error"))
        }
    }
}
