import 'package:flutter/services.dart';
import '../services/automation_service.dart';

class ToolsManager {
  static const MethodChannel _toolsChannel =
      MethodChannel('com.vibeagent.dude/tools');
  static const MethodChannel _automationChannel =
      MethodChannel('com.vibeagent.dude/automation');

  // Global throttle for tool calls to prevent abuse
  static DateTime? _lastToolCallAt;
  static DateTime? get lastToolCallAt => _lastToolCallAt;
  static const Duration _toolGap = Duration.zero;
  static bool _toolCallInFlight = false;

  static Future<void> _enforceToolGap() async {
    // Tool gap enforcement disabled - no delay between tool calls
    return;
  }

  /// Execute a tool with the given name and parameters
  static Future<Map<String, dynamic>> executeTool(
      String toolName, Map<String, dynamic> parameters) async {
    try {
      // Serialize tool calls to avoid concurrent duplicates
      while (_toolCallInFlight) {
        await Future.delayed(const Duration(milliseconds: 25));
      }
      _toolCallInFlight = true;

      // Enforce global gap between tool calls (measured from end of last call)
      await _enforceToolGap();

      switch (toolName) {
        // Screen Capture & Analysis
        case 'take_screenshot':
          return await _takeScreenshot();
        case 'get_accessibility_tree':
          return await _getAccessibilityTree();
        case 'get_screen_elements':
          return await _getScreenElements();
        case 'analyze_screen':
          return await _analyzeScreen();
        case 'perform_ocr':
          return await _performOcr(parameters);
        case 'get_current_app':
          return await _getCurrentApp();

        // Touch Operations
        case 'perform_tap':
          return await _performTap(parameters);
        case 'perform_long_press':
          return await _performLongPress(parameters);
        case 'perform_double_click':
          return await _performDoubleClick(parameters);

        // Precise Element Tapping
        case 'tap_element_by_text':
        case 'tap_element_by_index':
        case 'tap_element_by_bounds':
          return {'success': true, 'message': 'Handled by automation service'};

        // Gesture Operations
        case 'perform_swipe':
          return await _performSwipe(parameters);
        case 'perform_scroll':
          return await _performScroll(parameters);
        case 'perform_dynamic_scroll':
          return await _performDynamicScroll(parameters);
        case 'perform_pinch':
          return await _performPinch(parameters);
        case 'perform_zoom_in':
          return await _performZoomIn();
        case 'perform_zoom_out':
          return await _performZoomOut();

        // Text Operations
        case 'perform_advanced_type':
          return await _performAdvancedType(parameters);
        case 'advanced_type_text':
          return await _advancedTypeText(parameters);
        case 'non_tap_text_input':
          return await _nonTapTextInput(parameters);
        case 'get_focused_input_info':
          return await _getFocusedInputInfo();
        case 'get_all_input_fields':
          return await _getAllInputFields();

        case 'clear_text':
          return await _clearText();
        case 'select_all_text':
          return await _selectAllText();
        case 'copy_text':
          return await _copyText();
        case 'paste_text':
          return await _pasteText();
        case 'replace_text':
          return await _replaceText(parameters);
        case 'type_text_slowly':
          return await _typeTextSlowly(parameters);
        case 'insert_text':
          return await _insertText(parameters);



        // Key Events
        case 'perform_enter':
          return await _performEnter();
        case 'perform_backspace':
          return await _performBackspace();
        case 'perform_delete':
          return await _performDelete();
        case 'send_key_event':
          return await _sendKeyEvent(parameters);

        // UI Interaction
        case 'find_and_click':
          return await _findAndClick(parameters);
        case 'perform_back':
          return await _performBack();

        // App Management
        case 'open_app':
          return await _openApp(parameters);
        case 'open_app_by_name':
          return await _openAppByName(parameters);
        case 'get_launchable_apps':
          return await _getLaunchableApps();
        case 'get_installed_apps':
          return await _getInstalledApps();
        case 'find_matching_apps':
          return await _findMatchingApps(parameters);
        case 'search_apps':
          return await _searchApps(parameters);
        case 'get_best_matching_app':
          return await _getBestMatchingApp(parameters);

        // Navigation
        case 'perform_home':
          return await _performHome();
        case 'perform_recents':
          return await _performRecents();
        case 'open_settings':
          return await _openSettings();
        case 'open_notifications':
          return await _openNotifications();
        case 'open_quick_settings':
          return await _openQuickSettings();

        // Permissions
        case 'check_accessibility_permission':
          return await _checkAccessibilityPermission();
        case 'request_accessibility_permission':
          return await _requestAccessibilityPermission();
        case 'check_overlay_permission':
          return await _checkOverlayPermission();
        case 'request_overlay_permission':
          return await _requestOverlayPermission();

        // Automation
        case 'execute_user_task':
          return await _executeUserTask(parameters);

        default:
          return {
            'success': false,
            'error': 'Unknown tool: $toolName',
            'data': null
          };
      }
    } catch (e) {
      return {
        'success': false,
        'error': 'Error executing tool $toolName: $e',
        'data': null
      };
    } finally {
      // Mark end-of-call timestamp and release lock
      _lastToolCallAt = DateTime.now();
      _toolCallInFlight = false;
    }
  }

