import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class ForegroundAutomationService {
  static const MethodChannel _channel = MethodChannel('com.vibeagent.dude/automation');

  /// Start automation task in foreground service
  static Future<bool> startForegroundAutomation(String userTask) async {
    try {
      final result = await _channel.invokeMethod('startForegroundAutomation', {
        'user_task': userTask,
      });
      debugPrint('✅ Foreground automation started: $userTask');
      return result == true;
    } catch (e) {
      debugPrint('❌ Failed to start foreground automation: $e');
      return false;
    }
  }

  /// Stop foreground automation service
  static Future<bool> stopForegroundAutomation() async {
    try {
      final result = await _channel.invokeMethod('stopForegroundAutomation');
      debugPrint('✅ Foreground automation stopped');
      return result == true;
    } catch (e) {
      debugPrint('❌ Failed to stop foreground automation: $e');
      return false;
    }
  }

  /// Check if automation is currently running
  static Future<bool> isAutomating() async {
    try {
      final result = await _channel.invokeMethod('isAutomating');
      return result == true;
    } catch (e) {
      debugPrint('❌ Failed to check automation status: $e');
      return false;
    }
  }

  /// Execute a user task (regular automation)
  static Future<bool> executeUserTask(String userTask) async {
    try {
      final result = await _channel.invokeMethod('executeUserTask', {
        'user_task': userTask,
      });
      return result == true;
    } catch (e) {
      debugPrint('❌ Failed to execute user task: $e');
      return false;
    }
  }

  /// Stop regular automation
  static Future<bool> stopAutomation() async {
    try {
      final result = await _channel.invokeMethod('stopAutomation');
      return result == true;
    } catch (e) {
      debugPrint('❌ Failed to stop automation: $e');
      return false;
    }
  }

  /// Get task history
  static Future<List<String>> getTaskHistory() async {
    try {
      final result = await _channel.invokeMethod('getTaskHistory');
      if (result is List) {
        return result.cast<String>();
      }
      return [];
    } catch (e) {
      debugPrint('❌ Failed to get task history: $e');
      return [];
    }
  }
}