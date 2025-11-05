package com.vibeagent.dude

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class AutomationService(private val context: Context) {
    companion object {
        private const val TAG = "AutomationService"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val toolActivityManager = ToolActivityManager(context)
    private val appManagementActivity = AppManagementActivity()
    private val taskHistory = mutableListOf<TaskStep>()
    private val memory = ConcurrentHashMap<String, String>()
    private var currentAppPackage: String? = null
    private var isAutomating = false

    data class TaskStep(
            val action: String,
            val parameters: Map<String, Any>,
            val result: Boolean,
            val timestamp: Long,
            val preCondition: String? = null,
            val postCondition: String? = null
    )

    data class LLMRequest(
            val userTask: String,
            val screenshot: String,
            val accessibilityTree: JSONArray,
            val taskHistory: List<TaskStep>,
            val currentApp: String?,
            val availableTools: List<String>
    )

    data class LLMResponse(
            val action: String,
            val parameters: Map<String, Any>,
            val preCondition: String? = null,
            val reasoning: String? = null,
            val isComplete: Boolean = false
    )

    /** Main automation entry point */
    suspend fun executeUserTask(userTask: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🤖 Starting automation for task: $userTask")
                isAutomating = true

                // Check if task involves an app first
                val appRequired = checkIfAppRequired(userTask)
                if (appRequired) {
                    val appOpened = handleAppSelection(userTask)
                    if (!appOpened) {
                        Log.e(TAG, "❌ Failed to open required app")
                        return@withContext false
                    }
                }

                // Main automation loop
                var attempts = 0
                var taskComplete = false

                while (!taskComplete && attempts < 10) {
                    attempts++
                    Log.d(TAG, "🔄 Automation attempt $attempts")

                    val currentState = getCurrentState()
                    val llmRequest = createLLMRequest(userTask, currentState)
                    val llmResponse = sendToLLM(llmRequest)

                    if (llmResponse.isComplete) {
                        Log.d(TAG, "✅ Task completed successfully")
                        taskComplete = true
                        break
                    }

                    val actionResult = executeAction(llmResponse)
                    if (!actionResult && attempts >= MAX_RETRIES) {
                        Log.e(TAG, "❌ Failed to execute action after $attempts attempts")
                        break
                    }

                    delay(500) // Brief pause between actions
                }

                taskComplete
            } catch (e: Exception) {
                Log.e(TAG, "❌ Automation failed: ${e.message}", e)
                // Send error broadcast to trigger TTS notification and hide overlay
                sendErrorBroadcast("Automation failed: ${e.message}")
                false
            } finally {
                isAutomating = false
            }
        }
    }

    /** Check if user task requires opening an app */
    private suspend fun checkIfAppRequired(userTask: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Simple heuristic: if task mentions "open" or contains app names
                val lowerTask = userTask.lowercase()
                lowerTask.contains("open") || lowerTask.contains("launch") ||
                lowerTask.contains("start") || lowerTask.contains("app")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking app requirement: ${e.message}", e)
                false
            }
        }
    }

    /** Handle app selection and opening */
    private suspend fun handleAppSelection(userTask: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Extract app keywords from user task
                val lowerTask = userTask.lowercase()
                val appKeywords = listOf(
                    "gmail", "email", "chrome", "browser", "instagram", "facebook",
                    "whatsapp", "messages", "camera", "photos", "maps", "youtube",
                    "spotify", "music", "settings", "calculator", "calendar"
                )

                // Search for matching apps based on keywords
                val matchingApps = mutableListOf<Map<String, String>>()
                for (keyword in appKeywords) {
                    if (lowerTask.contains(keyword)) {
                        val apps = appManagementActivity.findMatchingApps(keyword, context)
                        matchingApps.addAll(apps.filter { it["isLaunchable"] == "true" })
                    }
                }

                // If no specific keywords found, get all launchable apps
                if (matchingApps.isEmpty()) {
                    val allApps = appManagementActivity.getLaunchableApps(context)
                    matchingApps.addAll(allApps)
                }

                if (matchingApps.isEmpty()) {
                    Log.w(TAG, "⚠️ No matching apps found")
                    return@withContext false
                }

                // Send matches to LLM for final selection
                // Simply use the first matching app
                val selectedApp = matchingApps.first()
                val packageName = selectedApp["packageName"] ?: return@withContext false

                Log.d(TAG, "📱 Selected app: ${selectedApp["label"]} ($packageName)")

                // Open the selected app
                val opened = appManagementActivity.openApp(packageName, context)
                if (opened) {
                    currentAppPackage = packageName
                    delay(2000) // Wait for app to open
                }

                opened
            } catch (e: Exception) {
                Log.e(TAG, "❌ App selection failed: ${e.message}", e)
                false
            }
        }
    }

    /** Get current state of the device */
    private suspend fun getCurrentState(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val screenshot = toolActivityManager.takeScreenshot()
                val elements = toolActivityManager.getScreenElements()
                val currentApp = MyAccessibilityService.instance?.getCurrentAppPackage()

                mapOf(
                        "screenshot" to (screenshot ?: ""),
                        "elements" to elements,
                        "current_app" to (currentApp ?: ""),
                        "screen_dimensions" to toolActivityManager.getScreenDimensions()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting current state: ${e.message}", e)
                emptyMap()
            }
        }
    }

    /** Create LLM request with current state */
    private fun createLLMRequest(userTask: String, currentState: Map<String, Any>): LLMRequest {
        val elements = currentState["elements"] as? List<Map<String, Any>> ?: emptyList()
        val accessibilityTree = JSONArray()

        elements.forEachIndexed { index, element ->
            val jsonElement =
                    JSONObject().apply {
                        put("index", index)
                        put("type", element["type"] ?: "unknown")
                        put("text", element["text"] ?: "")
                        put("clickable", element["clickable"] ?: false)
                        put("scrollable", element["scrollable"] ?: false)
                        put(
                                "bounds",
                                JSONObject(element["bounds"] as? Map<String, Any> ?: emptyMap<String, Any>())
                        )
                    }
            accessibilityTree.put(jsonElement)
        }

        return LLMRequest(
                userTask = userTask,
                screenshot = currentState["screenshot"] as? String ?: "",
                accessibilityTree = accessibilityTree,
                taskHistory = taskHistory.takeLast(5), // Last 5 steps
                currentApp = currentState["current_app"] as? String,
                availableTools = getAvailableTools()
        )
    }

    /** Send request to LLM and parse response */
    private suspend fun sendToLLM(request: LLMRequest): LLMResponse {
        return withContext(Dispatchers.IO) {
            try {
                // All LLM logic is now handled in Flutter
                // This method should not be called anymore
                Log.w(TAG, "⚠️ performAutomationStep called but LLM logic moved to Flutter")
                LLMResponse("message", mapOf("text" to "Automation handled by Flutter"), isComplete = true)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in automation step: ${e.message}", e)
                LLMResponse("message", mapOf("text" to "Error: ${e.message}"), isComplete = true)
            }
        }
    }

    /** Build the LLM prompt */
    private fun buildLLMPrompt(request: LLMRequest): String {
        return """
            You are an Android automation agent. Execute this task step by step.

            USER TASK: "${request.userTask}"
            CURRENT APP: ${request.currentApp ?: "Unknown"}

            AVAILABLE TOOLS:
            ${getAvailableTools().joinToString(", ")}

            ACCESSIBILITY TREE (indexed elements):
            ${request.accessibilityTree.toString(2)}

            RECENT ACTIONS:
            ${request.taskHistory.takeLast(3).joinToString("\n") {
                "${it.action}(${it.parameters}) -> ${if(it.result) "SUCCESS" else "FAILED"}"
            }}

            Respond with JSON:
            {
                "action": "tool_name",
                "parameters": {"param": "value"},
                "pre_condition": "required state (optional)",
                "reasoning": "why this action",
                "is_complete": false
            }

            Use tap_by_index for clicking elements, input_text for typing, start_app for opening apps.
        """.trimIndent()
    }

    /** Parse LLM response */
    private fun parseLLMResponse(response: String): LLMResponse {
        return try {
            val json = JSONObject(response)
            val parameters = mutableMapOf<String, Any>()

            val paramsJson = if (json.has("parameters")) json.getJSONObject("parameters") else null
            if (paramsJson != null) {
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    parameters[key] = paramsJson.get(key)
                }
            }

            LLMResponse(
                    action = if (json.has("action")) json.getString("action") else "complete",
                    parameters = parameters,
                    preCondition = if (json.has("pre_condition")) json.getString("pre_condition") else null,
                    reasoning = if (json.has("reasoning")) json.getString("reasoning") else null,
                    isComplete = if (json.has("is_complete")) json.getBoolean("is_complete") else false
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing LLM response: ${e.message}", e)
            LLMResponse("complete", emptyMap(), isComplete = true)
        }
    }

    /** Execute the action from LLM response */
    private suspend fun executeAction(response: LLMResponse): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔧 Executing action: ${response.action}")

                // Check pre-condition if specified
                if (response.preCondition != null && !checkPreCondition(response.preCondition)) {
                    Log.w(TAG, "⚠️ Pre-condition not met: ${response.preCondition}")
                    return@withContext false
                }

                val result =
                        when (response.action) {
                            "get_state" -> handleGetState()
                            "tap_by_index" -> handleTapByIndex(response.parameters)
                            "tap" -> handleTap(response.parameters)
                            "swipe" -> handleSwipe(response.parameters)
                            "input_text" -> handleInputText(response.parameters)
                            "back" -> handleBack()
                            "press_key" -> handlePressKey(response.parameters)
                            "start_app" -> handleStartApp(response.parameters)
                            "take_screenshot" -> handleTakeScreenshot()
                            "list_packages" -> handleListPackages()
                            "remember" -> handleRemember(response.parameters)
                            "get_memory" -> handleGetMemory(response.parameters)
                            "complete" -> handleComplete()
                            else -> {
                                Log.w(TAG, "⚠️ Unknown action: ${response.action}")
                                false
                            }
                        }

                // Record the action
                recordAction(response.action, response.parameters, result, response.preCondition)

                result
            } catch (e: Exception) {
                Log.e(TAG, "❌ Action execution failed: ${e.message}", e)
                false
            }
        }
    }

    /** Tool implementations */
    private suspend fun handleGetState(): Boolean {
        val state = getCurrentState()
        Log.d(TAG, "📊 Current state retrieved")
        return true
    }

    private suspend fun handleTapByIndex(params: Map<String, Any>): Boolean {
        val index = params["index"] as? Int ?: return false
        val elements = toolActivityManager.getScreenElements()

        if (index >= elements.size) return false

        val element = elements[index]
        val bounds = element["bounds"] as? Map<String, Any> ?: return false
        val x = bounds["x"] as? Int ?: return false
        val y = bounds["y"] as? Int ?: return false

        return toolActivityManager.performTap(x.toFloat(), y.toFloat())
    }

    private suspend fun handleTap(params: Map<String, Any>): Boolean {
        val x = (params["x"] as? Number)?.toFloat() ?: return false
        val y = (params["y"] as? Number)?.toFloat() ?: return false
        return toolActivityManager.performTap(x, y)
    }

    private suspend fun handleSwipe(params: Map<String, Any>): Boolean {
        val direction = params["direction"] as? String ?: return false
        return toolActivityManager.performScroll(direction)
    }

    private suspend fun handleInputText(params: Map<String, Any>): Boolean {
        val text = params["text"] as? String ?: return false
        return toolActivityManager.performAdvancedType(text)
    }

    private suspend fun handleBack(): Boolean {
        return toolActivityManager.performBack()
    }

    private suspend fun handlePressKey(params: Map<String, Any>): Boolean {
        val keyCode = params["key_code"] as? Int ?: return false
        return toolActivityManager.sendKeyEvent(keyCode)
    }

    private suspend fun handleStartApp(params: Map<String, Any>): Boolean {
        val packageName = params["package_name"] as? String
        val appName = params["app_name"] as? String

        return when {
            packageName != null -> {
                currentAppPackage = packageName
                appManagementActivity.openApp(packageName, context)
            }
            appName != null -> {
                val opened = appManagementActivity.openAppByName(appName, context)
                if (opened) {
                    delay(1000)
                    currentAppPackage = MyAccessibilityService.instance?.getCurrentAppPackage()
                }
                opened
            }
            else -> false
        }
    }

    private suspend fun handleTakeScreenshot(): Boolean {
        val screenshot = toolActivityManager.takeScreenshot()
        return screenshot != null
    }

    private suspend fun handleListPackages(): Boolean {
        val apps = appManagementActivity.getLaunchableApps(context)
        Log.d(TAG, "📱 Found ${apps.size} launchable apps")
        return true
    }

    private fun handleRemember(params: Map<String, Any>): Boolean {
        val key = params["key"] as? String ?: return false
        val value = params["value"] as? String ?: return false
        memory[key] = value
        Log.d(TAG, "🧠 Remembered: $key = $value")
        return true
    }

    private fun handleGetMemory(params: Map<String, Any>): Boolean {
        val key = params["key"] as? String ?: return false
        val value = memory[key]
        Log.d(TAG, "🧠 Retrieved memory: $key = $value")
        return value != null
    }

    private fun handleComplete(): Boolean {
        Log.d(TAG, "✅ Task marked as complete")
        return true
    }

    /** Check pre-conditions */
    private suspend fun checkPreCondition(condition: String): Boolean {
        return try {
            when {
                condition.contains("app_is") -> {
                    val expectedApp = condition.substringAfter("app_is:").trim()
                    val currentApp = MyAccessibilityService.instance?.getCurrentAppPackage()
                    currentApp?.contains(expectedApp, ignoreCase = true) == true
                }
                condition.contains("element_visible") -> {
                    val elementText = condition.substringAfter("element_visible:").trim()
                    val elements = toolActivityManager.getScreenElements()
                    elements.any {
                        (it["text"] as? String)?.contains(elementText, ignoreCase = true) == true
                    }
                }
                else -> true // No specific condition check
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Pre-condition check failed: ${e.message}", e)
            false
        }
    }

    /** Record action in task history */
    private fun recordAction(
            action: String,
            parameters: Map<String, Any>,
            result: Boolean,
            preCondition: String?
    ) {
        val step =
                TaskStep(
                        action = action,
                        parameters = parameters,
                        result = result,
                        timestamp = System.currentTimeMillis(),
                        preCondition = preCondition
                )
        taskHistory.add(step)

        // Keep only last 20 steps
        if (taskHistory.size > 20) {
            taskHistory.removeAt(0)
        }
    }

    /** Get available tools */
    private fun getAvailableTools(): List<String> {
        return listOf(
                "get_state",
                "tap_by_index",
                "tap",
                "swipe",
                "input_text",
                "back",
                "press_key",
                "start_app",
                "take_screenshot",
                "list_packages",
                "remember",
                "get_memory",
                "complete"
        )
    }

    /** Communicate with Flutter AutomationService for LLM requests */
    private suspend fun requestAIDecision(
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): String {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "🤖 Requesting AI decision from Flutter service...")

                // Send request to Flutter via method channel
                val mainActivity = MainActivity.instance
                if (mainActivity != null) {
                    val requestData = mapOf(
                        "prompt" to prompt,
                        "context" to context,
                        "timestamp" to System.currentTimeMillis()
                    )

                    // For now, return a continue action to let Flutter handle the AI
                    // The Flutter AutomationService will process this through VertexAI
                    return@withContext """
                    {
                        "action": "get_state",
                        "parameters": {},
                        "is_complete": false,
                        "reasoning": "Delegating to Flutter AI service"
                    }
                    """.trimIndent()
                } else {
                    Log.e(TAG, "❌ MainActivity not available for AI request")
                    return@withContext """
                    {
                        "action": "complete",
                        "parameters": {},
                        "is_complete": true,
                        "reasoning": "No AI service available"
                    }
                    """.trimIndent()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error requesting AI decision: ${e.message}", e)
                return@withContext """
                {
                    "action": "complete",
                    "parameters": {},
                    "is_complete": true,
                    "reasoning": "AI request failed: ${e.message}"
                }
                """.trimIndent()
            }
        }
    }

    /** Get automation status */
    fun isAutomating(): Boolean = isAutomating

    /** Stop automation */
    fun stopAutomation() {
        isAutomating = false
        Log.d(TAG, "🛑 Automation stopped")
    }

    /** Get task history */
    fun getTaskHistory(): List<TaskStep> = taskHistory.toList()

    /** Clear task history */
    fun clearTaskHistory() {
        taskHistory.clear()
        Log.d(TAG, "🧹 Task history cleared")
    }

    /** Get memory contents */
    fun getMemory(): Map<String, String> = memory.toMap()

    /** Clear memory */
    fun clearMemory() {
        memory.clear()
        Log.d(TAG, "Memory cleared")
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