  // ==================== SCREEN CAPTURE & ANALYSIS ====================

  static Future<Map<String, dynamic>> _takeScreenshot() async {
    try {
      final result = await _toolsChannel.invokeMethod('takeScreenshot');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to take screenshot: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _getAccessibilityTree() async {
    try {
      final result = await _toolsChannel.invokeMethod('getAccessibilityTree');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get accessibility tree: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _getScreenElements() async {
    try {
      final result = await _toolsChannel.invokeMethod('getScreenElements');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get screen elements: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _analyzeScreen() async {
    try {
      final result = await _toolsChannel.invokeMethod('analyzeScreen');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to analyze screen: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _getCurrentApp() async {
    try {
      final result = await _toolsChannel.invokeMethod('getCurrentApp');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get current app: $e',
        'data': null
      };
    }
  }

  // ==================== TOUCH OPERATIONS ====================

  static Future<Map<String, dynamic>> _performTap(
      Map<String, dynamic> parameters) async {
    try {
      final x = parameters['x']?.toDouble() ?? 0.0;
      final y = parameters['y']?.toDouble() ?? 0.0;

      final result = await _toolsChannel.invokeMethod('performTap', {
        'x': x,
        'y': y,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Tap failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform tap: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performLongPress(
      Map<String, dynamic> parameters) async {
    try {
      final x = parameters['x']?.toDouble() ?? 0.0;
      final y = parameters['y']?.toDouble() ?? 0.0;
      final duration = parameters['duration']?.toInt() ?? 500;

      final result = await _toolsChannel.invokeMethod('performLongPress', {
        'x': x,
        'y': y,
        'duration': duration,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Long press failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform long press: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performDoubleClick(
      Map<String, dynamic> parameters) async {
    try {
      final x = parameters['x']?.toDouble() ?? 0.0;
      final y = parameters['y']?.toDouble() ?? 0.0;

      final result = await _toolsChannel.invokeMethod('performDoubleClick', {
        'x': x,
        'y': y,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Double click failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform double click: $e',
        'data': null
      };
    }
  }

  // ==================== GESTURE OPERATIONS ====================

  static Future<Map<String, dynamic>> _performSwipe(
      Map<String, dynamic> parameters) async {
    try {
      final startX = parameters['startX']?.toDouble() ?? 0.0;
      final startY = parameters['startY']?.toDouble() ?? 0.0;
      final endX = parameters['endX']?.toDouble() ?? 0.0;
      final endY = parameters['endY']?.toDouble() ?? 0.0;
      final duration = parameters['duration']?.toInt() ?? 300;

      final result = await _toolsChannel.invokeMethod('performSwipe', {
        'startX': startX,
        'startY': startY,
        'endX': endX,
        'endY': endY,
        'duration': duration,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Swipe failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform swipe: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performScroll(
      Map<String, dynamic> parameters) async {
    try {
      final direction = parameters['direction'] ?? 'down';

      final result = await _toolsChannel.invokeMethod('performScroll', {
        'direction': direction,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Scroll failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform scroll: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performDynamicScroll(
      Map<String, dynamic> parameters) async {
    try {
      final direction = parameters['direction'] ?? 'down';
      final targetText = parameters['target_text'] ?? parameters['targetText'] ?? '';
      final maxScrollAttempts = parameters['max_scroll_attempts'] ?? parameters['maxScrollAttempts'] ?? 5;
      final consecutiveIdenticalThreshold = parameters['consecutive_identical_threshold'] ?? parameters['consecutiveIdenticalThreshold'] ?? 2;
      final scrollWaitDurationMs = parameters['scroll_wait_duration_ms'] ?? parameters['scrollWaitDurationMs'] ?? 1500;
      
      if (targetText.isEmpty) {
        return {
          'success': false,
          'error': 'target_text parameter is required for dynamic scroll',
          'data': null
        };
      }

      // Import automation service to access the dynamic scroll method
      final automationService = AutomationService();
      final result = await automationService.performDynamicScroll(
        direction: direction,
        targetText: targetText,
        maxScrollAttempts: maxScrollAttempts,
        consecutiveIdenticalThreshold: consecutiveIdenticalThreshold,
        scrollWaitDuration: Duration(milliseconds: scrollWaitDurationMs),
      );

      return {
        'success': result,
        'data': {
          'target_found': result,
          'direction': direction,
          'target_text': targetText,
        },
        'error': result ? null : 'Target text "$targetText" not found after scrolling'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform dynamic scroll: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performPinch(
      Map<String, dynamic> parameters) async {
    try {
      final centerX = parameters['centerX']?.toDouble() ?? 500.0;
      final centerY = parameters['centerY']?.toDouble() ?? 500.0;
      final startDistance = parameters['startDistance']?.toDouble() ?? 100.0;
      final endDistance = parameters['endDistance']?.toDouble() ?? 200.0;

      final result = await _toolsChannel.invokeMethod('performPinch', {
        'centerX': centerX,
        'centerY': centerY,
        'startDistance': startDistance,
        'endDistance': endDistance,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Pinch failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform pinch: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performZoomIn() async {
    try {
      final result = await _toolsChannel.invokeMethod('performZoomIn');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Zoom in failed'
      };
    } catch (e) {
      return {'success': false, 'error': 'Failed to zoom in: $e', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> _performZoomOut() async {
    try {
      final result = await _toolsChannel.invokeMethod('performZoomOut');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Zoom out failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to zoom out: $e',
        'data': null
      };
    }
  }

  // ==================== TEXT OPERATIONS ====================



  static Future<Map<String, dynamic>> _performAdvancedType(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';

      final result = await _toolsChannel.invokeMethod('performAdvancedType', {
        'text': text,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Advanced type failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform advanced type: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _nonTapTextInput(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';
      final fieldId = parameters['fieldId']; // Optional field ID

      final Map<String, dynamic> args = {'text': text};
      if (fieldId != null) {
        args['fieldId'] = fieldId;
      }

      final result = await _toolsChannel.invokeMethod('nonTapTextInput', args);

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Non-tap text input failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform non-tap text input: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _getFocusedInputInfo() async {
    try {
      final result = await _toolsChannel.invokeMethod('getFocusedInputInfo');
      return {
        'success': true,
        'data': result,
        'error': null
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get focused input info: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _getAllInputFields() async {
    try {
      final result = await _toolsChannel.invokeMethod('getAllInputFields');
      return {
        'success': true,
        'data': result,
        'error': null
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get all input fields: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _clearText() async {
    try {
      final result = await _toolsChannel.invokeMethod('clearText');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Clear text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to clear text: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _selectAllText() async {
    try {
      final result = await _toolsChannel.invokeMethod('selectAllText');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Select all text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to select all text: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _copyText() async {
    try {
      final result = await _toolsChannel.invokeMethod('copyText');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Copy text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to copy text: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _pasteText() async {
    try {
      final result = await _toolsChannel.invokeMethod('pasteText');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Paste text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to paste text: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _replaceText(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';

      final result = await _toolsChannel.invokeMethod('replaceText', {
        'text': text,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Replace text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to replace text: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _typeTextSlowly(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';
      final delayMs = parameters['delayMs']?.toInt() ?? 50;

      final result = await _toolsChannel.invokeMethod('typeTextSlowly', {
        'text': text,
        'delayMs': delayMs,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Type text slowly failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to type text slowly: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _insertText(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';

      final result = await _toolsChannel.invokeMethod('insertText', {
        'text': text,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Insert text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to insert text: $e',
        'data': null
      };
    }
  }



  static Future<Map<String, dynamic>> _advancedTypeText(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';
      final clearFirst = parameters['clearFirst'] ?? false;
      final delayMs = parameters['delayMs']?.toInt() ?? 0;

      final result = await _toolsChannel.invokeMethod('advancedTypeText', {
        'text': text,
        'clearFirst': clearFirst,
        'delayMs': delayMs,
      });

      // If text input was successful, perform Enter for multi-field forms with proper timing
      if (result == true) {
        try {
          // Small delay to ensure text is properly set in the field
          await Future.delayed(Duration(milliseconds: 200));
          
          // Get all input fields to check if there are multiple fields on screen
          final inputFieldsResult = await _getAllInputFields();
          if (inputFieldsResult['success'] == true && inputFieldsResult['data'] != null) {
            final inputFields = inputFieldsResult['data'] as List;
            
            // If there are multiple input fields, perform Enter
            if (inputFields.length > 1) {
              await _performEnter();
            }
          }
        } catch (e) {
          // Ignore errors in auto-enter functionality, don't affect main operation
        }
      }

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Advanced type text failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to advanced type text: $e',
        'data': null
      };
    }
  }







  // ==================== KEY EVENTS ====================

  static Future<Map<String, dynamic>> _performEnter() async {
    try {
      final result = await _toolsChannel.invokeMethod('performEnter');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Enter key failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform enter: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performBackspace() async {
    try {
      final result = await _toolsChannel.invokeMethod('performBackspace');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Backspace failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform backspace: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performDelete() async {
    try {
      final result = await _toolsChannel.invokeMethod('performDelete');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Delete failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform delete: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _sendKeyEvent(
      Map<String, dynamic> parameters) async {
    try {
      final keyCode = parameters['keyCode']?.toInt() ?? 0;

      final result = await _toolsChannel.invokeMethod('sendKeyEvent', {
        'keyCode': keyCode,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Send key event failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to send key event: $e',
        'data': null
      };
    }
  }

  // ==================== UI INTERACTION ====================

  static Future<Map<String, dynamic>> _findAndClick(
      Map<String, dynamic> parameters) async {
    try {
      final text = parameters['text'] ?? '';
      final contentDescription = parameters['contentDescription'] ?? '';
      final className = parameters['className'] ?? '';

      final result = await _toolsChannel.invokeMethod('findAndClick', {
        'text': text,
        'contentDescription': contentDescription,
        'className': className,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Find and click failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to find and click: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performBack() async {
    try {
      final result = await _toolsChannel.invokeMethod('performBack');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Back navigation failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform back: $e',
        'data': null
      };
    }
  }

  // ==================== APP MANAGEMENT ====================

  static Future<Map<String, dynamic>> _openApp(
      Map<String, dynamic> parameters) async {
    try {
      final packageName =
          parameters['package'] ?? parameters['packageName'] ?? '';

      final result = await _toolsChannel.invokeMethod('openApp', {
        'packageName': packageName,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Failed to open app'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to open app: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _openAppByName(
      Map<String, dynamic> parameters) async {
    try {
      final appName = parameters['appName'] ?? parameters['name'] ?? '';

      final result = await _toolsChannel.invokeMethod('openAppByName', {
        'app_name': appName,
      });

      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Failed to open app by name'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to open app by name: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _getLaunchableApps() async {
    try {
      final result = await _toolsChannel.invokeMethod('getLaunchableApps');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get launchable apps: $e',
        'data': []
      };
    }
  }

  static Future<Map<String, dynamic>> _getInstalledApps() async {
    try {
      final result = await _toolsChannel.invokeMethod('getInstalledApps');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get installed apps: $e',
        'data': []
      };
    }
  }

  static Future<Map<String, dynamic>> _findMatchingApps(
      Map<String, dynamic> parameters) async {
    try {
      final appName = parameters['appName'] ?? parameters['name'] ?? '';

      final result = await _toolsChannel.invokeMethod('findMatchingApps', {
        'appName': appName,
      });

      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to find matching apps: $e',
        'data': []
      };
    }
  }

  static Future<Map<String, dynamic>> _searchApps(
      Map<String, dynamic> parameters) async {
    try {
      final keyword = parameters['keyword'] ?? parameters['query'] ?? '';

      final result = await _toolsChannel.invokeMethod('searchApps', {
        'keyword': keyword,
      });

      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to search apps: $e',
        'data': []
      };
    }
  }

  static Future<Map<String, dynamic>> _getBestMatchingApp(
      Map<String, dynamic> parameters) async {
    try {
      final appName = parameters['appName'] ?? parameters['name'] ?? '';

      final result = await _toolsChannel.invokeMethod('getBestMatchingApp', {
        'appName': appName,
      });

      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to get best matching app: $e',
        'data': null
      };
    }
  }

  // ==================== NAVIGATION ====================

  static Future<Map<String, dynamic>> _performHome() async {
    try {
      final result = await _toolsChannel.invokeMethod('performHome');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Home navigation failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform home: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _performRecents() async {
    try {
      final result = await _toolsChannel.invokeMethod('performRecents');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Recents navigation failed'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform recents: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _openSettings() async {
    try {
      final result = await _toolsChannel.invokeMethod('openSettings');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Failed to open settings'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to open settings: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _openNotifications() async {
    try {
      final result = await _toolsChannel.invokeMethod('openNotifications');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Failed to open notifications'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to open notifications: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _openQuickSettings() async {
    try {
      final result = await _toolsChannel.invokeMethod('openQuickSettings');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Failed to open quick settings'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to open quick settings: $e',
        'data': null
      };
    }
  }

  // ==================== PERMISSIONS ====================

  static Future<Map<String, dynamic>> _checkAccessibilityPermission() async {
    try {
      final result =
          await _toolsChannel.invokeMethod('checkAccessibilityPermission');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to check accessibility permission: $e',
        'data': false
      };
    }
  }

  static Future<Map<String, dynamic>> _requestAccessibilityPermission() async {
    try {
      final result =
          await _toolsChannel.invokeMethod('requestAccessibilityPermission');
      return {
        'success': result == true,
        'data': null,
        'error':
            result == true ? null : 'Failed to request accessibility permission'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to request accessibility permission: $e',
        'data': null
      };
    }
  }

  static Future<Map<String, dynamic>> _checkOverlayPermission() async {
    try {
      final result = await _toolsChannel.invokeMethod('checkOverlayPermission');
      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to check overlay permission: $e',
        'data': false
      };
    }
  }

  static Future<Map<String, dynamic>> _requestOverlayPermission() async {
    try {
      final result =
          await _toolsChannel.invokeMethod('requestOverlayPermission');
      return {
        'success': result == true,
        'data': null,
        'error': result == true ? null : 'Failed to request overlay permission'
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to request overlay permission: $e',
        'data': null
      };
    }
  }

  // ==================== AUTOMATION ====================

  static Future<Map<String, dynamic>> _executeUserTask(
      Map<String, dynamic> parameters) async {
    try {
      final userTask = parameters['user_task'] ?? parameters['task'] ?? '';

      final result = await _automationChannel.invokeMethod('executeUserTask', {
        'user_task': userTask,
      });

      return {'success': true, 'data': result, 'error': null};
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to execute user task: $e',
        'data': null
      };
    }
  }



  static Future<Map<String, dynamic>> _performOcr(Map<String, dynamic> parameters) async {
    try {
      final screenshotBase64 = parameters['screenshot'] as String?;
      if (screenshotBase64 == null || screenshotBase64.isEmpty) {
        return {
          'success': false,
          'error': 'Missing screenshot',
          'data': null,
        };
      }

      final result = await _toolsChannel.invokeMethod('performOcr', {
        'screenshot': screenshotBase64,
      });
      return {
        'success': result['success'] == true,
        'data': result,
        'error': result['success'] == true ? null : (result['error']?.toString() ?? 'OCR failed'),
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Failed to perform OCR: $e',
        'data': null,
      };
    }
  }

  // ==================== UTILITY METHODS ====================

  /// Check if a tool is available
  static bool isToolAvailable(String toolName) {
    return getAvailableTools().contains(toolName);
  }

  /// Get list of all available tools
  static List<String> getAvailableTools() {
    return [
      // Screen Capture & Analysis
      'take_screenshot',
      'get_accessibility_tree',
      'get_screen_elements',
      'analyze_screen',
      'perform_ocr',
      'get_current_app',

      // Touch Operations
      'perform_tap',
      'perform_long_press',
      'perform_double_click',
      'tap_element_by_text',
      'tap_element_by_index',
      'tap_element_by_bounds',

      // Gesture Operations
      'perform_swipe',
      'perform_scroll',
      'perform_dynamic_scroll',
      'perform_pinch',
      'perform_zoom_in',
      'perform_zoom_out',

      // Text Operations
      'perform_advanced_type',
      'advanced_type_text',
      'non_tap_text_input',
      'get_focused_input_info',

      'clear_text',
      'select_all_text',
      'copy_text',
      'paste_text',
      'replace_text',
      'type_text_slowly',
      'insert_text',

      // Input Chip Operations


      // Key Events
      'perform_enter',
      'perform_backspace',
      'perform_delete',
      'send_key_event',

      // UI Interaction
      'find_and_click',
      'perform_back',

      // App Management
      'open_app',
      'open_app_by_name',
      'get_launchable_apps',
      'get_installed_apps',
      'find_matching_apps',
      'search_apps',
      'get_best_matching_app',

      // Navigation
      'perform_home',
      'perform_recents',
      'open_settings',
      'open_notifications',
      'open_quick_settings',

      // Permissions
      'check_accessibility_permission',
      'request_accessibility_permission',
      'check_overlay_permission',
      'request_overlay_permission',

      // Automation
      'execute_user_task',
    ];
  }

  /// Get tools organized by category
  static Map<String, List<String>> getToolsByCategory() {
    return {
      'screen_analysis': [
        'take_screenshot',
        'get_accessibility_tree',
        'get_screen_elements',
        'analyze_screen',
        'perform_ocr',
        'get_current_app',
      ],
      'touch_gestures': [
        'perform_tap',
         'perform_long_press',
         'tap_ocr_text',
         'tap_ocr_bounds',
        'perform_double_click',
        'perform_swipe',
        'perform_scroll',
        'perform_dynamic_scroll',
        'perform_pinch',
        'perform_zoom_in',
        'perform_zoom_out',
      ],
      'text_input': [
        'perform_advanced_type',
        'advanced_type_text',
        'non_tap_text_input',
        'get_focused_input_info',
        'get_all_input_fields',
        'clear_text',
        'select_all_text',
        'copy_text',
        'paste_text',
        'replace_text',
        'type_text_slowly',
        'insert_text',
        'perform_enter',
        'perform_backspace',
        'perform_delete',
        'send_key_event',
        // Input Chip Operations
        'get_all_input_chips',
        'click_input_chip_by_text',
        'select_input_chip_by_text',
        'toggle_input_chip_by_text',
        'find_input_chip_by_text',
        'is_input_chip',
        'get_input_chip_info',
        'perform_smart_chip_interaction',
      ],
      'ui_interaction': [
        'find_and_click',
        'perform_back',
      ],
      'app_management': [
        'open_app',
        'open_app_by_name',
        'get_launchable_apps',
        'get_installed_apps',
        'find_matching_apps',
        'search_apps',
        'get_best_matching_app',
      ],
      'navigation': [
        'perform_home',
        'perform_recents',
        'open_settings',
        'open_notifications',
        'open_quick_settings',
      ],
      'permissions': [
        'check_accessibility_permission',
        'request_accessibility_permission',
        'check_overlay_permission',
        'request_overlay_permission',
      ],
      'automation': [
        'execute_user_task',
      ],
    };
  }

  /// Get tool description for AI context
  static String getToolDescription(String toolName) {
    const descriptions = {
      // Screen Analysis
      'take_screenshot':
          'Capture a screenshot of the current screen as base64 encoded image',
      'get_accessibility_tree':
          'Get the accessibility tree of UI elements on screen',
      'get_screen_elements':
          'Get detailed information about all interactive elements on screen',
      'analyze_screen':
           'Analyze the current screen and return structured information',
      'perform_ocr':
          'Run on-device OCR on a base64 screenshot and return extracted text and blocks',
       'get_current_app':
           'Get information about the currently active application',

      // Touch Operations
      'perform_tap': 'Tap at specific coordinates (x, y)',
       'perform_long_press': 'Long press at coordinates with optional duration',
       'tap_ocr_text': 'Tap using OCR-matched text block {"text": "..."}',
       'tap_ocr_bounds': 'Tap using explicit OCR bounds {"left","top","right","bottom"}',
      'perform_double_click': 'Double tap at specific coordinates',

      // Gestures
      'perform_swipe': 'Swipe from start coordinates to end coordinates',
      'perform_scroll':
          'Scroll in a specific direction (up, down, left, right)',
      'perform_dynamic_scroll':
          'Intelligently scroll to find target text with automatic direction reversal and end detection',
      'perform_pinch': 'Perform pinch gesture for zoom',
      'perform_zoom_in': 'Zoom in on the screen',
      'perform_zoom_out': 'Zoom out on the screen',

      // Text Operations
      'perform_advanced_type': 'Type text with advanced input method support',
      'advanced_type_text': 'Type text with options to clear first and add delays',
      'non_tap_text_input': 'Inject text directly into input fields without tapping using AccessibilityService',
      'get_focused_input_info': 'Get information about the currently focused input field including ID, class, and text selection',

      'clear_text': 'Clear text from the currently focused input field',
      'select_all_text': 'Select all text in the current input field',
      'copy_text': 'Copy selected text to clipboard',
      'paste_text': 'Paste text from clipboard',
      'replace_text': 'Replace all text in input field with new text',
      'type_text_slowly': 'Type text character by character with delays',
      'insert_text': 'Insert text at current cursor position',

      // Input Chip Operations


      // Key Events
      'perform_enter': 'Press the Enter/Return key',
      'perform_backspace': 'Press the Backspace key',
      'perform_delete': 'Press the Delete key',
      'send_key_event': 'Send specific key event by key code',

      // UI Interaction
      'find_and_click': 'Find UI element by text/description and click it',
      'perform_back': 'Navigate back (equivalent to back button)',

      // App Management
      'open_app': 'Open app by package name',
      'open_app_by_name': 'Open app by display name',
      'get_launchable_apps': 'Get list of all launchable apps',
      'get_installed_apps': 'Get list of all installed apps',
      'find_matching_apps': 'Find apps matching a name pattern',
      'search_apps': 'Search apps by keyword',
      'get_best_matching_app': 'Get the best matching app for a given name',

      // Navigation
      'perform_home': 'Navigate to home screen',
      'perform_recents': 'Open recent apps screen',
      'open_settings': 'Open device settings',
      'open_notifications': 'Open notification panel',
      'open_quick_settings': 'Open quick settings panel',

      // Permissions
      'check_accessibility_permission':
          'Check if accessibility permission is granted',
      'request_accessibility_permission': 'Request accessibility permission',
      'check_overlay_permission': 'Check if overlay permission is granted',
      'request_overlay_permission': 'Request overlay permission',

      // Automation
      'execute_user_task': 'Execute a complex user task using AI automation',
    };

    return descriptions[toolName] ?? 'No description available';
  }
}
