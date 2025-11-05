package com.vibeagent.dude

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val GESTURE_DURATION = 100L
        private const val SCROLL_DURATION = 500L
        var instance: MyAccessibilityService? = null
    }

    private enum class AppFramework {
        NATIVE_JAVA_KOTLIN,
        FLUTTER,
        REACT_NATIVE,
        XAMARIN,
        UNKNOWN
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        Log.d(TAG, "Accessibility service connected")
        
        // Check and log consent status when service connects
        try {
            val consentManager = AccessibilityConsentManager(this)
            val hasConsent = consentManager.hasAccessibilityConsent()
            val consentInfo = consentManager.getConsentInfo()
            Log.d(TAG, "📋 Accessibility consent status: $hasConsent")
            Log.d(TAG, "📋 Consent info: $consentInfo")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking consent status: ${e.message}", e)
        }
    }

    // Track currently focused input element
    private var currentFocusedInput: AccessibilityNodeInfo? = null
    private var focusedInputInfo: Map<String, Any?>? = null
    


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            val framework = detectAppFramework(it.packageName?.toString())
            Log.v(TAG, "Event: ${it.eventType} from ${it.packageName} [${framework}]")
            
            // Track focused input elements
            when (it.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handleViewFocused(it)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                    handleTextSelectionChanged(it)
                }
            }
        }
    }
    
    private fun handleViewFocused(event: AccessibilityEvent) {
        try {
            val source = event.source
            if (source != null && isEditableNode(source)) {
                // Clear previous focus
                currentFocusedInput?.recycle()
                
                // Store new focused input
                currentFocusedInput = AccessibilityNodeInfo.obtain(source)
                focusedInputInfo = extractFocusedInputInfo(source)
                
                Log.d(TAG, "📍 Input focused: ${source.className} - ${source.viewIdResourceName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling view focused event: ${e.message}")
        }
    }
    
    private fun handleTextSelectionChanged(event: AccessibilityEvent) {
        try {
            val source = event.source
            if (source != null && isEditableNode(source)) {
                // Update current focused input if it's the same element
                if (currentFocusedInput != null) {
                    val currentBounds = android.graphics.Rect()
                    val sourceBounds = android.graphics.Rect()
                    currentFocusedInput?.getBoundsInScreen(currentBounds)
                    source.getBoundsInScreen(sourceBounds)
                    
                    if (currentBounds == sourceBounds) {
                        // Update selection info
                        focusedInputInfo = extractFocusedInputInfo(source)
                        Log.d(TAG, "🔄 Text selection changed in focused input")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling text selection changed event: ${e.message}")
        }
    }
    
    private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        return node.isEditable || 
               node.className?.toString()?.lowercase()?.contains("edittext") == true ||
               node.className?.toString()?.lowercase()?.contains("textinput") == true
    }
    
    private fun extractFocusedInputInfo(node: AccessibilityNodeInfo): Map<String, Any?> {
        return mapOf(
            "viewIdResourceName" to node.viewIdResourceName,
            "className" to node.className?.toString(),
            "packageName" to node.packageName?.toString(),
            "textSelectionStart" to node.textSelectionStart,
            "textSelectionEnd" to node.textSelectionEnd,
            "text" to node.text?.toString(),
            "hintText" to node.hintText?.toString(),
            "contentDescription" to node.contentDescription?.toString()
        )
    }
    
    fun getCurrentFocusedInput(): AccessibilityNodeInfo? {
        // If we have a cached focused input, return it
        if (currentFocusedInput != null) {
            return currentFocusedInput
        }
        
        // Otherwise, actively search for the currently focused input field
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val focusedInput = findFocusedInput(rootNode)
                rootNode.recycle()
                
                // Cache the found focused input
                if (focusedInput != null) {
                    currentFocusedInput = AccessibilityNodeInfo.obtain(focusedInput)
                    focusedInputInfo = extractFocusedInputInfo(focusedInput)
                    Log.d(TAG, "📍 Found focused input: ${focusedInput.className} - ${focusedInput.viewIdResourceName}")
                }
                
                return focusedInput
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding focused input: ${e.message}")
        }
        
        return null
    }
    
    fun clearFocusCache() {
        currentFocusedInput?.recycle()
        currentFocusedInput = null
        focusedInputInfo = null
    }
    
    private fun findFocusedInput(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            // Check if current node is a focused input field
            if (isEditableNode(node) && node.isFocused) {
                return node
            }
            
            // Search children recursively
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                child?.let {
                    val focusedInput = findFocusedInput(it)
                    if (focusedInput != null) {
                        it.recycle()
                        return focusedInput
                    }
                    it.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching for focused input: ${e.message}")
        }
        
        return null
    }
    
    fun getFocusedInputInfo(): Map<String, Any?>? {
        return focusedInputInfo
    }
    
    fun getAllInputFields(): List<Map<String, Any?>> {
        val inputFields = mutableListOf<Map<String, Any?>>()
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                collectInputFields(rootNode, inputFields)
                rootNode.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting input fields: ${e.message}")
        }
        return inputFields
    }
    
    private fun collectInputFields(node: AccessibilityNodeInfo, inputFields: MutableList<Map<String, Any?>>) {
        try {
            // Check if current node is an input field
            if (isEditableNode(node)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                
                val fieldInfo = mapOf(
                    "viewIdResourceName" to node.viewIdResourceName,
                    "className" to node.className?.toString(),
                    "packageName" to node.packageName?.toString(),
                    "text" to node.text?.toString(),
                    "hintText" to node.hintText?.toString(),
                    "contentDescription" to node.contentDescription?.toString(),
                    "bounds" to mapOf(
                        "left" to bounds.left,
                        "top" to bounds.top,
                        "right" to bounds.right,
                        "bottom" to bounds.bottom
                    ),
                    "isFocused" to node.isFocused
                )
                inputFields.add(fieldInfo)
            }
            
            // Search children recursively
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                child?.let {
                    collectInputFields(it, inputFields)
                    it.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting input field: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    private fun detectAppFramework(packageName: String?): AppFramework {
        if (packageName == null) return AppFramework.UNKNOWN

        val rootNode = rootInActiveWindow ?: return AppFramework.UNKNOWN

        try {
            val framework =
                    when {
                        isFlutterApp(rootNode) -> AppFramework.FLUTTER
                        isReactNativeApp(rootNode) -> AppFramework.REACT_NATIVE
                        isXamarinApp(rootNode) -> AppFramework.XAMARIN
                        else -> AppFramework.NATIVE_JAVA_KOTLIN
                    }
            rootNode.recycle()
            return framework
        } catch (e: Exception) {
            rootNode.recycle()
            return AppFramework.UNKNOWN
        }
    }

    private fun isFlutterApp(rootNode: AccessibilityNodeInfo): Boolean {
        return findNodesByClassName(rootNode, "io.flutter.view.FlutterView").isNotEmpty() ||
                findNodesByClassName(rootNode, "flutter.view").isNotEmpty() ||
                findNodesByClassName(rootNode, "io.flutter").isNotEmpty()
    }

    private fun isReactNativeApp(rootNode: AccessibilityNodeInfo): Boolean {
        return findNodesByClassName(rootNode, "com.facebook.react.ReactRootView").isNotEmpty() ||
                findNodesByClassName(rootNode, "ReactNativeHost").isNotEmpty() ||
                findNodesByContentDescription(rootNode, "RCT").isNotEmpty()
    }

    private fun isXamarinApp(rootNode: AccessibilityNodeInfo): Boolean {
        return findNodesByClassName(rootNode, "xamarin").isNotEmpty() ||
                findNodesByClassName(rootNode, "Xamarin").isNotEmpty()
    }

    private fun findNodesByClassName(
            rootNode: AccessibilityNodeInfo,
            className: String
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByClassName(rootNode, className, nodes)
        return nodes
    }

    private fun collectNodesByClassName(
            node: AccessibilityNodeInfo,
            className: String,
            nodes: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            if (node.className?.toString()?.contains(className, ignoreCase = true) == true) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectNodesByClassName(child, className, nodes)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting nodes by className: ${e.message}")
        }
    }

    private fun findNodesByContentDescription(
            rootNode: AccessibilityNodeInfo,
            description: String
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByContentDescription(rootNode, description, nodes)
        return nodes
    }

    private fun collectNodesByContentDescription(
            node: AccessibilityNodeInfo,
            description: String,
            nodes: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            if (node.contentDescription?.toString()?.contains(description, ignoreCase = true) ==
                            true
            ) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectNodesByContentDescription(child, description, nodes)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting nodes by contentDescription: ${e.message}")
        }
    }

    fun performTap(x: Float, y: Float): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return false
            }

            Log.d(TAG, "🎯 Attempting tap at coordinates: ($x, $y)")
            
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                // Count input fields on screen to determine tapping strategy
                val inputFields = getAllInputFields()
                val hasMultipleInputFields = inputFields.size >= 2
                
                Log.d(TAG, "📊 Input fields detected: ${inputFields.size} (multiple: $hasMultipleInputFields)")
                
                val targetNode = findNodeAtCoordinates(rootNode, x, y)
                if (targetNode != null) {
                    // Use node-based tapping ONLY when we have multiple input fields AND we're tapping on an input field
                    if (hasMultipleInputFields && isEditableNode(targetNode)) {
                        Log.d(TAG, "📝 Multiple input fields + input field target detected - using node-based click")
                        val success = performNodeBasedClick(targetNode)
                        targetNode.recycle()
                        Log.d(TAG, if (success) "✅ Node-based click on input field successful" else "❌ Node-based click on input field failed")
                        return success
                    }
                    targetNode.recycle()
                }
            }
            
            // For all other scenarios (single input field, non-input elements, or no multiple inputs), use normal coordinate-based tapping
            Log.d(TAG, "🖱️ Using coordinate-based tap (normal scenario)")
            val path = Path()
            path.moveTo(x, y)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION)
            gestureBuilder.addStroke(strokeDescription)

            val gesture = gestureBuilder.build()
            val success = dispatchGesture(gesture, null, null)
            Log.d(TAG, if (success) "✅ Coordinate tap dispatched" else "❌ Coordinate tap failed")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Tap error: ${e.message}", e)
            false
        }
    }

    fun performScroll(direction: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return false
            }

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()
            val centerX = screenWidth / 2
            val centerY = screenHeight / 2

            val path = Path()
            when (direction.lowercase()) {
                "up" -> {
                    // To scroll up (show content above), swipe from bottom to top
                    path.moveTo(centerX, centerY - 300)
                    path.lineTo(centerX, centerY + 300)
                }
                "down" -> {
                    // To scroll down (show content below), swipe from top to bottom
                    path.moveTo(centerX, centerY + 300)
                    path.lineTo(centerX, centerY - 300)
                }
                "left" -> {
                    // To scroll left (show content to the left), swipe from right to left
                    path.moveTo(centerX - 300, centerY)
                    path.lineTo(centerX + 300, centerY)
                }
                "right" -> {
                    // To scroll right (show content to the right), swipe from left to right
                    path.moveTo(centerX + 300, centerY)
                    path.lineTo(centerX - 300, centerY)
                }
                else -> return false
            }

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION)
            gestureBuilder.addStroke(strokeDescription)

            val gesture = gestureBuilder.build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            false
        }
    }

    fun takeScreenshot(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return takeScreenshotModern()
            } else {
                return null
            }
        } catch (e: Exception) {
            return null
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun takeScreenshotModern(): String? {
        return try {
            var resultBase64: String? = null
            val latch = CountDownLatch(1)

            val callback =
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            try {
                                val bitmap =
                                        Bitmap.wrapHardwareBuffer(
                                                result.hardwareBuffer,
                                                result.colorSpace
                                        )
                                if (bitmap != null) {
                                    resultBase64 = bitmapToBase64(bitmap)
                                    bitmap.recycle()
                                }
                                result.hardwareBuffer.close()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing screenshot: ${e.message}")
                            } finally {
                                latch.countDown()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            latch.countDown()
                        }
                    }

            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, { it.run() }, callback)

            val success = latch.await(5, TimeUnit.SECONDS)
            if (!success) {
                return null
            }

            resultBase64
        } catch (e: Exception) {
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    fun getScreenElements(): List<Map<String, Any?>> {
        return try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "⚠️ No active window found for accessibility tree")
                return emptyList()
            }

            val packageName = rootNode.packageName?.toString()
            val framework = detectAppFramework(packageName)
            Log.d(TAG, "🔍 Extracting elements for $packageName using $framework framework")

            val elements =
                    when (framework) {
                        AppFramework.FLUTTER -> extractFlutterElements(rootNode)
                        AppFramework.REACT_NATIVE -> extractReactNativeElements(rootNode)
                        AppFramework.XAMARIN -> extractXamarinElements(rootNode)
                        else -> extractNativeElements(rootNode)
                    }

            Log.d(TAG, "✅ Extracted ${elements.size} screen elements")
            rootNode.recycle()
            elements
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting screen elements: ${e.message}", e)
            emptyList()
        }
    }

    private fun extractNativeElements(rootNode: AccessibilityNodeInfo): List<Map<String, Any?>> {
        return try {
            val elements = mutableListOf<Map<String, Any?>>()
            collectNativeNodeInfo(rootNode, elements)
            Log.d(TAG, "✅ Extracted ${elements.size} native elements")
            elements
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting native elements: ${e.message}", e)
            emptyList()
        }
    }

    private fun collectNativeNodeInfo(
            node: AccessibilityNodeInfo,
            elements: MutableList<Map<String, Any?>>
    ) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!bounds.isEmpty) {
                val element = mutableMapOf<String, Any?>()
                element["framework"] = "native"
                element["type"] = node.className?.toString() ?: "unknown"
                element["text"] = node.text?.toString() ?: ""
                element["contentDescription"] = node.contentDescription?.toString() ?: ""
                element["clickable"] = node.isClickable
                element["scrollable"] = node.isScrollable
                element["editable"] = node.isEditable
                element["enabled"] = node.isEnabled
                element["focusable"] = node.isFocusable
                element["checkable"] = node.isCheckable
                element["checked"] = node.isChecked
                element["selected"] = node.isSelected
                element["bounds"] =
                        mapOf(
                                "x" to bounds.centerX(),
                                "y" to bounds.centerY(),
                                "width" to bounds.width(),
                                "height" to bounds.height(),
                                "left" to bounds.left,
                                "top" to bounds.top,
                                "right" to bounds.right,
                                "bottom" to bounds.bottom
                        )

                val resourceId = node.viewIdResourceName
                if (resourceId != null) {
                    element["resourceId"] = resourceId
                }

                if (node.isClickable ||
                                node.isScrollable ||
                                !node.text.isNullOrEmpty() ||
                                !node.contentDescription.isNullOrEmpty() ||
                                node.isEditable
                ) {
                    elements.add(element)
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectNativeNodeInfo(it, elements)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing native child node: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting native node info: ${e.message}")
        }
    }

    private fun extractFlutterElements(rootNode: AccessibilityNodeInfo): List<Map<String, Any?>> {
        val elements = mutableListOf<Map<String, Any?>>()
        collectFlutterNodeInfo(rootNode, elements)
        return elements
    }

    private fun collectFlutterNodeInfo(
            node: AccessibilityNodeInfo,
            elements: MutableList<Map<String, Any?>>
    ) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!bounds.isEmpty) {
                val element = mutableMapOf<String, Any?>()
                element["framework"] = "flutter"
                element["type"] = node.className?.toString() ?: "unknown"

                val text = node.text?.toString() ?: ""
                val contentDesc = node.contentDescription?.toString() ?: ""

                element["text"] = text
                element["contentDescription"] = contentDesc
                element["clickable"] = node.isClickable
                element["scrollable"] = node.isScrollable
                element["editable"] = node.isEditable
                element["enabled"] = node.isEnabled
                element["focusable"] = node.isFocusable
                element["bounds"] =
                        mapOf(
                                "x" to bounds.centerX(),
                                "y" to bounds.centerY(),
                                "width" to bounds.width(),
                                "height" to bounds.height(),
                                "left" to bounds.left,
                                "top" to bounds.top,
                                "right" to bounds.right,
                                "bottom" to bounds.bottom
                        )

                val actions = mutableListOf<String>()
                if (node.actionList != null) {
                    for (action in node.actionList) {
                        actions.add(action.toString())
                    }
                }
                element["actions"] = actions

                if (isFlutterWidget(node)) {
                    element["flutterWidget"] = true
                    extractFlutterSpecificInfo(node, element)
                }

                if (node.isClickable ||
                                node.isScrollable ||
                                text.isNotEmpty() ||
                                contentDesc.isNotEmpty() ||
                                node.isEditable ||
                                isFlutterWidget(node)
                ) {
                    elements.add(element)
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectFlutterNodeInfo(it, elements)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing Flutter child node: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting Flutter node info: ${e.message}")
        }
    }

    private fun isFlutterWidget(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        return className.contains("flutter", ignoreCase = true) ||
                className.contains("semantics", ignoreCase = true) ||
                node.contentDescription?.toString()?.contains("flutter", ignoreCase = true) == true
    }

    private fun extractFlutterSpecificInfo(
            node: AccessibilityNodeInfo,
            element: MutableMap<String, Any?>
    ) {
        try {
            val flutterInfo = mutableMapOf<String, Any?>()
            val extras = node.extras
            for (key in extras.keySet()) {
                try {
                    flutterInfo[key] = extras.get(key)
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting Flutter extra: $key")
                }
            }

            if (flutterInfo.isNotEmpty()) {
                element["flutterExtras"] = flutterInfo
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting Flutter specific info: ${e.message}")
        }
    }

    private fun extractReactNativeElements(
            rootNode: AccessibilityNodeInfo
    ): List<Map<String, Any?>> {
        val elements = mutableListOf<Map<String, Any?>>()
        collectReactNativeNodeInfo(rootNode, elements)
        return elements
    }

    private fun collectReactNativeNodeInfo(
            node: AccessibilityNodeInfo,
            elements: MutableList<Map<String, Any?>>
    ) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!bounds.isEmpty) {
                val element = mutableMapOf<String, Any?>()
                element["framework"] = "react_native"
                element["type"] = node.className?.toString() ?: "unknown"

                val text = node.text?.toString() ?: ""
                val contentDesc = node.contentDescription?.toString() ?: ""

                element["text"] = text
                element["contentDescription"] = contentDesc
                element["clickable"] = node.isClickable
                element["scrollable"] = node.isScrollable
                element["editable"] = node.isEditable
                element["enabled"] = node.isEnabled
                element["focusable"] = node.isFocusable
                element["bounds"] =
                        mapOf(
                                "x" to bounds.centerX(),
                                "y" to bounds.centerY(),
                                "width" to bounds.width(),
                                "height" to bounds.height(),
                                "left" to bounds.left,
                                "top" to bounds.top,
                                "right" to bounds.right,
                                "bottom" to bounds.bottom
                        )

                if (isReactNativeComponent(node)) {
                    element["reactNativeComponent"] = true
                    extractReactNativeSpecificInfo(node, element)
                }

                val resourceId = node.viewIdResourceName
                if (resourceId != null) {
                    element["resourceId"] = resourceId
                }

                if (node.isClickable ||
                                node.isScrollable ||
                                text.isNotEmpty() ||
                                contentDesc.isNotEmpty() ||
                                node.isEditable ||
                                isReactNativeComponent(node)
                ) {
                    elements.add(element)
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectReactNativeNodeInfo(it, elements)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing React Native child node: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting React Native node info: ${e.message}")
        }
    }

    private fun isReactNativeComponent(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        return className.contains("react", ignoreCase = true) ||
                className.contains("RCT", ignoreCase = true) ||
                node.contentDescription?.toString()?.contains("RCT", ignoreCase = true) == true
    }

    private fun extractReactNativeSpecificInfo(
            node: AccessibilityNodeInfo,
            element: MutableMap<String, Any?>
    ) {
        try {
            val actions = mutableListOf<String>()
            if (node.actionList != null) {
                for (action in node.actionList) {
                    actions.add(action.toString())
                }
            }
            element["reactNativeActions"] = actions

            val rnInfo = mutableMapOf<String, Any?>()
            val extras = node.extras
            for (key in extras.keySet()) {
                try {
                    rnInfo[key] = extras.get(key)
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting React Native extra: $key")
                }
            }

            if (rnInfo.isNotEmpty()) {
                element["reactNativeExtras"] = rnInfo
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting React Native specific info: ${e.message}")
        }
    }

    private fun extractXamarinElements(rootNode: AccessibilityNodeInfo): List<Map<String, Any?>> {
        val elements = mutableListOf<Map<String, Any?>>()
        collectXamarinNodeInfo(rootNode, elements)
        return elements
    }

    private fun collectXamarinNodeInfo(
            node: AccessibilityNodeInfo,
            elements: MutableList<Map<String, Any?>>
    ) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!bounds.isEmpty) {
                val element = mutableMapOf<String, Any?>()
                element["framework"] = "xamarin"
                element["type"] = node.className?.toString() ?: "unknown"
                element["text"] = node.text?.toString() ?: ""
                element["contentDescription"] = node.contentDescription?.toString() ?: ""
                element["clickable"] = node.isClickable
                element["scrollable"] = node.isScrollable
                element["editable"] = node.isEditable
                element["enabled"] = node.isEnabled
                element["focusable"] = node.isFocusable
                element["bounds"] =
                        mapOf(
                                "x" to bounds.centerX(),
                                "y" to bounds.centerY(),
                                "width" to bounds.width(),
                                "height" to bounds.height(),
                                "left" to bounds.left,
                                "top" to bounds.top,
                                "right" to bounds.right,
                                "bottom" to bounds.bottom
                        )

                val resourceId = node.viewIdResourceName
                if (resourceId != null) {
                    element["resourceId"] = resourceId
                }

                if (node.isClickable ||
                                node.isScrollable ||
                                !node.text.isNullOrEmpty() ||
                                !node.contentDescription.isNullOrEmpty() ||
                                node.isEditable
                ) {
                    elements.add(element)
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectXamarinNodeInfo(it, elements)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing Xamarin child node: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting Xamarin node info: ${e.message}")
        }
    }

    fun getAllTextContent(): List<String> {
        return try {
            val rootNode = rootInActiveWindow ?: return emptyList()
            val framework = detectAppFramework(rootNode.packageName?.toString())

            val textContent =
                    when (framework) {
                        AppFramework.FLUTTER -> extractAllFlutterText(rootNode)
                        AppFramework.REACT_NATIVE -> extractAllReactNativeText(rootNode)
                        AppFramework.XAMARIN -> extractAllXamarinText(rootNode)
                        else -> extractAllNativeText(rootNode)
                    }

            rootNode.recycle()
            textContent.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractAllNativeText(rootNode: AccessibilityNodeInfo): List<String> {
        val textList = mutableListOf<String>()
        collectAllText(rootNode, textList)
        return textList
    }

    private fun extractAllFlutterText(rootNode: AccessibilityNodeInfo): List<String> {
        val textList = mutableListOf<String>()
        collectAllFlutterText(rootNode, textList)
        return textList
    }

    private fun extractAllReactNativeText(rootNode: AccessibilityNodeInfo): List<String> {
        val textList = mutableListOf<String>()
        collectAllReactNativeText(rootNode, textList)
        return textList
    }

    private fun extractAllXamarinText(rootNode: AccessibilityNodeInfo): List<String> {
        val textList = mutableListOf<String>()
        collectAllText(rootNode, textList)
        return textList
    }

    private fun collectAllText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        try {
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()

            if (!text.isNullOrEmpty()) {
                textList.add(text)
            }
            if (!contentDesc.isNullOrEmpty()) {
                textList.add(contentDesc)
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectAllText(it, textList)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing child for text: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting text: ${e.message}")
        }
    }

    private fun collectAllFlutterText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        try {
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()

            if (!text.isNullOrEmpty()) {
                textList.add(text)
            }
            if (!contentDesc.isNullOrEmpty()) {
                textList.add(contentDesc)
            }

            if (isFlutterWidget(node)) {
                val extras = node.extras
                for (key in extras.keySet()) {
                    try {
                        val value = extras.get(key)?.toString()
                        if (!value.isNullOrEmpty() && value != "null") {
                            textList.add(value)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error extracting Flutter text from extras: ${e.message}")
                    }
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectAllFlutterText(it, textList)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing Flutter child for text: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting Flutter text: ${e.message}")
        }
    }

    private fun collectAllReactNativeText(
            node: AccessibilityNodeInfo,
            textList: MutableList<String>
    ) {
        try {
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()

            if (!text.isNullOrEmpty()) {
                textList.add(text)
            }
            if (!contentDesc.isNullOrEmpty()) {
                textList.add(contentDesc)
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        collectAllReactNativeText(it, textList)
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing React Native child for text: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting React Native text: ${e.message}")
        }
    }

    fun findAndClick(text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false

            val targetNode = findClickableNativeNode(rootNode, text)

            val success =
                    if (targetNode != null) {
                        val result = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        targetNode.recycle()
                        result
                    } else {
                        false
                    }

            rootNode.recycle()
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun findClickableNativeNode(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        // First try to find a directly clickable node with the text
        val directClickable = findClickableNodeWithText(node, targetText)
        if (directClickable != null) return directClickable

        // If not found, find any node with text and search for nearby clickable
        val textNode = findNodeWithText(node, targetText)
        if (textNode != null) {
            val clickableParent = findClickableParent(textNode)
            if (clickableParent != null) {
                textNode.recycle()
                return clickableParent
            }

            val clickableNearby = findClickableNearNode(node, textNode, 200)
            textNode.recycle()
            return clickableNearby
        }

        return null
    }

    private fun findClickableFlutterNode(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        return findClickableNativeNode(node, targetText)
    }

    private fun findClickableReactNativeNode(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        return findClickableNativeNode(node, targetText)
    }

    private fun findClickableXamarinNode(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        return findClickableNativeNode(node, targetText)
    }

    private fun findClickableNodeWithText(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        return try {
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""

            if ((nodeText.contains(targetText, ignoreCase = true) ||
                            nodeDesc.contains(targetText, ignoreCase = true)) && node.isClickable
            ) {
                return node
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        val result = findClickableNodeWithText(it, targetText)
                        if (result != null) {
                            it.recycle()
                            return result
                        }
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching clickable child node: ${e.message}")
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error searching for clickable node: ${e.message}")
            null
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return try {
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    return parent
                }
                val nextParent = parent.parent
                parent.recycle()
                parent = nextParent
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error finding clickable parent: ${e.message}")
            null
        }
    }

    private fun findClickableNearNode(
            rootNode: AccessibilityNodeInfo,
            targetNode: AccessibilityNodeInfo,
            maxDistance: Int
    ): AccessibilityNodeInfo? {
        return try {
            val targetBounds = Rect()
            targetNode.getBoundsInScreen(targetBounds)

            findNearestClickable(rootNode, targetBounds, maxDistance)
        } catch (e: Exception) {
            Log.w(TAG, "Error finding clickable near node: ${e.message}")
            null
        }
    }

    private fun findNearestClickable(
            node: AccessibilityNodeInfo,
            targetBounds: Rect,
            maxDistance: Int
    ): AccessibilityNodeInfo? {
        try {
            if (node.isClickable) {
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)

                val distance = calculateDistance(targetBounds, nodeBounds)
                if (distance <= maxDistance) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        val result = findNearestClickable(it, targetBounds, maxDistance)
                        if (result != null) {
                            it.recycle()
                            return result
                        }
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching child in findNearestClickable: ${e.message}")
                }
            }

            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error in findNearestClickable: ${e.message}")
            return null
        }
    }

    private fun calculateDistance(bounds1: Rect, bounds2: Rect): Int {
        val centerX1 = bounds1.centerX()
        val centerY1 = bounds1.centerY()
        val centerX2 = bounds2.centerX()
        val centerY2 = bounds2.centerY()

        val dx = centerX1 - centerX2
        val dy = centerY1 - centerY2

        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
    }

    private fun findAndClickSubmitButton(rootNode: AccessibilityNodeInfo): Boolean {
        // Look for common submit button texts
        val submitTexts = listOf(
            "send", "submit", "enter", "go", "search", "ok", "done", 
            "post", "reply", "comment", "message", "chat", "→", "➤"
        )
        
        for (text in submitTexts) {
            if (findAndClick(text)) {
                return true
            }
        }
        
        // Look for buttons with content descriptions
        val submitDescriptions = listOf(
            "send", "submit", "enter", "go", "search", "send message", "submit form"
        )
        
        for (description in submitDescriptions) {
            val nodes = findNodesByContentDescription(rootNode, description)
            for (node in nodes) {
                if (node.isClickable) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val result = performTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    node.recycle()
                    if (result) return true
                }
            }
        }
        
        return false
    }

    fun findAndClickWithFallback(text: String): Boolean {
        return try {
            // Strategy 1: Try normal findAndClick
            if (findAndClick(text)) return true

            // Strategy 2: Try clicking by coordinates if element found but not clickable
            val rootNode = rootInActiveWindow ?: return false
            val textNode = findNodeWithText(rootNode, text)

            if (textNode != null) {
                val bounds = Rect()
                textNode.getBoundsInScreen(bounds)
                textNode.recycle()
                rootNode.recycle()

                // Try tapping at the center of the text element
                return performTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }

            // Strategy 3: Try partial text match
            rootNode.recycle()
            return findAndClickPartial(text)
        } catch (e: Exception) {
            false
        }
    }

    fun findAndClickPartial(text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val clickableNode = findClickableWithPartialText(rootNode, text)

            val success =
                    if (clickableNode != null) {
                        val result = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        clickableNode.recycle()
                        result
                    } else {
                        false
                    }

            rootNode.recycle()
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun findClickableWithPartialText(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        return try {
            if (node.isClickable) {
                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""

                if (nodeText.contains(targetText, ignoreCase = true) ||
                                nodeDesc.contains(targetText, ignoreCase = true)
                ) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        val result = findClickableWithPartialText(it, targetText)
                        if (result != null) {
                            it.recycle()
                            return result
                        }
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching partial text child: ${e.message}")
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error finding clickable with partial text: ${e.message}")
            null
        }
    }

    fun smartClick(text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val framework = detectAppFramework(rootNode.packageName?.toString())

            // Framework-specific smart clicking
            val success =
                    when (framework) {
                        AppFramework.FLUTTER -> smartClickFlutter(rootNode, text)
                        AppFramework.REACT_NATIVE -> smartClickReactNative(rootNode, text)
                        AppFramework.XAMARIN -> smartClickXamarin(rootNode, text)
                        else -> smartClickNative(rootNode, text)
                    }

            rootNode.recycle()
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun smartClickNative(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        // Try multiple strategies in order
        val strategies =
                listOf(
                        { findClickableNodeWithText(rootNode, text) },
                        { findTextNodeAndClickParent(rootNode, text) },
                        { findTextNodeAndClickNearby(rootNode, text) },
                        { findClickableByBounds(rootNode, text) }
                )

        for (strategy in strategies) {
            try {
                val node = strategy()
                if (node != null) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    if (result) return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Strategy failed: ${e.message}")
            }
        }
        return false
    }

    private fun smartClickFlutter(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        return smartClickNative(rootNode, text)
    }

    private fun smartClickReactNative(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        return smartClickNative(rootNode, text)
    }

    private fun smartClickXamarin(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        return smartClickNative(rootNode, text)
    }

    private fun findTextNodeAndClickParent(
            rootNode: AccessibilityNodeInfo,
            text: String
    ): AccessibilityNodeInfo? {
        val textNode = findNodeWithText(rootNode, text)
        if (textNode != null) {
            val clickableParent = findClickableParent(textNode)
            textNode.recycle()
            return clickableParent
        }
        return null
    }

    private fun findTextNodeAndClickNearby(
            rootNode: AccessibilityNodeInfo,
            text: String
    ): AccessibilityNodeInfo? {
        val textNode = findNodeWithText(rootNode, text)
        if (textNode != null) {
            val nearbyClickable = findClickableNearNode(rootNode, textNode, 150)
            textNode.recycle()
            return nearbyClickable
        }
        return null
    }

    private fun findClickableByBounds(
            rootNode: AccessibilityNodeInfo,
            text: String
    ): AccessibilityNodeInfo? {
        val textNode = findNodeWithText(rootNode, text)
        if (textNode != null) {
            val bounds = Rect()
            textNode.getBoundsInScreen(bounds)
            textNode.recycle()

            // Find any clickable element that overlaps with text bounds
            return findClickableInBounds(rootNode, bounds)
        }
        return null
    }

    private fun findClickableInBounds(
            node: AccessibilityNodeInfo,
            targetBounds: Rect
    ): AccessibilityNodeInfo? {
        try {
            if (node.isClickable) {
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)

                if (Rect.intersects(nodeBounds, targetBounds)) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        val result = findClickableInBounds(it, targetBounds)
                        if (result != null) {
                            it.recycle()
                            return result
                        }
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching bounds child: ${e.message}")
                }
            }
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error finding clickable in bounds: ${e.message}")
            return null
        }
    }

    fun performPinch(
            centerX: Float,
            centerY: Float,
            startDistance: Float,
            endDistance: Float
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return false
            }

            val gestureBuilder = GestureDescription.Builder()

            val path1 = Path()
            path1.moveTo(centerX - startDistance / 2, centerY)
            path1.lineTo(centerX - endDistance / 2, centerY)

            val path2 = Path()
            path2.moveTo(centerX + startDistance / 2, centerY)
            path2.lineTo(centerX + endDistance / 2, centerY)

            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 500L)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, 500L)

            gestureBuilder.addStroke(stroke1)
            gestureBuilder.addStroke(stroke2)

            val gesture = gestureBuilder.build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            false
        }
    }

    fun performDoubleClick(x: Float, y: Float): Boolean {
        return try {
            val success1 = performTap(x, y)
            Thread.sleep(100)
            val success2 = performTap(x, y)
            success1 && success2
        } catch (e: Exception) {
            false
        }
    }

    fun sendKeyEvent(keyCode: Int): Boolean {
        return try {
            when (keyCode) {
                4 -> performGlobalAction(GLOBAL_ACTION_BACK) // KEYCODE_BACK
                3 -> performGlobalAction(GLOBAL_ACTION_HOME) // KEYCODE_HOME
                187 -> performGlobalAction(GLOBAL_ACTION_RECENTS) // KEYCODE_APP_SWITCH
                else -> {
                    val focusedNode =
                            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedNode != null) {
                        when (keyCode) {
                            66 -> { // KEYCODE_ENTER
                                // First focus the text field
                                focusedNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                                // Then trigger the IME action (API 30+ only)
                                val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    focusedNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                                } else {
                                    // For older API levels, use click action as fallback
                                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                }
                                focusedNode.recycle()
                                return result
                            }
                            67 -> { // KEYCODE_DEL
                                val result =
                                        focusedNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                                focusedNode.recycle()
                                return result
                            }
                            112 -> { // KEYCODE_FORWARD_DEL
                                val result =
                                        focusedNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                                focusedNode.recycle()
                                return result
                            }
                            else -> {
                                focusedNode.recycle()
                                return false
                            }
                        }
                    }
                    return false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun findNodeWithText(
            node: AccessibilityNodeInfo,
            targetText: String
    ): AccessibilityNodeInfo? {
        return try {
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""

            if (nodeText.contains(targetText, ignoreCase = true) ||
                            nodeDesc.contains(targetText, ignoreCase = true)
            ) {
                return node
            }

            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    child?.let {
                        val result = findNodeWithText(it, targetText)
                        if (result != null) {
                            it.recycle()
                            return result
                        }
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching child node: ${e.message}")
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error searching for node: ${e.message}")
            null
        }
    }

    fun getCurrentAppPackage(): String? {
        return try {
            val rootNode = rootInActiveWindow
            val packageName = rootNode?.packageName?.toString()
            rootNode?.recycle()
            packageName
        } catch (e: Exception) {
            null
        }
    }

    fun getAppFrameworkInfo(): Map<String, Any?> {
        return try {
            val rootNode = rootInActiveWindow ?: return mapOf("error" to "No root node")
            val packageName = rootNode.packageName?.toString()
            val framework = detectAppFramework(packageName)

            val info = mutableMapOf<String, Any?>()
            info["package"] = packageName
            info["framework"] = framework.toString()
            info["detected_patterns"] =
                    when (framework) {
                        AppFramework.FLUTTER -> listOf("FlutterView", "flutter.view", "io.flutter")
                        AppFramework.REACT_NATIVE ->
                                listOf("ReactRootView", "ReactNativeHost", "RCT")
                        AppFramework.XAMARIN -> listOf("xamarin", "Xamarin")
                        else -> listOf("native")
                    }

            rootNode.recycle()
            info
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    fun performBack(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            false
        }
    }

    fun performHome(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            false
        }
    }

    fun performLongPress(x: Float, y: Float, duration: Long = 1000L): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return false
            }

            val path = Path()
            path.moveTo(x, y)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)

            val gesture = gestureBuilder.build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            false
        }
    }

    // Overlay detection and node-based interaction to prevent coordinate tap interference
    fun hasVisibleOverlay(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return scanForOverlays(rootNode)
    }

    private fun scanForOverlays(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        if ((className.contains("ListView") || className.contains("RecyclerView")) && node.isVisibleToUser) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && scanForOverlays(child)) {
                child.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    fun performNodeBasedClick(targetNode: AccessibilityNodeInfo): Boolean {
        return try {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            false
        }
    }

    fun performNodeBasedTextInput(targetNode: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (e: Exception) {
            false
        }
    }

    private fun findNodeAtCoordinates(node: AccessibilityNodeInfo, x: Float, y: Float): AccessibilityNodeInfo? {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            // Check if coordinates are within this node's bounds
            if (bounds.contains(x.toInt(), y.toInt())) {
                // First check children for more specific matches
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val childResult = findNodeAtCoordinates(child, x, y)
                    if (childResult != null) {
                        child.recycle()
                        return childResult
                    }
                    child.recycle()
                }
                
                // If no child matches, return this node if it's clickable
                if (node.isClickable) {
                    return node
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    fun performSwipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            duration: Long = 300L
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return false
            }

            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)

            val gesture = gestureBuilder.build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            false
        }
    }

    fun injectTextWithoutTap(text: String, fieldId: String? = null): Boolean {
        return try {
            Log.d(TAG, "🎯 Attempting text injection: '$text'${if (fieldId != null) " to field: $fieldId" else ""}")
            
            // Check for visible overlays first
            val hasOverlay = hasVisibleOverlay()
            Log.d(TAG, if (hasOverlay) "⚠️ Overlay detected - using node-based approach" else "✅ No overlay detected")
            
            // First try to use currently focused input if no specific field is requested
            if (fieldId == null && currentFocusedInput != null) {
                val success = performNodeBasedTextInput(currentFocusedInput!!, text)
                Log.d(TAG, if (success) "✅ Text injection to focused field successful: '$text'" else "❌ Text injection to focused field failed: '$text'")
                return success
            }
            
            val rootNode = rootInActiveWindow ?: return false
            
            // Find target field by ID or use first editable field
            val editableNode = if (fieldId != null) {
                findEditableNodeById(rootNode, fieldId)
            } else {
                findEditableNode(rootNode)
            }
            
            if (editableNode != null) {
                // Always use node-based text input to avoid coordinate interference
                val success = performNodeBasedTextInput(editableNode, text)
                editableNode.recycle()
                Log.d(TAG, if (success) "✅ Node-based text injection successful: '$text'" else "❌ Node-based text injection failed: '$text'")
                return success
            } else {
                Log.w(TAG, "⚠️ No editable text field found for injection")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Text injection error: ${e.message}", e)
            false
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            // Check if current node is editable
            if (node.isEditable && node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
                return node
            }
            
            // Search children recursively
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                child?.let {
                    val result = findEditableNode(it)
                    if (result != null) {
                        it.recycle()
                        return result
                    }
                    it.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding editable node: ${e.message}")
        }
        return null
    }
    
    private fun findEditableNodeById(node: AccessibilityNodeInfo, targetId: String): AccessibilityNodeInfo? {
        try {
            // Check if current node matches the target ID and is editable
            if (isEditableNode(node) && node.viewIdResourceName == targetId) {
                return node
            }
            
            // Search children recursively
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                child?.let {
                    val result = findEditableNodeById(it, targetId)
                    if (result != null) {
                        it.recycle()
                        return result
                    }
                    it.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding editable node by ID: ${e.message}")
        }
        return null
    }

    fun typeText(text: String): Boolean {
        return try {
            val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null) {
                // Prefer ACTION_SET_TEXT if supported
                val canSetText = focusedNode.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
                if (canSetText) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text
                    )
                    val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    if (success) {
                        focusedNode.recycle()
                        return true
                    }
                }

                // Try append strategy via ACTION_SET_TEXT (simulate typing without clipboard)
                val appended = appendTextOnFocused(text)
                if (appended) {
                    focusedNode.recycle()
                    return true
                }

                // No clipboard fallback; give up to avoid altering clipboard
                focusedNode.recycle()
                return false
            } else {
                return false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Append text to currently focused input using ACTION_SET_TEXT without touching clipboard */
    fun appendTextOnFocused(text: String): Boolean {
        return try {
            val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode == null) return false
            val canSetText = focusedNode.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
            if (!canSetText) {
                focusedNode.recycle()
                return false
            }
            val existing = focusedNode.text?.toString() ?: ""
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                existing + text
            )
            val ok = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** Slowly append text per character using ACTION_SET_TEXT. Returns true if entire text was appended. */
    fun appendTextOnFocusedSlow(text: String, delayMs: Long = 35L): Boolean {
        return try {
            val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode == null) return false
            val canSetText = focusedNode.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
            if (!canSetText) {
                focusedNode.recycle()
                return false
            }
            var buffer = focusedNode.text?.toString() ?: ""
            for (ch in text) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    buffer + ch
                )
                val ok = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (!ok) {
                    focusedNode.recycle()
                    return false
                }
                buffer += ch
                if (delayMs > 0) Thread.sleep(delayMs)
            }
            focusedNode.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setTextOnFirstEditable(text: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val target = findFirstEditableNode(root)
            if (target != null) {
                // Try ACTION_SET_TEXT first on the target node
                val canSetText = target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
                if (canSetText) {
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    target.recycle()
                    root.recycle()
                    return ok
                }
                // No clipboard fallback on target node; fail gracefully
                target.recycle()
                root.recycle()
                false
            } else {
                root.recycle()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Move focus to the next editable field (best-effort) */
    fun focusNextEditable(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val editables = collectEditableNodes(root)
            if (editables.isEmpty()) { root.recycle(); return false }

            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            var focusedIdx = -1
            if (focused != null) {
                for (i in editables.indices) {
                    if (nodeBoundsEqual(editables[i], focused)) { focusedIdx = i; break }
                }
            }

            val nextIdx = if (focusedIdx >= 0 && focusedIdx + 1 < editables.size) focusedIdx + 1 else -1
            if (nextIdx == -1) {
                // recycle
                focused?.recycle()
                for (n in editables) n.recycle()
                root.recycle()
                return false
            }

            val next = editables[nextIdx]
            val moved = focusOrClickNode(next)
            focused?.recycle()
            for (n in editables) if (n != next) n.recycle()
            next.recycle()
            root.recycle()
            moved
        } catch (e: Exception) {
            false
        }
    }

    private fun collectEditableNodes(root: AccessibilityNodeInfo): MutableList<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                val editable = node.isEditable || (node.className?.toString()?.lowercase()?.contains("edittext") == true)
                if (editable) {
                    list.add(AccessibilityNodeInfo.obtain(node))
                }
                for (i in 0 until node.childCount) {
                    dfs(node.getChild(i))
                }
            } catch (_: Exception) {
            }
        }
        dfs(root)
        return list
    }

    private fun nodeBoundsEqual(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
        return try {
            val ra = android.graphics.Rect(); a.getBoundsInScreen(ra)
            val rb = android.graphics.Rect(); b.getBoundsInScreen(rb)
            ra == rb
        } catch (_: Exception) { false }
    }

    private fun focusOrClickNode(node: AccessibilityNodeInfo): Boolean {
        try {
            // Try focus action first
            if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_FOCUS }) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return true
            }
            // Then click
            if (node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            // Fallback: tap center
            val r = android.graphics.Rect(); node.getBoundsInScreen(r)
            return performTap((r.centerX()).toFloat(), (r.centerY()).toFloat())
        } catch (_: Exception) {
            return false
        }
    }

    fun focusFirstEditable(): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val target = findFirstEditableNode(rootNode)
            if (target != null) {
                // Try to focus; if not possible, click
                val focused = target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val clicked = if (!focused) target.performAction(AccessibilityNodeInfo.ACTION_CLICK) else true
                target.recycle()
                rootNode.recycle()
                focused || clicked
            } else {
                rootNode.recycle()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            if (node.isEditable || (node.className?.toString()?.contains("EditText", true) == true)) {
                return AccessibilityNodeInfo.obtain(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findFirstEditableNode(child)
                if (found != null) return found
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun waitForElement(text: String, timeout: Long = 5000L): AccessibilityNodeInfo? {
        return try {
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeout) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val foundNode = findNodeWithText(rootNode, text)
                    if (foundNode != null) {
                        rootNode.recycle()
                        return foundNode
                    }
                    rootNode.recycle()
                }
                Thread.sleep(500)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun getDetailedScreenInfo(): Map<String, Any?> {
        return try {
            val info = mutableMapOf<String, Any?>()
            val rootNode = rootInActiveWindow

            if (rootNode != null) {
                val bounds = Rect()
                rootNode.getBoundsInScreen(bounds)
                val packageName = rootNode.packageName?.toString()
                val framework = detectAppFramework(packageName)

                info["screen_bounds"] =
                        mapOf("width" to bounds.width(), "height" to bounds.height())
                info["current_package"] = packageName ?: "unknown"
                info["framework"] = framework.toString()

                val elements =
                        when (framework) {
                            AppFramework.FLUTTER -> extractFlutterElements(rootNode)
                            AppFramework.REACT_NATIVE -> extractReactNativeElements(rootNode)
                            AppFramework.XAMARIN -> extractXamarinElements(rootNode)
                            else -> extractNativeElements(rootNode)
                        }

                info["total_elements"] = elements.size
                info["clickable_elements"] = elements.count { it["clickable"] == true }
                info["text_elements"] = elements.count { !(it["text"] as? String).isNullOrEmpty() }
                info["framework_specific_elements"] =
                        elements.count {
                            it.containsKey("flutterWidget") ||
                                    it.containsKey("reactNativeComponent")
                        }

                rootNode.recycle()
            } else {
                info["error"] = "No root node available"
            }

            info
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    fun extractFrameworkSpecificContent(): Map<String, Any?> {
        return try {
            val rootNode = rootInActiveWindow ?: return mapOf("error" to "No root node")
            val packageName = rootNode.packageName?.toString()
            val framework = detectAppFramework(packageName)

            val content = mutableMapOf<String, Any?>()
            content["framework"] = framework.toString()
            content["package"] = packageName

            when (framework) {
                AppFramework.FLUTTER -> {
                    content["flutter_widgets"] = extractFlutterWidgetInfo(rootNode)
                    content["flutter_text"] = extractAllFlutterText(rootNode)
                }
                AppFramework.REACT_NATIVE -> {
                    content["react_components"] = extractReactNativeComponentInfo(rootNode)
                    content["react_text"] = extractAllReactNativeText(rootNode)
                }
                AppFramework.XAMARIN -> {
                    content["xamarin_controls"] = extractXamarinControlInfo(rootNode)
                    content["xamarin_text"] = extractAllXamarinText(rootNode)
                }
                else -> {
                    content["native_views"] = extractNativeViewInfo(rootNode)
                    content["native_text"] = extractAllNativeText(rootNode)
                }
            }

            rootNode.recycle()
            content
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    private fun extractFlutterWidgetInfo(rootNode: AccessibilityNodeInfo): List<Map<String, Any?>> {
        val widgets = mutableListOf<Map<String, Any?>>()
        collectFlutterWidgets(rootNode, widgets)
        return widgets
    }

    private fun collectFlutterWidgets(
            node: AccessibilityNodeInfo,
            widgets: MutableList<Map<String, Any?>>
    ) {
        try {
            if (isFlutterWidget(node)) {
                val widget = mutableMapOf<String, Any?>()
                widget["type"] = node.className?.toString()
                widget["text"] = node.text?.toString()
                widget["contentDescription"] = node.contentDescription?.toString()

                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                widget["bounds"] =
                        mapOf(
                                "x" to bounds.centerX(),
                                "y" to bounds.centerY(),
                                "width" to bounds.width(),
                                "height" to bounds.height()
                        )

                extractFlutterSpecificInfo(node, widget)
                widgets.add(widget)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectFlutterWidgets(child, widgets)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting Flutter widgets: ${e.message}")
        }
    }

    private fun extractReactNativeComponentInfo(
            rootNode: AccessibilityNodeInfo
    ): List<Map<String, Any?>> {
        val components = mutableListOf<Map<String, Any?>>()
        collectReactNativeComponents(rootNode, components)
        return components
    }

    private fun collectReactNativeComponents(
            node: AccessibilityNodeInfo,
            components: MutableList<Map<String, Any?>>
    ) {
        try {
            if (isReactNativeComponent(node)) {
                val component = mutableMapOf<String, Any?>()
                component["type"] = node.className?.toString()
                component["text"] = node.text?.toString()
                component["contentDescription"] = node.contentDescription?.toString()

                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                component["bounds"] =
                        mapOf(
                                "x" to bounds.centerX(),
                                "y" to bounds.centerY(),
                                "width" to bounds.width(),
                                "height" to bounds.height()
                        )

                components.add(component)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectReactNativeComponents(child, components)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting React Native components: ${e.message}")
        }
    }

    private fun extractXamarinControlInfo(
            rootNode: AccessibilityNodeInfo
    ): List<Map<String, Any?>> {
        val controls = mutableListOf<Map<String, Any?>>()
        collectXamarinControls(rootNode, controls)
        return controls
    }

    private fun collectXamarinControls(
            node: AccessibilityNodeInfo,
            controls: MutableList<Map<String, Any?>>
    ) {
        try {
            val control = mutableMapOf<String, Any?>()
            control["type"] = node.className?.toString()
            control["text"] = node.text?.toString()
            control["contentDescription"] = node.contentDescription?.toString()

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            control["bounds"] =
                    mapOf(
                            "x" to bounds.centerX(),
                            "y" to bounds.centerY(),
                            "width" to bounds.width(),
                            "height" to bounds.height()
                    )

            if (node.isClickable || node.isScrollable || !node.text.isNullOrEmpty()) {
                controls.add(control)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectXamarinControls(child, controls)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting Xamarin controls: ${e.message}")
        }
    }

    private fun extractNativeViewInfo(rootNode: AccessibilityNodeInfo): List<Map<String, Any?>> {
        val views = mutableListOf<Map<String, Any?>>()
        collectNativeViews(rootNode, views)
        return views
    }

    private fun collectNativeViews(
            node: AccessibilityNodeInfo,
            views: MutableList<Map<String, Any?>>
    ) {
        try {
            val view = mutableMapOf<String, Any?>()
            view["type"] = node.className?.toString()
            view["text"] = node.text?.toString()
            view["contentDescription"] = node.contentDescription?.toString()
            view["resourceId"] = node.viewIdResourceName

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            view["bounds"] =
                    mapOf(
                            "x" to bounds.centerX(),
                            "y" to bounds.centerY(),
                            "width" to bounds.width(),
                            "height" to bounds.height()
                    )

            if (node.isClickable || node.isScrollable || !node.text.isNullOrEmpty()) {
                views.add(view)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectNativeViews(child, views)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting native views: ${e.message}")
        }
    }

    /**
     * Launch an app by package name using accessibility service privileges
     * This can work even when the calling app is in the background
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val packageManager = applicationContext.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                applicationContext.startActivity(launchIntent)
                Log.d(TAG, "Successfully launched app: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app $packageName: ${e.message}")
            false
        }
    }

    /**
     * Launch an app by app name using accessibility service privileges
     * This can work even when the calling app is in the background
     * Only launches apps that have a launch intent (are launchable)
     */
    fun launchAppByName(appName: String): Boolean {
        return try {
            val packageManager = applicationContext.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            // Collect all matching apps with their labels and sort by specificity
            val matchingApps = mutableListOf<Pair<ApplicationInfo, String>>()
            val appsWithoutIntent = mutableListOf<String>()
            
            Log.d(TAG, "Searching for app: '$appName'")
            
            for (appInfo in installedApps) {
                val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                
                // Check if this app matches our search query
                val isExactMatch = appLabel.equals(appName, ignoreCase = true)
                val isPartialMatch = appLabel.contains(appName, ignoreCase = true)
                
                if (isExactMatch || isPartialMatch) {
                    // Check if app has launch intent
                    val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        if (isExactMatch) {
                            // Exact match - highest priority
                            Log.d(TAG, "Found exact match with launch intent: '$appLabel' (${appInfo.packageName})")
                            matchingApps.add(0, Pair(appInfo, appLabel))
                        } else {
                            // Partial match - add to list
                            Log.d(TAG, "Found partial match with launch intent: '$appLabel' (${appInfo.packageName})")
                            matchingApps.add(Pair(appInfo, appLabel))
                        }
                    } else {
                        // Log apps without launch intent for debugging
                        val matchType = if (isExactMatch) "exact" else "partial"
                        Log.w(TAG, "Found $matchType match but NO launch intent: '$appLabel' (${appInfo.packageName})")
                        appsWithoutIntent.add("$appLabel (${appInfo.packageName})")
                    }
                }
            }
            
            // Log summary of findings
            Log.d(TAG, "Search results for '$appName': ${matchingApps.size} launchable apps, ${appsWithoutIntent.size} non-launchable apps")
            if (appsWithoutIntent.isNotEmpty()) {
                Log.w(TAG, "Apps without launch intent: ${appsWithoutIntent.joinToString(", ")}")
            }
            
            // Sort partial matches by length (longer names first for more specific matches)
            if (matchingApps.size > 1) {
                val exactMatches = matchingApps.filter { it.second.equals(appName, ignoreCase = true) }
                val partialMatches = matchingApps.filter { !it.second.equals(appName, ignoreCase = true) }
                    .sortedByDescending { it.second.length }
                matchingApps.clear()
                matchingApps.addAll(exactMatches)
                matchingApps.addAll(partialMatches)
            }
            
            // Try to launch the best match
            if (matchingApps.isNotEmpty()) {
                val bestMatch = matchingApps.first()
                Log.d(TAG, "Launching best match: '${bestMatch.second}' for query: '$appName'")
                return launchApp(bestMatch.first.packageName)
            }
            
            // If no launchable apps found but we have apps without launch intent, try alternative launch methods
            if (appsWithoutIntent.isNotEmpty()) {
                Log.d(TAG, "No launchable apps found, trying alternative launch methods for apps without launch intent")
                
                // Try to find the best match from apps without launch intent
                for (appWithoutIntent in appsWithoutIntent) {
                    val packageName = appWithoutIntent.substringAfter("(").substringBefore(")")
                    val appLabel = appWithoutIntent.substringBefore(" (")
                    
                    // Prioritize exact matches
                    if (appLabel.equals(appName, ignoreCase = true)) {
                        Log.d(TAG, "Trying alternative launch for exact match: '$appLabel' ($packageName)")
                        if (tryAlternativeLaunch(packageName, appLabel)) {
                            return true
                        }
                    }
                }
                
                // If no exact match worked, try partial matches
                for (appWithoutIntent in appsWithoutIntent) {
                    val packageName = appWithoutIntent.substringAfter("(").substringBefore(")")
                    val appLabel = appWithoutIntent.substringBefore(" (")
                    
                    if (!appLabel.equals(appName, ignoreCase = true)) { // Skip exact matches (already tried)
                        Log.d(TAG, "Trying alternative launch for partial match: '$appLabel' ($packageName)")
                        if (tryAlternativeLaunch(packageName, appLabel)) {
                            return true
                        }
                    }
                }
            }
            
            // If still not found, try standard Android system app packages as fallback
            val standardPackage = getStandardSystemPackage(appName)
            if (standardPackage != null) {
                Log.d(TAG, "Trying standard system package: $standardPackage for app: $appName")
                // Check if standard package has launch intent
                val launchIntent = packageManager.getLaunchIntentForPackage(standardPackage)
                if (launchIntent != null && launchApp(standardPackage)) {
                    return true
                } else {
                    Log.d(TAG, "Standard package '$standardPackage' has no launch intent, skipping")
                }
            }
            
            Log.w(TAG, "No launchable app found with name: $appName")
            // Send error broadcast to trigger TTS notification and hide overlay
            sendErrorBroadcast("No launchable app found with name: $appName")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app by name $appName: ${e.message}")
            // Send error broadcast to trigger TTS notification and hide overlay
            sendErrorBroadcast("Failed to launch app by name $appName: ${e.message}")
            false
        }
    }
    
    /**
     * Get standard Android system package for common app names
     */
    private fun getStandardSystemPackage(appName: String): String? {
        return when (appName.lowercase()) {
            "settings" -> "com.android.settings"
            "camera" -> "com.android.camera2"
            "gallery", "photos" -> "com.android.gallery3d"
            "phone", "dialer" -> "com.android.dialer"
            "messages", "messaging" -> "com.android.mms"
            "contacts" -> "com.android.contacts"
            "calculator" -> "com.android.calculator2"
            "calendar" -> "com.android.calendar"
            "clock" -> "com.android.deskclock"
            "browser" -> "com.android.browser"
            "files", "file manager" -> "com.android.documentsui"
            "whatsapp business" -> "com.whatsapp.w4b"
            "wa business" -> "com.whatsapp.w4b"
            "whatsapp" -> "com.whatsapp"
            else -> null
        }
    }
    
    /**
     * Try alternative launch methods for apps without standard launch intents
     */
    private fun tryAlternativeLaunch(packageName: String, appLabel: String): Boolean {
        try {
            Log.d(TAG, "Attempting alternative launch methods for $appLabel ($packageName)")
            
            // Method 1: Try to get main activity and launch it directly
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                intent.setPackage(packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                if (resolveInfos.isNotEmpty()) {
                    val mainActivity = resolveInfos[0].activityInfo
                    val launchIntent = Intent(Intent.ACTION_MAIN)
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                    launchIntent.setClassName(packageName, mainActivity.name)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    startActivity(launchIntent)
                    Log.d(TAG, "Successfully launched $appLabel using main activity method")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Main activity launch failed for $appLabel: ${e.message}")
            }
            
            // Method 2: Try package manager's launch intent with different flags
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    Log.d(TAG, "Successfully launched $appLabel using package manager with flags")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Package manager launch with flags failed for $appLabel: ${e.message}")
            }
            
            // Method 3: Try to open app info/settings page as last resort
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:$packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "Opened app settings for $appLabel as fallback")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "App settings launch failed for $appLabel: ${e.message}")
            }
            
            Log.w(TAG, "All alternative launch methods failed for $appLabel")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception in tryAlternativeLaunch for $appLabel: ${e.message}")
            return false
        }
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
            intent.setPackage(applicationContext.packageName)
            applicationContext.sendBroadcast(intent)
            Log.d(TAG, "Error broadcast sent with result: $resultJson")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send error broadcast: ${e.message}", e)
        }
    }
}
