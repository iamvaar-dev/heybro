package com.vibeagent.dude

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

class AppManagementActivity {
    companion object {
        private const val TAG = "AppManagementActivity"
    }

    /**
     * Opens an app by its package name
     * @param packageName The package name of the app to open
     * @param context The context to use for launching the app
     * @return Boolean indicating success/failure
     */
    fun openApp(packageName: String, context: Context): Boolean {
        return try {
            Log.d(TAG, "📱 Attempting to open app: $packageName")

            // Validate input
            if (packageName.isBlank()) {
                Log.e(TAG, "❌ Package name is empty or blank")
                return false
            }

            val packageManager = context.packageManager

            // Check if app is installed first
            try {
                packageManager.getApplicationInfo(packageName, 0)
                Log.d(TAG, "✅ App is installed: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ App not installed: $packageName")
                return false
            }

            // Try accessibility service first for direct app launching
            val accessibilityService = MyAccessibilityService.instance
            if (accessibilityService != null) {
                Log.d(TAG, "✅ Using accessibility service to launch app: $packageName")
                return accessibilityService.launchApp(packageName)
            }

            // Fallback to traditional intent-based launching
            Log.d(TAG, "⚠️ Accessibility service not available, using traditional intent method")
            val intent = packageManager.getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Log.d(TAG, "🚀 Starting activity for: $packageName")
                context.startActivity(intent)
                Log.d(TAG, "✅ Successfully opened app: $packageName")
                true
            } else {
                Log.e(TAG, "❌ No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open app $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * Extracts app name from a command string
     * @param command The command string to extract app name from
     * @return The extracted app name
     */
    fun extractAppNameFromCommand(command: String): String {
        val lowerCommand = command.lowercase()
        val words = lowerCommand.split(" ")

        // Common app opening patterns
        val openingKeywords = listOf("open", "launch", "start", "run", "go to", "switch to")

        // Remove opening keywords and extract potential app name
        val cleanedWords =
                words.filter { word -> !openingKeywords.any { keyword -> word.contains(keyword) } }

        val appName = cleanedWords.joinToString(" ").trim()
        Log.d(TAG, "🔍 Extracted app name: '$appName' from command: '$command'")

        return appName
    }

    /**
     * Finds apps that match the given app name
     * @param appName The name to search for
     * @param context The context to use for package manager
     * @return List of matching apps with their details
     */
    fun findMatchingApps(appName: String, context: Context): List<Map<String, String>> {
        return try {
            if (appName.isBlank()) {
                Log.e(TAG, "❌ App name is empty or blank")
                return emptyList()
            }

            val packageManager = context.packageManager
            val installedApps = getInstalledApps(context)
            val lowerAppName = appName.lowercase().trim()

            Log.d(
                    TAG,
                    "🔍 Searching for apps matching: '$appName' (${installedApps.size} total apps)"
            )

            val matchingApps =
                    installedApps
                            .filter { app ->
                                val appLabel = app["label"]?.lowercase()?.trim() ?: ""
                                val packageName = app["packageName"]?.lowercase() ?: ""

                                // Check for exact matches first, then partial matches
                                val exactMatch = appLabel == lowerAppName
                                val startsWithMatch = appLabel.startsWith(lowerAppName)
                                val containsMatch = appLabel.contains(lowerAppName)
                                val packageMatch = packageName.contains(lowerAppName)
                                val reverseMatch =
                                        lowerAppName.contains(appLabel) && appLabel.isNotEmpty()

                                exactMatch ||
                                        startsWithMatch ||
                                        containsMatch ||
                                        packageMatch ||
                                        reverseMatch
                            }
                            .sortedWith(
                                    compareBy<Map<String, String>> { app ->
                                        val appLabel = app["label"]?.lowercase()?.trim() ?: ""
                                        // Prioritize exact matches
                                        when {
                                            appLabel == lowerAppName -> 0
                                            appLabel.startsWith(lowerAppName) -> 1
                                            appLabel.contains(lowerAppName) -> 2
                                            else -> 3
                                        }
                                    }
                                            .thenBy { it["label"] }
                            )

            Log.d(TAG, "📱 Found ${matchingApps.size} matching apps for '$appName':")
            matchingApps.forEachIndexed { index, app ->
                Log.d(
                        TAG,
                        "  ${index + 1}. ${app["label"]} (${app["packageName"]}) - Launchable: ${app["isLaunchable"]}"
                )
            }

            matchingApps
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error finding matching apps: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Gets all installed apps on the device
     * @param context The context to use for package manager
     * @return List of all installed apps with their details
     */
    fun getInstalledApps(context: Context): List<Map<String, String>> {
        return try {
            Log.d(TAG, "📱 GETTING ALL INSTALLED APPS (USER + SYSTEM)")

            val packageManager = context.packageManager
            val installedPackages =
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            val apps = mutableListOf<Map<String, String>>()

            for (appInfo in installedPackages) {
                try {
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUserApp = !isSystemApp

                    // Get launch intent to check if app is launchable
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    val isLaunchable = launchIntent != null

                    val appData =
                            mapOf(
                                    "label" to label,
                                    "packageName" to packageName,
                                    "isSystemApp" to isSystemApp.toString(),
                                    "isUserApp" to isUserApp.toString(),
                                    "isLaunchable" to isLaunchable.toString()
                            )

                    apps.add(appData)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error processing app ${appInfo.packageName}: ${e.message}")
                }
            }

            // Sort apps by label
            val sortedApps = apps.sortedBy { it["label"]?.lowercase() }

            Log.d(TAG, "📱 Found total ${sortedApps.size} apps")
            Log.d(TAG, "📱 User apps: ${sortedApps.count { it["isUserApp"] == "true" }}")
            Log.d(TAG, "📱 System apps: ${sortedApps.count { it["isSystemApp"] == "true" }}")
            Log.d(TAG, "📱 Launchable apps: ${sortedApps.count { it["isLaunchable"] == "true" }}")

            sortedApps
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting installed apps: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Gets only user-installed (non-system) apps
     * @param context The context to use for package manager
     * @return List of user-installed apps
     */
    fun getUserInstalledApps(context: Context): List<Map<String, String>> {
        return getInstalledApps(context).filter { app ->
            app["isUserApp"] == "true" && app["isLaunchable"] == "true"
        }
    }

    /**
     * Gets only launchable apps (apps that can be opened)
     * @param context The context to use for package manager
     * @return List of launchable apps
     */
    fun getLaunchableApps(context: Context): List<Map<String, String>> {
        return getInstalledApps(context).filter { app -> app["isLaunchable"] == "true" }
    }

    /**
     * Searches for apps by keyword in their name or package
     * @param keyword The keyword to search for
     * @param context The context to use for package manager
     * @return List of apps matching the keyword
     */
    fun searchApps(keyword: String, context: Context): List<Map<String, String>> {
        val lowerKeyword = keyword.lowercase()
        return getInstalledApps(context).filter { app ->
            val label = app["label"]?.lowercase() ?: ""
            val packageName = app["packageName"]?.lowercase() ?: ""
            label.contains(lowerKeyword) || packageName.contains(lowerKeyword)
        }
    }

    /**
     * Gets app information by package name
     * @param packageName The package name of the app
     * @param context The context to use for package manager
     * @return Map containing app information or null if not found
     */
    fun getAppInfo(packageName: String, context: Context): Map<String, String>? {
        return try {
            val packageManager = context.packageManager
            val appInfo =
                    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val isLaunchable = launchIntent != null

            mapOf(
                    "label" to label,
                    "packageName" to packageName,
                    "isSystemApp" to isSystemApp.toString(),
                    "isUserApp" to (!isSystemApp).toString(),
                    "isLaunchable" to isLaunchable.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting app info for $packageName: ${e.message}", e)
            null
        }
    }

    /**
     * Checks if an app is installed
     * @param packageName The package name to check
     * @param context The context to use for package manager
     * @return Boolean indicating if the app is installed
     */
    fun isAppInstalled(packageName: String, context: Context): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking if app is installed: ${e.message}", e)
            false
        }
    }

    /**
     * Gets the best matching app for a given app name
     * @param appName The app name to search for
     * @param context The context to use for package manager
     * @return The best matching app or null if none found
     */
    fun getBestMatchingApp(appName: String, context: Context): Map<String, String>? {
        try {
            val matchingApps = findMatchingApps(appName, context)

            if (matchingApps.isEmpty()) {
                Log.d(TAG, "❌ No apps found matching: '$appName'")
                return null
            }

            // Filter for launchable apps only
            val launchableApps = matchingApps.filter { it["isLaunchable"] == "true" }

            if (launchableApps.isEmpty()) {
                Log.w(
                        TAG,
                        "⚠️ Found ${matchingApps.size} matches but none are launchable for: '$appName'"
                )
                return null
            }

            // Return the first (best) match that's launchable
            val bestMatch = launchableApps.first()
            Log.d(
                    TAG,
                    "✅ Best match for '$appName': ${bestMatch["label"]} (${bestMatch["packageName"]}) - Launchable: ${bestMatch["isLaunchable"]}"
            )

            return bestMatch
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting best matching app: ${e.message}", e)
            return null
        }
    }

    /**
     * Opens an app by name (searches for the best match)
     * @param appName The name of the app to open
     * @param context The context to use
     * @return Boolean indicating success/failure
     */
    fun openAppByName(appName: String, context: Context): Boolean {
        try {
            Log.d(TAG, "🔍 Opening app by name: '$appName'")

            if (appName.isBlank()) {
                Log.e(TAG, "❌ App name is empty or blank")
                return false
            }

            val bestMatch = getBestMatchingApp(appName, context)

            if (bestMatch != null) {
                val packageName = bestMatch["packageName"] ?: return false
                
                // Try accessibility service first for direct app launching
                val accessibilityService = MyAccessibilityService.instance
                if (accessibilityService != null) {
                    Log.d(TAG, "✅ Using accessibility service to launch app: $packageName")
                    return accessibilityService.launchApp(packageName)
                } else {
                    Log.d(TAG, "⚠️ Accessibility service not available, falling back to openApp method")
                    return openApp(packageName, context)
                }
            } else {
                Log.e(TAG, "❌ Could not find app matching: '$appName'")
                // Try a broader search
                val allMatches = findMatchingApps(appName, context)
                Log.d(TAG, "🔍 Broader search found ${allMatches.size} matches")
                allMatches.forEach { app ->
                    Log.d(TAG, "  - ${app["label"]} (${app["packageName"]})")
                }
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening app by name '$appName': ${e.message}")
            return false
        }
    }

    /**
     * Gets recently used apps
     * @param context The context to use
     * @return List of recent apps
     */
    fun getRecentApps(context: Context): List<Map<String, String>> {
        return try {
            Log.d(TAG, "📋 Getting recent apps")
            // This is a simplified implementation - in practice you'd need to access
            // usage stats which requires special permissions
            val recentApps = mutableListOf<Map<String, String>>()

            // For now, return a subset of launchable apps as "recent"
            val allApps = getLaunchableApps(context)
            val recent = allApps.take(10) // Just take first 10 as "recent"

            Log.d(TAG, "✅ Retrieved ${recent.size} recent apps")
            recent
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting recent apps: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Clears app data for the specified package
     * @param packageName The package name
     * @param context The context to use
     * @return Boolean indicating success
     */
    fun clearAppData(packageName: String, context: Context): Boolean {
        return try {
            Log.d(TAG, "🧹 Attempting to clear data for: $packageName")

            // This typically requires system-level permissions
            // For now, return false as it's not easily implementable without root
            Log.w(TAG, "⚠️ Clear app data requires system permissions - not implemented")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing app data: ${e.message}", e)
            false
        }
    }

    /**
     * Force stops the specified app
     * @param packageName The package name
     * @param context The context to use
     * @return Boolean indicating success
     */
    fun forceStopApp(packageName: String, context: Context): Boolean {
        return try {
            Log.d(TAG, "🛑 Attempting to force stop: $packageName")

            // This typically requires system-level permissions
            // For now, return false as it's not easily implementable without root
            Log.w(TAG, "⚠️ Force stop app requires system permissions - not implemented")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error force stopping app: ${e.message}", e)
            false
        }
    }
}
