package com.vibeagent.dude

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class TextInputActivity {
    companion object {
        private const val TAG = "TextInputActivity"
        private const val INPUT_RETRY_ATTEMPTS = 3
        private const val FOCUS_WAIT_DELAY = 500L
    }

    /** Simple and reliable text input - uses focused input detection only */
    suspend fun performAdvancedType(text: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "⌨️ Direct text input: '$text'")
                
                if (text.isEmpty()) {
                    return@withContext false
                }

                val service = MyAccessibilityService.instance ?: return@withContext false
                 val rootNode = service.rootInActiveWindow ?: return@withContext false
                 
                 // Find the currently focused input field directly
                 val focusedInput = findFocusedEditableNode(rootNode)
                
                if (focusedInput == null) {
                    Log.e(TAG, "❌ No focused input found")
                    return@withContext false
                }
                
                // Clear cache and set text directly
                 service.clearFocusCache()
                 val arguments = Bundle()
                 arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                 val success = focusedInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                if (success) {
                    Log.d(TAG, "✅ Text set: '$text'")
                } else {
                    Log.e(TAG, "❌ Text failed: '$text'")
                }

                return@withContext success
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during text input: ${e.message}", e)
                    return@withContext false
                }
            }
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            if (node.isFocused && node.isEditable) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findFocusedEditableNode(child)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun typeText(text: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "⌨️ Direct text input: '$text'")
                
                if (text.isEmpty()) {
                    return@withContext false
                }

                val service = MyAccessibilityService.instance ?: return@withContext false
                 val rootNode = service.rootInActiveWindow ?: return@withContext false
                 
                 val focusedInput = findFocusedEditableNode(rootNode)
                
                if (focusedInput == null) {
                    Log.e(TAG, "❌ No focused input found")
                    return@withContext false
                }
                
                service.clearFocusCache()
                 val arguments = Bundle()
                 arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                 val success = focusedInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                if (success) {
                    Log.d(TAG, "✅ Text set: '$text'")
                } else {
                    Log.e(TAG, "❌ Text failed: '$text'")
                }

                return@withContext success
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during text input: ${e.message}", e)
                return@withContext false
            }
        }
    }
}