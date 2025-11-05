package com.vibeagent.dude

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrProcessor {
    companion object {
        private const val TAG = "OcrProcessor"
    }

    data class OcrResult(
        val success: Boolean,
        val fullText: String,
        val blocks: List<Map<String, Any>>,
        val imageWidth: Int,
        val imageHeight: Int,
        val error: String?
    )

    suspend fun extractTextFromBase64Screenshot(base64Screenshot: String): OcrResult {
        return try {
            val bitmap = decodeBase64ToBitmap(base64Screenshot)
                ?: return OcrResult(false, "", emptyList(), 0, 0, "Invalid screenshot data")

            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result: Text = recognizer.process(image).await()

            val blocks = mutableListOf<Map<String, Any>>()
            for (block in result.textBlocks) {
                blocks.add(
                    mapOf(
                        "text" to block.text,
                        "cornerPoints" to (block.cornerPoints?.map { mapOf("x" to it.x, "y" to it.y) } ?: emptyList()),
                        "boundingBox" to (block.boundingBox?.let { bb ->
                            mapOf("left" to bb.left, "top" to bb.top, "right" to bb.right, "bottom" to bb.bottom)
                        } ?: emptyMap())
                    )
                )
            }

            val fullText = result.text ?: ""
            Log.d(TAG, "✅ OCR extracted ${fullText.length} chars across ${blocks.size} blocks")

            OcrResult(true, fullText, blocks, bitmap.width, bitmap.height, null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ OCR extraction failed: ${e.message}", e)
            OcrResult(false, "", emptyList(), 0, 0, e.message)
        }
    }

    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decoded = Base64.decode(base64Str, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding base64 image: ${e.message}")
            null
        }
    }
} 