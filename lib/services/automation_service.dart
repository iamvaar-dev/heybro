import 'dart:convert';
import 'dart:async';
import 'package:flutter/services.dart';
import '../vertex_ai_client.dart';
import '../tools/tools_manager.dart';

class AutomationService {
  // Singleton pattern
  static final AutomationService _instance = AutomationService._internal();
  factory AutomationService() => _instance;
  AutomationService._internal();
  
  // Method channel for Android communication
  static const MethodChannel _channel = MethodChannel('com.vibeagent.dude/automation');
  
  // Callback functions
  Function(String)? onMessage;
  Function(String)? onError;
  Function()? onComplete;
  Function(bool)? onAutomationStateChanged;

  // Service state
  bool _isAutomating = false;
  bool _isInitialized = false;
  VertexAIClient? _aiClient;
  bool _llmRequestOngoing = false;
  DateTime? _lastLlmRequestedAt;
  Map<String, dynamic>? _lastContext; // latest captured screen context

  // Task state
  String? _currentTask;
  List<Map<String, dynamic>> _taskHistory = [];
  int _currentStep = 0;
  
  // Field tracking state - prevent repeated taps on same field
  Set<String> _processedFields = {};
  Map<String, dynamic>? _lastTappedField;
  
  // Replay state - store tap coordinates for replay functionality
  Map<String, dynamic>? _lastTapCoordinates;

  // Public getters
  bool get isAutomating => _isAutomating;
  bool get isInitialized => _isInitialized;
  Map<String, dynamic>? get lastTapCoordinates => _lastTapCoordinates;

  /// Initialize the automation service
  Future<void> initialize() async {
    if (_isInitialized) {
      print('⚠️ AutomationService singleton already initialized');
      return;
    }

    try {
      print('🔧 Initializing AutomationService singleton...');

      // Set up method channel handler to receive calls from Android
      _channel.setMethodCallHandler(_handleMethodCall);
      print('📡 Method channel handler set up for automation service singleton');

      // Initialize AI client
      _aiClient = VertexAIClient();
      await _aiClient!.initialize();

      // Skip AI testing - start automation instantly
      _isInitialized = true;
      print('✅ AutomationService singleton initialized successfully and ready for voice commands');
    } catch (e) {
      print('❌ Failed to initialize AutomationService singleton: $e');
      throw e;
    }
  }

  /// Handle method calls from Android
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    print('🤖 Received method call: ${call.method}');
    
    try {
      switch (call.method) {
        case 'executeUserTask':
          final userTask = call.arguments['user_task'] as String?;
          if (userTask != null && userTask.isNotEmpty) {
            print('🎯 Starting automation for task: $userTask');
            // Initialize if not already done
            if (!_isInitialized) {
              await initialize();
            }
            // Start automation
            await startAutomation(userTask);
            return true;
          } else {
            print('❌ Invalid user task provided');
            return false;
          }
        
        case 'isAutomationActive':
          return _isAutomating;
        
        case 'stopAutomation':
          stopAutomation();
          return true;
        
        default:
          print('❌ Unknown method call: ${call.method}');
          return false;
      }
    } catch (e) {
      print('❌ Error handling method call ${call.method}: $e');
      _notifyError('Failed to execute task: $e');
      return false;
    }
  }

  // AI testing removed - automation starts instantly

  /// Start automation with user task
  Future<void> startAutomation(String userMessage) async {
    if (!_isInitialized) {
      throw Exception('AutomationService not initialized');
    }

    if (_isAutomating) {
      throw Exception('Automation already running');
    }

    _currentTask = userMessage;
    _taskHistory.clear();
    _currentStep = 0;
    _processedFields.clear(); // Reset field tracking for new task
    _lastTappedField = null;
    _isAutomating = true;
    
    // Notify UI that automation state changed to true
    onAutomationStateChanged?.call(true);

    try {
      // Start the automation loop
      await _executeAutomationLoop();
    } catch (e) {
      _notifyError('Automation failed: $e');
    } finally {
      _isAutomating = false;
      // Notify UI that automation state changed to false
      onAutomationStateChanged?.call(false);
    }
  }

  /// Stop automation
  void stopAutomation() {
    if (_isAutomating) {
      _isAutomating = false;
      // Notify UI that automation state changed to false
      onAutomationStateChanged?.call(false);
      _notifyMessage('🛑 Automation stopped by user');
      // Notify Android to send broadcast for voice service overlay closing
      _notifyComplete();
    }
  }

  /// Execute automation loop
  Future<void> _executeAutomationLoop() async {
    Map<String, dynamic>? previousContext;
    bool forceContextRefresh = false;

    while (_isAutomating) {
      _currentStep++;

      // Get current screen context
      final screenContext = await _captureScreenContext();

      // Validate context freshness - ensure screen state has actually changed
      // Skip validation if we need to force refresh (after scroll actions)
      if (!forceContextRefresh && previousContext != null && _isContextIdentical(previousContext, screenContext)) {
        print('⚠️ Screen context unchanged - waiting for UI state change...');
        await Future.delayed(const Duration(milliseconds: 2000));
        continue;
      }

      // Reset force refresh flag
      forceContextRefresh = false;

      // Build AI prompt with visual context and strict validation rules
      final prompt = _buildStepPrompt(screenContext);

      // Get AI decision
      final aiResponse = await _getAIDecision(prompt);
      if (aiResponse == null) {
        _notifyError('Failed to get AI response');
        break;
      }

      // Store current context for next iteration comparison
      previousContext = Map<String, dynamic>.from(screenContext);

      // Execute the AI's decision
      final shouldContinue = await _processAIDecision(aiResponse);
      if (!shouldContinue) {
        break;
      }

      // Check if action was a scroll - if so, wait longer for content to settle
      final action = aiResponse['action'] as String?;
      final isScrollAction = action != null && (action.contains('scroll') || action.contains('swipe'));
      
      if (isScrollAction) {
        // Extended wait for scroll actions to allow content loading and UI settling
        print('🔄 Scroll action detected - waiting for content to settle...');
        await Future.delayed(const Duration(milliseconds: 4000));
        // Force fresh context capture on next iteration
        forceContextRefresh = true;
        print('🔄 Forcing fresh context capture after scroll action');
      } else {
        // Standard wait for other actions
        await Future.delayed(const Duration(milliseconds: 2500));
      }
    }

    // No step limit - automation continues until task completion or manual stop
  }

  /// Capture current screen context for AI
  Future<Map<String, dynamic>> _captureScreenContext() async {
    try {
      print('📱 Capturing screen context...');

      // Get current app info
      final currentAppResult = await ToolsManager.executeTool('get_current_app', {});
      print('🔍 currentAppResult: success=${currentAppResult['success']}, data type=${currentAppResult['data']?.runtimeType}, raw data=${currentAppResult['data']}');

      Map<String, dynamic> currentApp;
      try {
        if (currentAppResult['success'] == true && currentAppResult['data'] is Map) {
          currentApp = Map<String, dynamic>.from(currentAppResult['data'] ?? {});
          print('✅ Successfully parsed current app: $currentApp');
        } else {
          currentApp = <String, dynamic>{};
          print('⚠️ Current app result not a map, using empty map');
        }
      } catch (e) {
        print('❌ Error parsing current app: $e');
        currentApp = <String, dynamic>{};
      }

      // Get accessibility tree
      final treeResult = await ToolsManager.executeTool('get_accessibility_tree', {});
      print('🔍 treeResult: success=${treeResult['success']}, data type=${treeResult['data']?.runtimeType}, raw data=${treeResult['data']}');

      List<dynamic> accessibilityTree;
      try {
        if (treeResult['success'] == true && treeResult['data'] is List) {
          accessibilityTree = List.from(treeResult['data'] ?? []);
          print('✅ Successfully parsed accessibility tree: ${accessibilityTree.length} items');
        } else {
          accessibilityTree = <dynamic>[];
          print('⚠️ Accessibility tree result not a list, using empty list. Data: ${treeResult['data']}');
          if (treeResult['data'] != null && treeResult['data'] is! List) {
            print('🚨 CRITICAL: accessibility tree data is ${treeResult['data']?.runtimeType}, expected List');
          }
        }
      } catch (e) {
        print('❌ Error parsing accessibility tree: $e');
        accessibilityTree = <dynamic>[];
      }

      // Detect system dialogs in accessibility tree
      final systemDialogs = _detectSystemDialogs(accessibilityTree, currentApp);
      if (systemDialogs.isNotEmpty) {
        print('🔔 Detected ${systemDialogs.length} system dialog(s)');
      }

      // Derive screen elements from accessibility tree to avoid duplicate extraction
      List<dynamic> screenElements;
      if (accessibilityTree.isNotEmpty) {
        screenElements = List.from(accessibilityTree);
        print('ℹ️ Using accessibility tree for screen elements (${screenElements.length})');
      } else {
        screenElements = <dynamic>[];
      }

      // Try to take screenshot
      final screenshotResult = await ToolsManager.executeTool('take_screenshot', {});
      final screenshotAvailable = screenshotResult['success'] == true;
      print('🔍 screenshotResult: success=${screenshotResult['success']}, data type=${screenshotResult['data']?.runtimeType}');

      // Fallback to OCR when accessibility tree is empty or likely web content
      String ocrText = '';
      List<dynamic> ocrBlocks = const [];
      final isA11yEmpty = accessibilityTree.isEmpty;
      final classHints = _collectClassHints(accessibilityTree);
      final looksLikeWeb = classHints.any((c) => c.contains('WebView') || c.contains('webview') || c.contains('ComposeView'));
      Map<String, double>? contextDimensions = {};
      if ((isA11yEmpty || looksLikeWeb) && screenshotAvailable) {
        final screenshotB64 = (screenshotResult['data'] as String?);
        if (screenshotB64 != null && screenshotB64.isNotEmpty) {
          print('🟡 Accessibility tree is ${isA11yEmpty ? 'empty' : 'possibly web content'} → running OCR...');
          final ocrResult = await ToolsManager.executeTool('perform_ocr', {
            'screenshot': screenshotB64,
          });
          if (ocrResult['success'] == true && ocrResult['data'] is Map) {
            final data = Map<String, dynamic>.from(ocrResult['data']);
            ocrText = (data['text']?.toString() ?? '').trim();
            ocrBlocks = (data['blocks'] is List) ? List.from(data['blocks']) : <dynamic>[];
            // Attach OCR image dimensions for coordinate normalization
            if (data['imageWidth'] != null && data['imageHeight'] != null) {
              contextDimensions = {
                'ocrImageWidth': (data['imageWidth'] as num).toDouble(),
                'ocrImageHeight': (data['imageHeight'] as num).toDouble(),
              };
            }
            if (ocrText.isNotEmpty) {
              print('✅ OCR extracted ${ocrText.length} chars (${ocrBlocks.length} blocks)');
            } else {
              print('⚠️ OCR returned no text');
            }
          } else {
            print('❌ OCR failed: ${ocrResult['error']}');
          }
        }
      }

      print('📊 Context captured - App: ${currentApp['packageName'] ?? 'Unknown'}, Elements: ${screenElements.length}, Tree: ${accessibilityTree.length}');

      final context = {
        'current_app': currentApp,
        'screen_elements': screenElements,
        'accessibility_tree': accessibilityTree,
        'system_dialogs': systemDialogs,
        'screenshot_available': screenshotAvailable,
        'ocr_text': ocrText,
        'ocr_blocks': ocrBlocks,
        'ocr_image_width': (contextDimensions['ocrImageWidth'] ?? 0.0),
        'ocr_image_height': (contextDimensions['ocrImageHeight'] ?? 0.0),
        'timestamp': DateTime.now().millisecondsSinceEpoch,
      };

      print('🔍 Final context structure: ${context.runtimeType}');
      print('🔍 Context keys: ${context.keys.toList()}');
      print('🔍 Each field type: current_app=${context['current_app']?.runtimeType}, screen_elements=${context['screen_elements']?.runtimeType}, accessibility_tree=${context['accessibility_tree']?.runtimeType}');
      if ((context['ocr_text'] as String).isNotEmpty) {
        print('🔤 OCR text: ${(context['ocr_text'] as String)}');
      }
      // Check for screen changes and reset field tracking if needed
      if (_hasScreenChanged(context)) {
        _resetFieldTracking();
      }
      
      // Store for downstream helpers
      _lastContext = context;
      return context;
    } catch (e) {
      print('❌ Failed to capture screen context: $e');
      final fallback = {
        'current_app': <String, dynamic>{},
        'screen_elements': <dynamic>[],
        'accessibility_tree': <dynamic>[],
        'screenshot_available': false,
        'ocr_text': '',
        'ocr_blocks': <dynamic>[],
        'ocr_image_width': 0.0,
        'ocr_image_height': 0.0,
        'error': e.toString(),
      };
      // Reset field tracking on error (likely screen change)
      _resetFieldTracking();
      _lastContext = fallback;
      return fallback;
    }
  }

  List<String> _collectClassHints(List<dynamic> screenElements) {
    final hints = <String>[];
    for (final el in screenElements) {
      try {
        if (el is Map) {
          final map = Map<String, dynamic>.from(el);
          final className = map['className']?.toString() ?? map['type']?.toString() ?? '';
          if (className.isNotEmpty) hints.add(className);
        }
      } catch (_) {}
    }
    return hints;
  }

/// Build AI prompt for next step
/// Validate if screen context has meaningfully changed
bool _isContextIdentical(Map<String, dynamic> prev, Map<String, dynamic> current) {
  try {
    // Always consider context changed after scroll actions to force fresh analysis
    final currentTimestamp = current['timestamp'] as int? ?? 0;
    final prevTimestamp = prev['timestamp'] as int? ?? 0;
    final timeDiff = currentTimestamp - prevTimestamp;
    
    // If timestamps are very close (< 3 seconds), do deeper comparison
    if (timeDiff < 3000) {
      // Compare accessibility tree structure and content
      final prevTree = prev['accessibility_tree'] as List? ?? [];
      final currentTree = current['accessibility_tree'] as List? ?? [];
      if (prevTree.length != currentTree.length) return false;
      
      // Deep compare first few elements for content changes
      for (int i = 0; i < (prevTree.length < 5 ? prevTree.length : 5); i++) {
        if (i < currentTree.length) {
          final prevEl = prevTree[i] is Map ? Map<String, dynamic>.from(prevTree[i]) : {};
          final currentEl = currentTree[i] is Map ? Map<String, dynamic>.from(currentTree[i]) : {};
          if (prevEl['text'] != currentEl['text'] || prevEl['bounds'] != currentEl['bounds']) {
            return false;
          }
        }
      }
      
      // Compare OCR text content
      final prevOcr = prev['ocr_text'] as String? ?? '';
      final currentOcr = current['ocr_text'] as String? ?? '';
      if (prevOcr != currentOcr) return false;
      
      // Compare current app
      final prevApp = prev['current_app'] as Map? ?? {};
      final currentApp = current['current_app'] as Map? ?? {};
      if (prevApp['packageName'] != currentApp['packageName']) return false;
      
      return true;
    }
    
    // If significant time has passed, assume context has changed
    return false;
  } catch (e) {
    return false; // Assume changed if comparison fails
  }
}

String _buildStepPrompt(Map<String, dynamic> context) {
  final currentApp = context['current_app'] is Map
      ? Map<String, dynamic>.from(context['current_app'])
      : <String, dynamic>{};
  final screenElements = context['screen_elements'] is List
      ? List.from(context['screen_elements'])
      : <dynamic>[];
  final accessibilityTree = context['accessibility_tree'] is List
      ? List.from(context['accessibility_tree'])
      : <dynamic>[];
  final systemDialogs = context['system_dialogs'] is List
      ? (context['system_dialogs'] as List).cast<Map<String, dynamic>>()
      : <Map<String, dynamic>>[];
  final hasScreenshot = context['screenshot_available'] == true;
  final ocrText = context['ocr_text'] is String ? (context['ocr_text'] as String) : '';
  final hasOcr = ocrText.isNotEmpty;
  // Extract simple hints for form inputs from a11y and OCR
  final inputHints = <String>[];
  final a11y = context['accessibility_tree'];
  if (a11y is List) {
    for (final n in a11y) {
      try {
        if (n is Map) {
          final m = Map<String, dynamic>.from(n);
          if (m['editable'] == true) {
            final label = (m['text']?.toString() ??
                    m['contentDescription']?.toString() ??
                    m['className']?.toString() ??
                    '')
                .trim();
            if (label.isNotEmpty) inputHints.add(label);
          }
        }
      } catch (_) {}
    }
  }

  // Build history summary
  String historyText = '';
  if (_taskHistory.isNotEmpty) {
    historyText = '\nCOMPLETED STEPS:\n';
    for (int i = 0; i < _taskHistory.length; i++) {
      final step = _taskHistory[i];
      historyText += '${i + 1}. ${step['action']} - ${step['description']}\n';
    }
  }

  // LEGACY PROMPT - COMMENTED OUT FOR REFERENCE
  /*
  return '''
You are an advanced Android automation AI with the sole responsibility of executing user-defined tasks step-by-step with maximum precision, reliability, and context-awareness. You function inside a task orchestration environment where every detail of the screen, accessibility data, OCR, and system dialogs is provided to you for exact decision-making. Your objective is always task completion.... every step you output must move closer to the defined goal.  

🎯 CURRENT TASK CONTEXT  
- Task: $_currentTask  
- Execution Step: $_currentStep  
- Progress History: $historyText  

📱 SCREEN CONTEXT ANALYSIS  
- Screenshot Available: $hasScreenshot  
- Interactive Elements Count: ${screenElements.length}  
- Accessibility Elements Count: ${accessibilityTree.length}  
- System Dialogs: ${systemDialogs.length}  
- OCR Text Available: $hasOcr  
- Editable Field Hints: ${inputHints.isEmpty ? '[]' : inputHints}  

🔘 INTERACTIVE ELEMENTS  
${_formatElements(screenElements)}  

🌳 ACCESSIBILITY TREE  
${_formatAccessibilityTree(accessibilityTree)}  

💬 SYSTEM DIALOGS  
${systemDialogs.isNotEmpty ? _formatSystemDialogs(systemDialogs) : '[No system dialogs detected]'}  

🔍 OCR EXTRACTED TEXT  
${hasOcr ? ocrText : '[No OCR text available]'}  

---

🎯 CRITICAL TASK EXECUTION PRINCIPLES  
1. FRESH CONTEXT ANALYSIS.... You MUST analyze the CURRENT screen state completely fresh each time. DO NOT make assumptions based on previous steps.  
2. DYNAMIC DECISION MAKING.... Every decision must be based ONLY on the current context provided. Ignore any mental models from previous steps.  
3. SCREEN STATE VALIDATION.... Before each action, verify the current screen matches your expectations. If not, reassess completely.  
4. Task-First Approach.... Always prioritize the user's requested task, not the current screen context.  
5. App Routing.... If the task requires another app, immediately open it (ignore current context).  
6. Context-Specific Routing....  
   • Food/Restaurant → Zomato, Swiggy, UberEats  
   • Transport → Uber, Ola, Google Maps  
   • Shopping → Amazon, Flipkart  
   • Communication → WhatsApp, Gmail, SMS  
   • Entertainment → YouTube, Netflix, Spotify  
7. Search Optimization.... Always locate and activate search input before typing queries.  
8. Form Handling.... Multi-field forms must be filled sequentially in correct logical order.  

---

🚨 STRICT EXECUTION PROTOCOLS  
- MANDATORY: Analyze current context completely fresh - no assumptions from previous steps  
- MANDATORY: Verify screen state matches expectations before proceeding  
- MANDATORY: Use precise OCR bounds for tapping when available - calculate exact center coordinates  
- MANDATORY SCROLL PROTOCOL: Before ANY scroll action, thoroughly analyze current accessibility tree and OCR text to verify target content is NOT already visible on screen  
- MANDATORY POST-SCROLL: After scroll actions, ALWAYS wait for fresh context capture (updated a11y tree + OCR) before making next decision  
- MANDATORY SCROLL VALIDATION: Check if target text/element exists in current context before scrolling - avoid unnecessary scrolls  
- One action per response.  
- Always follow natural UI flows (tap → type → confirm).  
- Use accessibility indices whenever possible.  
- If accessibility unavailable, use OCR bounds with precise coordinate calculation.  
- Only use manual coordinates if no other methods succeed.  
- Always click/focus input fields before typing.  
- Validate every typed input, retry with alternate methods if needed.  
- Element indexes start at 0 and must match the given lists.  
- OCR bounds format: {"left":x,"top":y,"right":x2,"bottom":y2} - tap at center: ((left+right)/2, (top+bottom)/2)  

---

⚡ AVAILABLE ACTIONS (must be used exactly as specified for precision):  
• take_screenshot  
• tap_element_by_text {"text": "..."}  
• tap_element_by_index {"index": number}  
• tap_element_by_bounds {"left":..,"top":..,"right":..,"bottom":..}  
• tap_ocr_text {"text": "..."}  
• tap_ocr_bounds {"left":..,"top":..,"right":..,"bottom":..}  
• perform_tap {"x":..,"y":..}  
• perform_long_press {"x":..,"y":..}  
• perform_swipe {"startX":..,"startY":..,"endX":..,"endY":..}  
• perform_scroll {"direction": "up/down/left/right"} - ONLY use after verifying target is NOT in current context  
• perform_dynamic_scroll {"direction": "up/down/left/right","targetText":"...","maxScrollAttempts":n} - ONLY use after verifying target is NOT in current context  
• type_text {"text": "..."}  
• focus_input_field {"x":..,"y":..,"text":"...","className":"..."}  
• advanced_type_text {"text":"...","clearFirst":true/false,"delayMs":n}  
• clear_text_field {"x":..,"y":..,"text":"..."}  
• replace_text_field {"x":..,"y":..,"text":"old_text","newText":"..."}  
• fill_form_fields {"fields":[{"type":"...","value":"...","selector":"..."}]}  
• open_app_by_name {"appName":"..."}  
• perform_back  
• perform_home  
• perform_enter  

🔄 SCROLL DECISION MATRIX:  
1. BEFORE SCROLL: Search current accessibility tree + OCR text for target content  
2. IF FOUND: Use tap_element_by_text, tap_element_by_index, or tap_ocr_text instead  
3. IF NOT FOUND: Proceed with scroll action  
4. AFTER SCROLL: Wait for next context refresh to analyze updated screen state  

---

📋 FORM PROCESSING STRATEGY  
1. Identify editable fields (via accessibility tree + OCR).  
2. Determine field types (email, subject, body, search, etc.).  
3. Focus precisely on field (accessibility > OCR > coordinates).  
4. Input using advanced_type_text with clearFirst if pre-filled.  
5. Handle suggestions/dropdowns by tapping relevant OCR text.  
6. Validate input → retry if incorrect.  
7. Continue field-by-field until form completion.  

---

✅ CRITICAL RESPONSE FORMAT  
You must only respond with raw JSON in the following structure:  
{
  "action": "action_name",
  "parameters": {"key": "value"},
  "description": "What this step does",
  "is_complete": false,
  "reasoning": "Why this action"
}  

- If task is finished, "is_complete": true.  
- Never output text, explanations, or code fences outside the JSON.  

---

⚖️ DECISION PRIORITY  
1. Accessibility elements (most precise)  
2. OCR text/bounds (if accessibility missing)  
3. Manual coordinates (last resort)  
''';  
  */

  // ENHANCED HUMAN-LIKE AUTOMATOR AI AGENT PROMPT
  return '''
🤖 HUMAN-LIKE MOBILE AUTOMATOR AGENT
You are an intelligent mobile automation agent that mimics human interaction patterns with Android devices. Think like a human user who naturally navigates apps, types text, scrolls through content, and completes tasks efficiently. Your goal is to execute the given task with human-like precision and intuition but use with declared tools..

🎯 MISSION BRIEFING
- Primary Task: $_currentTask
- Current Step: $_currentStep
- Journey So Far: $historyText

📱 CURRENT SCREEN INTELLIGENCE
- Visual Context: $hasScreenshot
- Interactive Elements: ${screenElements.length} available
- Accessibility Nodes: ${accessibilityTree.length} detected
- System Alerts: ${systemDialogs.length} active
- Text Recognition: $hasOcr
- Input Field Hints: ${inputHints.isEmpty ? 'None detected' : inputHints.join(', ')}

🎮 INTERACTIVE ELEMENTS MAP
${_formatElements(screenElements)}

🌲 ACCESSIBILITY NAVIGATION TREE
${_formatAccessibilityTree(accessibilityTree)}

🚨 SYSTEM NOTIFICATIONS & DIALOGS
${systemDialogs.isNotEmpty ? _formatSystemDialogs(systemDialogs) : '[Clean screen - no system interruptions]'}

👁️ VISUAL TEXT RECOGNITION (OCR)
${hasOcr ? ocrText : '[No readable text detected on screen]'}

═══════════════════════════════════════════════════════════════

🧠 HUMAN-LIKE AUTOMATION INTELLIGENCE

FIRST AND FOREMOST RULE: PRIORITIZE open_app_by_name FOR ALL APP SWITCHING/OPENING
• ALWAYS use open_app_by_name as the PRIMARY method for opening or switching to any application
• Use exact app labels (e.g., "YouTube", "Chrome", "WhatsApp", "Whatsapp Business", Gmail", "Maps")
• Only fallback to home screen navigation if open_app_by_name fails
• NEVER interact with the AI agent screen - always switch to the target app immediately
• Based on user context, decide the related app and open it using open_app_by_name with perfect label

🎯 CORE BEHAVIORAL PATTERNS:
• FRESH EYES APPROACH: Analyze each screen as if seeing it for the first time
• NATURAL FLOW: Follow intuitive user journeys (search → browse → select → confirm)
• CONTEXT AWARENESS: Understand app-specific UI patterns and conventions
• ADAPTIVE STRATEGY: Switch between touch methods based on what's most reliable
• GOAL-ORIENTED: Every action must advance toward task completion

🎪 SMART APP ROUTING:
• Food & Dining → Zomato, Swiggy, UberEats, DoorDash
• Transportation → Uber, Ola, Google Maps, Lyft
• Shopping → Amazon, Flipkart, eBay, Myntra
• Communication → WhatsApp, Gmail, Telegram, SMS
• Entertainment → YouTube, Netflix, Spotify, Instagram
• Productivity → Google Drive, Docs, Calendar, Notes

🎨 INTELLIGENT INTERACTION STRATEGIES:

📝 TEXT INPUT MASTERY:
• ⚠️ MANDATORY: ALWAYS tap/focus input fields before typing - NEVER type without tapping first
• ⚠️ CRITICAL: Even if a field appears focused, ALWAYS tap it before typing to ensure proper focus
• Use advanced_type_text with clearFirst for pre-filled fields
• Handle autocomplete suggestions intelligently
• Validate input success and retry with alternative methods if needed
• Support multi-field forms with logical sequential filling

🔄 SMART SCROLLING BEHAVIOR:
• BEFORE SCROLL: Thoroughly scan current accessibility tree + OCR for target content
• HUMAN-LIKE PATTERN: Scroll in natural chunks, not excessive amounts
• POST-SCROLL PATIENCE: Wait for content to load and UI to stabilize
• CONTENT VALIDATION: Verify new content appeared before next action
• AVOID REDUNDANCY: Never scroll if target is already visible

🎯 PRECISION TARGETING HIERARCHY:
1. ACCESSIBILITY-FIRST: Use element indices when available (most reliable)
2. OCR-SMART: Leverage text recognition with precise coordinate calculation
3. COORDINATE-FALLBACK: Manual coordinates only when other methods fail
4. BOUNDS-CALCULATION: For OCR bounds {"left":x,"top":y,"right":x2,"bottom":y2}, tap center: ((left+right)/2, (top+bottom)/2)

🚀 AVAILABLE HUMAN-LIKE ACTIONS:

📱 CORE INTERACTIONS:
• take_screenshot - Capture current screen state
• tap_element_by_text {"text": "exact_text"} - Tap by visible text
• tap_element_by_index {"index": number} - Tap by accessibility index
• tap_element_by_bounds {"left":x,"top":y,"right":x2,"bottom":y2} - Tap by coordinates
• tap_ocr_text {"text": "visible_text"} - Tap OCR-detected text
• tap_ocr_bounds {"left":x,"top":y,"right":x2,"bottom":y2} - Tap OCR bounds
• perform_tap {"x":x,"y":y} - Direct coordinate tap with overlay detection and node-based fallback
• perform_long_press {"x":x,"y":y} - Long press gesture

🎮 NAVIGATION & GESTURES:
• perform_swipe {"startX":x1,"startY":y1,"endX":x2,"endY":y2} - Swipe gesture
• perform_scroll {"direction": "up/down/left/right"} - Natural scrolling
• perform_dynamic_scroll {"direction": "up/down","targetText":"search_text","maxScrollAttempts":3} - Smart target scrolling
• perform_back - Navigate back
• perform_home - Go to home screen
• perform_enter - Press enter key

⌨️ TEXT INPUT ARSENAL:
• advanced_type_text {"text":"content","clearFirst":true,"delayMs":100} - Advanced typing with options
• non_tap_text_input {"text":"content", "fieldId":"optional_field_id"} - Direct text injection without tapping (uses AccessibilityService with overlay detection)
• get_focused_input_info {} - Get information about currently focused input field

🎯 CRITICAL INPUT FIELD PROTOCOLS:
• OVERLAY DETECTION: System automatically detects suggestion lists, dropdowns, and overlays that interfere with input
• NODE-BASED INTERACTIONS: When overlays are present, uses AccessibilityNodeInfo actions instead of coordinate taps
• ⚠️ ALWAYS TAP FIRST: Before typing, MUST tap the exact input field using tap_element_by_text, tap_element_by_bounds, or tap_ocr_text
• ⚠️ NO EXCEPTIONS: Even if a field is already focused, ALWAYS tap it before typing to prevent input failures
• FIELD IDENTIFICATION: Scan accessibility tree for EditText, TextField, or input elements with proper bounds
• CONTEXT AWARENESS: Read field labels, placeholders, and hints to understand what input is expected
• SUGGESTION HANDLING: System intelligently handles autocomplete dropdowns without coordinate interference
• MULTI-FIELD STRATEGY: For multiple inputs on screen, process ONE field at a time with proper focus
• VALIDATION FEEDBACK: Check for error states, field highlighting, or validation messages after input
• COORDINATE SAFETY: Never trust raw coordinates when overlays are detected - always resolve to actual UI nodes

🚀 APP MANAGEMENT:
• open_app_by_name {"appName": "App Name"} - Launch specific application

═══════════════════════════════════════════════════════════════

🎯 INTELLIGENT DECISION FRAMEWORK:

🔍 PRE-ACTION ANALYSIS:
1. SCREEN COMPREHENSION: What app am I in? What's the current context?
2. TASK ALIGNMENT: Does this screen help achieve the goal?
3. ELEMENT IDENTIFICATION: What interactive elements are available?
4. OPTIMAL PATH: What's the most human-like way to proceed?

🔍 ENHANCED SCREEN CONTEXT ANALYSIS:
• INPUT FIELD DETECTION: Scan accessibility tree for EditText, TextField, and input elements
• FIELD LABELING: Identify field labels, placeholders, hints, and associated text
• FIELD POSITIONING: Note exact bounds and coordinates of each input field
• FIELD STATES: Check if fields are empty, filled, focused, or showing errors
• SUGGESTION ELEMENTS: Look for autocomplete dropdowns, suggestion lists, or picker dialogs
• KEYBOARD STATE: Determine if virtual keyboard is visible and affecting layout

🧠 HUMAN DYNAMIC BEHAVIORS:
• NATURAL HESITATION: Occasionally pause to "read" content before acting (simulate human processing time)
• EXPLORATION PATTERNS: Sometimes check nearby elements before selecting target (human curiosity)
• ADAPTIVE SCROLLING: Vary scroll distances - small precise scrolls vs larger sweeping motions
• CONTEXTUAL AWARENESS: Reference previous actions and learn from app patterns
• ERROR RECOVERY: When actions fail, try alternative approaches like a human would
• READING SIMULATION: Spend time on text-heavy screens before proceeding
• PROGRESSIVE DISCLOSURE: Gradually explore UI elements rather than jumping directly to targets

🎪 SCROLL INTELLIGENCE MATRIX:
┌─ BEFORE SCROLL: Scan accessibility tree + OCR for target content
├─ IF TARGET FOUND: Use tap_element_by_text/index/ocr instead of scrolling
├─ IF TARGET MISSING: Execute scroll with human-like distance
├─ AFTER SCROLL: Wait for UI stabilization and content loading
└─ VALIDATE PROGRESS: Confirm new content appeared before next action

📋 FORM COMPLETION MASTERY:
1. FIELD DISCOVERY: Identify all input fields via accessibility + OCR
2. LOGICAL ORDERING: Fill fields in natural human sequence (top-to-bottom, left-to-right)
3. FIELD FOCUSING: ⚠️ MANDATORY - Tap each field before typing (even if already focused)
4. ⚠️ NO REPETITION: Never tap the same field twice - process each field only once
5. SEQUENTIAL PROCESSING: Complete one field fully before moving to the next
6. SMART INPUT: Use advanced_type_text with appropriate options
7. VALIDATION: Confirm input success before moving to next field
8. SUGGESTION HANDLING: Intelligently interact with autocomplete/dropdowns

⌨️ MANDATORY INPUT FIELD WORKFLOW:
1. IDENTIFY TARGET: Locate the specific input field in accessibility tree or OCR
2. TAP TO FOCUS: Use tap_element_by_text, tap_element_by_bounds, or tap_ocr_text to focus field
3. VERIFY FOCUS: Confirm cursor appears in the correct field
4. TYPE CONTENT: Use type_text or advanced_type_text with appropriate content
5. HANDLE SUGGESTIONS: If autocomplete appears, decide whether to select or continue typing
6. VALIDATE INPUT: Check for errors, field highlighting, or validation feedback
7. PROCEED TO NEXT: Only move to next field after current field is complete

NEVER type without first tapping the target input field!

═══════════════════════════════════════════════════════════════

🚫 CRITICAL EXECUTION SAFEGUARDS:

🔄 ACTION EXECUTION INTEGRITY:
• MANDATORY EXECUTION: Every action specified in the JSON response MUST be executed via tool call
• NO PHANTOM COMPLETION: Never mark steps as complete without actually performing the required action
• TOOL CALL VALIDATION: Each JSON response must be immediately followed by the corresponding function execution
• ACTION-RESULT COUPLING: Wait for tool execution result before proceeding to next step
• PROGRESS VERIFICATION: Confirm each action achieved its intended effect before moving forward

🎯 NEXT STEP PLANNING EXCELLENCE:
• SCREEN-FIRST ANALYSIS: Always analyze current screen state before determining next action
• CONTEXTUAL PROGRESSION: Each step must logically build upon previous actions and current screen context
• GOAL CONVERGENCE: Every action must demonstrably advance toward task completion
• ADAPTIVE PATHFINDING: Adjust strategy based on actual screen content and available elements
• OBSTACLE NAVIGATION: Handle unexpected screens, popups, or UI changes intelligently
• COMPLETION CRITERIA: Only set "is_complete": true when final objective is visibly achieved

🧠 SMART DECISION MATRIX:
1. SCREEN ASSESSMENT: What's currently visible and available for interaction?
2. PROGRESS EVALUATION: How does current state compare to desired outcome?
3. NEXT LOGICAL STEP: What specific action will move closest to completion?
4. EXECUTION METHOD: Which tool/approach is most reliable for this action?
5. SUCCESS VALIDATION: How will I know this action succeeded?
6. CONTINGENCY PLANNING: What alternatives exist if primary approach fails?

🔍 ENHANCED STEP PLANNING LOGIC:
• MICRO-PROGRESSION: Break complex actions into granular, executable steps
• STATE DEPENDENCIES: Recognize when certain screen states are prerequisites for next actions
• UI TIMING: Account for loading states, animations, and response delays
• ELEMENT AVAILABILITY: Confirm required elements exist before attempting interaction
• FALLBACK STRATEGIES: Prepare alternative approaches for common interaction failures
• COMPLETION SIGNALS: Identify clear indicators that mark task completion

═══════════════════════════════════════════════════════════════

✅ RESPONSE PROTOCOL
Respond ONLY with this exact JSON structure:

{
  "action": "action_name",
  "parameters": {"key": "value"},
  "description": "Human-readable explanation of this step",
  "is_complete": false,
  "reasoning": "Why this action makes sense in current context and how it advances toward completion"
}

RULES:
• Set "is_complete": true only when the entire task is finished AND visually confirmed
• Never include explanatory text outside the JSON
• One action per response for precision and verification
• Always include comprehensive reasoning that explains progression logic
• Each action must be immediately executable with current screen elements
• Wait for tool execution completion before considering step finished

═══════════════════════════════════════════════════════════════

🎯 EXECUTION PRIORITY STACK:
🥇 Accessibility elements (highest reliability)
🥈 OCR text/bounds (visual backup)
🥉 Manual coordinates (last resort)

🎪 DYNAMIC EXECUTION BEHAVIORS:
• CONTEXT SWITCHING: Acknowledge when moving between different app sections
• LEARNING PATTERNS: Reference similar actions performed earlier in the session
• UNCERTAINTY HANDLING: Express when multiple options exist and explain choice reasoning
• PROGRESS TRACKING: Mention how current action advances toward the overall goal
• ENVIRONMENTAL AWARENESS: Notice and comment on UI changes, loading states, or transitions
• ADAPTIVE STRATEGY: Adjust approach based on app responsiveness and UI patterns
• HUMAN CURIOSITY: Occasionally explore or mention interesting UI elements discovered

Now analyze the current screen with fresh human-like intelligence and determine the next optimal action to advance toward the goal!

''';
}


  /// Get AI decision for next step
  Future<Map<String, dynamic>?> _getAIDecision(String prompt) async {
    try {
      // Prevent duplicate concurrent requests
      if (_llmRequestOngoing) {
        print('⚠️ LLM request already in-flight; skipping duplicate');
        return null;
      }

      // Light throttle to avoid back-to-back calls
      final now = DateTime.now();
      if (_lastLlmRequestedAt != null) {
        final since = now.difference(_lastLlmRequestedAt!);
        if (since < const Duration(milliseconds: 800)) {
          final waitMs = 800 - since.inMilliseconds;
          await Future.delayed(Duration(milliseconds: waitMs));
        }
      }

      _llmRequestOngoing = true;
      _lastLlmRequestedAt = DateTime.now();
      print('🧠 Sending prompt to AI...');
      final response = await _aiClient!.generateContent(prompt);

      if (response == null || response.isEmpty) {
        print('❌ Empty response from AI');
        return null;
      }

      print('🤖 AI Response received');
      print('📄 Raw response: $response');

      // Extract JSON from response
      final jsonResponse = _extractJsonFromResponse(response);
      if (jsonResponse == null) {
        print('❌ Failed to parse JSON from AI response');
        print('📄 Response was: $response');
        return null;
      }

      print('✅ Successfully parsed AI decision');
      return jsonResponse;
    } catch (e) {
      print('❌ Error getting AI decision: $e');
      return null;
    } finally {
      _llmRequestOngoing = false;
      _lastLlmRequestedAt = DateTime.now();
    }
  }

  /// Extract JSON from AI response
  Map<String, dynamic>? _extractJsonFromResponse(String response) {
    try {
      // Remove any markdown formatting
      String cleanResponse = response.trim();

      // Remove ```json and ``` if present
      if (cleanResponse.startsWith('```json')) {
        cleanResponse = cleanResponse.substring(7);
      }
      if (cleanResponse.startsWith('```')) {
        cleanResponse = cleanResponse.substring(3);
      }
      if (cleanResponse.endsWith('```')) {
        cleanResponse = cleanResponse.substring(0, cleanResponse.length - 3);
      }

      // Find JSON object
      final startIndex = cleanResponse.indexOf('{');
      final endIndex = cleanResponse.lastIndexOf('}');

      if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
        return null;
      }

      final jsonString = cleanResponse.substring(startIndex, endIndex + 1).trim();
      return jsonDecode(jsonString) as Map<String, dynamic>;
    } catch (e) {
      print('❌ JSON parsing error: $e');
      return null;
    }
  }

  /// Process and execute AI decision
  Future<bool> _processAIDecision(Map<String, dynamic> decision) async {
    try {
      // Extract action details (may still be present even if is_complete=true)
      final isComplete = decision['is_complete'] == true;
      final action = decision['action'] as String?;
      final parameters = decision['parameters'] as Map<String, dynamic>? ?? {};
      final description = decision['description'] as String?;
      final reasoning = decision['reasoning'] as String?;

      if (!isComplete && action == null) {
        return false;
      }

      // Validate action sequence to prevent shortcuts
      if (action != null && !_validateActionSequence(action, parameters)) {
        return false;
      }

      // Send the raw JSON decision to UI instead of descriptive messages
      _notifyMessage(jsonEncode(decision));

      // Execute the action if provided and task is not complete
      bool success = true;
      if (action != null && !isComplete) {
        success = await _executeAction(action, parameters);
      }

      // Record in history with enhanced metadata
      _taskHistory.add({
        'step': _currentStep,
        'action': action ?? '',
        'description': (description ?? action) ?? '',
        'success': success,
        'timestamp': DateTime.now().millisecondsSinceEpoch,
        'parameters': parameters,
        'ui_context': _extractUIContext(action ?? '', parameters, description),
        'interaction_type':
            _classifyInteractionType(action ?? '', parameters, description),
      });

      // If LLM marked complete, only end after action attempt
      if (isComplete) {
        // Task completion - send final status as JSON
        // When LLM marks is_complete=true, the task is considered successful
        final completionStatus = {
          'task_completed': true,
          'success': true, // LLM marking complete means task succeeded
          'task': _currentTask,
          'final_action': action,
          'description': description ?? 'Automation finished'
        };
        _notifyMessage(jsonEncode(completionStatus));
        // Notify Android to send broadcast for voice service overlay closing
        _notifyComplete();
        return false;
      }

      return success;
    } catch (e) {
      _notifyError('Failed to process AI decision: $e');
      return false;
    }
  }

  /// Validate action sequence to prevent shortcuts
  bool _validateActionSequence(String action, Map<String, dynamic> parameters) {
    // Check for common shortcut patterns
    if (action == 'type_text') {
      // Check if we recently clicked on an input field or search button
      final recentActions = _taskHistory.length >= 2
          ? _taskHistory.sublist(_taskHistory.length - 2)
          : _taskHistory;

      final hasRecentInputInteraction = recentActions.any((step) {
        final uiContext = step['ui_context'] as Map<String, dynamic>? ?? {};
        final interactionType = step['interaction_type'] as String? ?? '';

        return uiContext['is_input_interaction'] == true ||
            uiContext['is_search_related'] == true ||
            interactionType == 'search_initiation' ||
            interactionType == 'ui_element_click';
      });

      if (!hasRecentInputInteraction && _taskHistory.isNotEmpty) {
        _notifyMessage(
            '🚫 SHORTCUT DETECTED: Cannot type text without first clicking an input field or search button');
        return false;
      }
    }

    // Validate search-related actions
    if (action == 'type_text' &&
        (_currentTask?.toLowerCase().contains('search') ?? false)) {
      final text = parameters['text'] as String? ?? '';
      if (text.isNotEmpty) {
        final recentSearchClick = _taskHistory.any((step) {
          final interactionType = step['interaction_type'] as String? ?? '';
          final uiContext = step['ui_context'] as Map<String, dynamic>? ?? {};

          return interactionType == 'search_initiation' ||
              (uiContext['is_search_related'] == true &&
                  uiContext['is_button_click'] == true);
        });

        if (!recentSearchClick) {
          _notifyMessage(
              '🚫 SEARCH SHORTCUT DETECTED: Must click search button/field before typing search query');
          return false;
        }
      }
    }

    return true;
  }

  /// Extract UI context from action
  Map<String, dynamic> _extractUIContext(
      String action, Map<String, dynamic> parameters, String? description) {
    final context = <String, dynamic>{};
    final desc = description?.toLowerCase() ?? '';

    // Detect search-related interactions
    context['is_search_related'] = desc.contains('search') ||
        desc.contains('find') ||
        (action == 'type_text' &&
            (_currentTask?.toLowerCase().contains('search') ?? false));

    // Detect input field interactions
    context['is_input_interaction'] = desc.contains('input') ||
        desc.contains('field') ||
        desc.contains('text') ||
        desc.contains('edit') ||
        action == 'type_text';

    // Detect button/clickable interactions
    context['is_button_click'] = action == 'find_and_click' ||
        action == 'perform_tap' ||
        action.startsWith('tap_element') ||
        action.startsWith('tap_ocr') ||
        desc.contains('button') ||
        desc.contains('click');

    // Extract target element text for clicks
    if (action == 'find_and_click' && parameters.containsKey('text')) {
      context['target_text'] = parameters['text'];
    }

    // Extract typed text
    if (action == 'type_text' && parameters.containsKey('text')) {
      context['typed_text'] = parameters['text'];
    }

    return context;
  }

  /// Classify the type of interaction
  String _classifyInteractionType(
      String action, Map<String, dynamic> parameters, String? description) {
    final desc = description?.toLowerCase() ?? '';

    if (desc.contains('search') && action.contains('click')) {
      return 'search_initiation';
    }

    if (action == 'type_text' &&
        (_currentTask?.toLowerCase().contains('search') ?? false)) {
      return 'search_query_input';
    }

    if (action == 'type_text') {
      return 'text_input';
    }

    if (action == 'find_and_click' || action == 'perform_tap' || action.startsWith('tap_element') || action.startsWith('tap_ocr')) {
      return 'ui_element_click';
    }

    if (action == 'perform_scroll') {
      return 'navigation_scroll';
    }

    if (action == 'open_app_by_name') {
      return 'app_launch';
    }

    return 'general_action';
  }

  /// Find exact element from accessibility tree
  Map<String, dynamic>? _findElementByText(List elements, String targetText) {
    for (int i = 0; i < elements.length; i++) {
      try {
        final element = elements[i];
        if (element is Map) {
          final elementMap = Map<String, dynamic>.from(element);
          final text = elementMap['text']?.toString() ?? '';
          final contentDesc = elementMap['contentDescription']?.toString() ?? '';

          if (text.toLowerCase().contains(targetText.toLowerCase()) ||
              contentDesc.toLowerCase().contains(targetText.toLowerCase())) {
            return {
              ...elementMap,
              'index': i,
              'matched_text': text.isNotEmpty ? text : contentDesc,
            };
          }
        }
      } catch (e) {
        print('Error processing element $i: $e');
        continue;
      }
    }
    return null;
  }

  /// Find element by index
  Map<String, dynamic>? _findElementByIndex(List elements, int index) {
    if (index >= 0 && index < elements.length) {
      try {
        final element = elements[index];
        if (element is Map) {
          final elementMap = Map<String, dynamic>.from(element);
          return {
            ...elementMap,
            'index': index,
          };
        }
      } catch (e) {
        print('Error processing element at index $index: $e');
      }
    }
    return null;
  }

  /// Find element by bounds
  Map<String, dynamic>? _findElementByBounds(List elements, Map<String, dynamic> targetBounds) {
    for (int i = 0; i < elements.length; i++) {
      try {
        final element = elements[i];
        if (element is Map) {
          final elementMap = Map<String, dynamic>.from(element);
          final bounds = elementMap['bounds'];

          if (bounds != null && bounds is Map) {
            final boundsMap = Map<String, dynamic>.from(bounds);
            final left = boundsMap['left']?.toDouble() ?? 0.0;
            final top = boundsMap['top']?.toDouble() ?? 0.0;
            final right = boundsMap['right']?.toDouble() ?? 0.0;
            final bottom = boundsMap['bottom']?.toDouble() ?? 0.0;

            final targetLeft = targetBounds['left']?.toDouble() ?? 0.0;
            final targetTop = targetBounds['top']?.toDouble() ?? 0.0;
            final targetRight = targetBounds['right']?.toDouble() ?? 0.0;
            final targetBottom = targetBounds['bottom']?.toDouble() ?? 0.0;

            // Check if bounds match (with small tolerance)
            if ((left - targetLeft).abs() < 5 &&
                (top - targetTop).abs() < 5 &&
                (right - targetRight).abs() < 5 &&
                (bottom - targetBottom).abs() < 5) {
              return {
                ...elementMap,
                'index': i,
              };
            }
          }
        }
      } catch (e) {
        print('Error processing element $i for bounds matching: $e');
        continue;
      }
    }
    return null;
  }

  /// Get precise tap coordinates from element
  Map<String, double> _getPreciseTapCoordinates(Map<String, dynamic> element) {
    final bounds = element['bounds'];
    if (bounds == null || bounds is! Map) {
      return {'x': 0.0, 'y': 0.0};
    }

    try {
      final boundsMap = Map<String, dynamic>.from(bounds);

      // Get element bounds
      final left = boundsMap['left']?.toDouble() ?? 0.0;
      final top = boundsMap['top']?.toDouble() ?? 0.0;
      final right = boundsMap['right']?.toDouble() ?? 0.0;
      final bottom = boundsMap['bottom']?.toDouble() ?? 0.0;
      final width = boundsMap['width']?.toDouble() ?? (right - left);
      final height = boundsMap['height']?.toDouble() ?? (bottom - top);

      // Calculate center point for optimal clicking
      double tapX = left + (width / 2.0);
      double tapY = top + (height / 2.0);

      // Ensure coordinates are within bounds and not on edges
      final padding = 2.0;
      tapX = tapX.clamp(left + padding, right - padding).toDouble();
      tapY = tapY.clamp(top + padding, bottom - padding).toDouble();

      return {'x': tapX, 'y': tapY};
    } catch (e) {
      print('Error calculating tap coordinates: $e');
      return {'x': 0.0, 'y': 0.0};
    }
  }

  /// Execute precise element tap by text
  Future<bool> _executePreciseElementTap(String targetText, List elements) async {
    final element = _findElementByText(elements, targetText);
    if (element == null) {
      _notifyMessage('❌ Element not found with text: "$targetText"');
      return false;
    }

    final coords = _getPreciseTapCoordinates(element);
    final elementInfo = element['matched_text'] ?? targetText;
    final index = element['index'];

    _notifyMessage('🎯 Found element [$index]: "$elementInfo"');
    _notifyMessage('📍 Precise tap at (${coords['x']!.round()}, ${coords['y']!.round()})');

    // Execute precise tap using ToolsManager
    final result = await ToolsManager.executeTool('perform_tap', {
      'x': coords['x'],
      'y': coords['y'],
    });

    return result['success'] == true;
  }

  /// Execute precise element tap by index
  Future<bool> _executePreciseElementTapByIndex(int index, List elements) async {
    final element = _findElementByIndex(elements, index);
    if (element == null) {
      _notifyMessage('❌ Element not found at index: $index');
      return false;
    }

    final coords = _getPreciseTapCoordinates(element);
    final elementText = element['text']?.toString() ??
                      element['contentDescription']?.toString() ??
                      element['type']?.toString() ?? 'Unknown';

    _notifyMessage('🎯 Tapping element [$index]: "$elementText"');
    _notifyMessage('📍 Precise tap at (${coords['x']!.round()}, ${coords['y']!.round()})');

    // Save tap coordinates for replay functionality
    _lastTapCoordinates = {
      'x': coords['x'],
      'y': coords['y'],
      'element_text': elementText,
      'element_index': index,
    };

    // Execute precise tap using ToolsManager
    final result = await ToolsManager.executeTool('perform_tap', {
      'x': coords['x'],
      'y': coords['y'],
    });

    return result['success'] == true;
  }

  /// Execute precise element tap by bounds
  Future<bool> _executePreciseElementTapByBounds(Map<String, dynamic> targetBounds, List elements) async {
    final element = _findElementByBounds(elements, targetBounds);
    if (element == null) {
      _notifyMessage('❌ Element not found with specified bounds');
      return false;
    }

    final coords = _getPreciseTapCoordinates(element);
    final index = element['index'];
    final elementText = element['text']?.toString() ??
                      element['contentDescription']?.toString() ??
                      element['type']?.toString() ?? 'Unknown';

    _notifyMessage('🎯 Found element [$index]: "$elementText"');
    _notifyMessage('📍 Precise tap at (${coords['x']!.round()}, ${coords['y']!.round()})');

    // Execute precise tap using ToolsManager
    final result = await ToolsManager.executeTool('perform_tap', {
      'x': coords['x'],
      'y': coords['y'],
    });

    return result['success'] == true;
  }

  /// Execute a tap action during replay using saved coordinates
  Future<bool> _executeReplayTap(Map<String, dynamic> actionData) async {
    // Check if we have saved coordinates from the original action
    if (actionData.containsKey('saved_coordinates')) {
      final coords = actionData['saved_coordinates'] as Map<String, dynamic>;
      final x = coords['x'] as double?;
      final y = coords['y'] as double?;
      
      if (x != null && y != null) {
        _notifyMessage('🔄 Replaying tap at saved coordinates (${x.round()}, ${y.round()})');
        
        // Execute tap using saved coordinates
        final result = await ToolsManager.executeTool('perform_tap', {
          'x': x,
          'y': y,
        });
        
        return result['success'] == true;
      }
    }
    
    // Fallback to regular execution if no saved coordinates
    _notifyMessage('⚠️ No saved coordinates found, using regular tap execution');
    final parameters = actionData['parameters'] as Map<String, dynamic>? ?? {};
    return await _executeAction('tap_element_by_index', parameters);
  }

  /// Execute an action directly for replay functionality
  Future<bool> executeActionDirectly(Map<String, dynamic> actionData) async {
    try {
      final action = actionData['action'] as String?;
      final parameters = actionData['parameters'] as Map<String, dynamic>? ?? {};
      
      if (action == null) {
        print('❌ No action specified in action data');
        return false;
      }
      
      // Special handling for tap_element_by_index during replay
      if (action == 'tap_element_by_index') {
        return await _executeReplayTap(actionData);
      }
      
      return await _executeAction(action, parameters);
    } catch (e) {
      print('❌ Error executing action directly: $e');
      return false;
    }
  }

  /// Execute a specific action with enhanced precision tapping
  Future<bool> _executeAction(String action, Map<String, dynamic> parameters) async {
    try {
      print('🔧 Executing: $action with params: $parameters');

      if (action == 'message') {
        final text = parameters['text'] as String?;
        if (text != null) {
          _notifyMessage('💬 $text');
        }
        return true;
      }

      // Handle precise element actions first
      if (action == 'tap_element_by_text') {
        final text = parameters['text'] as String?;
        if (text == null || text.isEmpty) {
          _notifyMessage('❌ No text specified for element tap');
          return false;
        }

        // Use latest captured elements to avoid duplicate tool calls
        final elements = _getElementsFromLastContext();

        return await _executePreciseElementTap(text, elements);
      }

      if (action == 'tap_element_by_index') {
        final index = parameters['index'] as int?;
        if (index == null) {
          _notifyMessage('❌ No index specified for element tap');
          return false;
        }

        // Use latest captured elements to avoid duplicate tool calls
        final elements = _getElementsFromLastContext();

        return await _executePreciseElementTapByIndex(index, elements);
      }

      if (action == 'tap_element_by_bounds') {
        final bounds = parameters;
        if (!bounds.containsKey('left') || !bounds.containsKey('top') ||
            !bounds.containsKey('right') || !bounds.containsKey('bottom')) {
          _notifyMessage('❌ Invalid bounds specified for element tap');
          return false;
        }

        // Use latest captured elements to avoid duplicate tool calls
        final elements = _getElementsFromLastContext();

        return await _executePreciseElementTapByBounds(bounds, elements);
      }

      // Ensure input focus before typing by tapping OCR-detected input/search if needed
      if (action == 'type_text' || action == 'advanced_type_text') {
        final textToType = parameters['text']?.toString() ?? '';
        await _ensureInputFocusBeforeTyping(textToType, force: true);
      }

      // OCR-based actions (use when relying on OCR text/regions)
      if (action == 'tap_ocr_text') {
        final text = parameters['text'] as String?;
        if (text == null || text.isEmpty) {
          _notifyMessage('❌ No text specified for OCR tap');
          return false;
        }

        // Prefer OCR from latest context to avoid duplicate runs
        final ctx = _lastContext ?? <String, dynamic>{};
        List ocrBlocks = (ctx['ocr_blocks'] is List) ? List.from(ctx['ocr_blocks']) : <dynamic>[];
        // Only run OCR on-demand if a11y is empty or looks like web AND screenshot is available
        final a11y = ctx['accessibility_tree'];
        final a11yEmpty = !(a11y is List) || a11y.isEmpty;
        final looksWeb = _lastContextLooksLikeWeb();
        final hasScreenshot = ctx['screenshot_available'] == true;
        if (ocrBlocks.isEmpty && hasScreenshot && (a11yEmpty || looksWeb)) {
          final ss = await ToolsManager.executeTool('take_screenshot', {});
          if (ss['success'] == true && ss['data'] is String) {
            final ocrRes = await ToolsManager.executeTool('perform_ocr', { 'screenshot': ss['data'] });
            if (ocrRes['success'] == true && ocrRes['data'] is Map) {
              final data = Map<String, dynamic>.from(ocrRes['data']);
              _lastContext ??= <String, dynamic>{};
              _lastContext!['ocr_text'] = data['text']?.toString() ?? '';
              _lastContext!['ocr_blocks'] = (data['blocks'] is List) ? List.from(data['blocks']) : <dynamic>[];
              ocrBlocks = List.from(_lastContext!['ocr_blocks'] as List);
            }
          }
        }

        if (ocrBlocks.isEmpty) {
          _notifyMessage('❌ No OCR blocks available to tap');
          return false;
        }

        final block = _findBestOcrBlock(ocrBlocks, text);
        if (block == null) {
          _notifyMessage('❌ No matching OCR block for: "$text"');
          return false;
        }

        final bounds = _getBoundsFromOcrBlock(block);
        final coords = _getCenterFromBounds(bounds);
        _notifyMessage('🎯 OCR tap on "$text" at (${coords['x']!.round()}, ${coords['y']!.round()})');
        final result = await ToolsManager.executeTool('perform_tap', { 'x': coords['x'], 'y': coords['y'] });
        return result['success'] == true;
      }

      if (action == 'tap_ocr_bounds') {
        final hasAll = parameters.containsKey('left') && parameters.containsKey('top') && parameters.containsKey('right') && parameters.containsKey('bottom');
        if (!hasAll) {
          _notifyMessage('❌ Invalid OCR bounds');
          return false;
        }
        final coords = _getCenterFromBounds(parameters);
        _notifyMessage('📍 OCR bounds tap at (${coords['x']!.round()}, ${coords['y']!.round()})');
        final result = await ToolsManager.executeTool('perform_tap', { 'x': coords['x'], 'y': coords['y'] });
        return result['success'] == true;
      }

      // Check if action is available
      if (!ToolsManager.isToolAvailable(action)) {
        _notifyMessage('❌ Action not available: $action');
        return false;
      }

      // Execute the tool
      var result = await ToolsManager.executeTool(action, parameters);

      // If typing failed, try to recover by focusing via OCR and retry once
      if ((action == 'type_text' || action == 'advanced_type_text') && result['success'] != true) {
        _notifyMessage('🔁 Retrying ${action} after focusing input via OCR');
        final textToType = parameters['text']?.toString() ?? '';
        await _ensureInputFocusBeforeTyping(textToType, force: true);
        result = await ToolsManager.executeTool(action, parameters);
      }

      return result['success'] == true;
    } catch (e) {
      _notifyError('Tool execution failed: $action - $e');
      return false;
    }
  }

  /// Format screen elements for AI prompt with precise targeting info
  String _formatElements(List elements) {
    if (elements.isEmpty) return 'No interactive elements found.';

    final buffer = StringBuffer();
    buffer.writeln('INTERACTIVE ELEMENTS (use index numbers for tap_element_by_index):');

    for (int i = 0; i < elements.length; i++) {
      try {
        final element = elements[i];
        if (element is Map) {
          try {
            final elementMap = element is Map<String, dynamic>
                ? element
                : Map<String, dynamic>.from(element);
            final text = elementMap['text']?.toString() ?? '';
            final contentDesc = elementMap['contentDescription']?.toString() ?? '';
            final type = elementMap['className']?.toString() ?? elementMap['type']?.toString() ?? 'Unknown';
            final bounds = elementMap['bounds'];
            final clickable = elementMap['clickable'] == true ? '✓ CLICKABLE' : '✗ Not clickable';
            final scrollable = elementMap['scrollable'] == true ? '📜' : '';

            // Format bounds info
            String boundsInfo = 'Unknown bounds';
            if (bounds != null && bounds is Map) {
              final boundsMap = Map<String, dynamic>.from(bounds);
              final x = boundsMap['x'] ?? boundsMap['centerX'] ?? 0;
              final y = boundsMap['y'] ?? boundsMap['centerY'] ?? 0;
              final w = boundsMap['width'] ?? 0;
              final h = boundsMap['height'] ?? 0;
              boundsInfo = 'center($x,$y) size(${w}x$h)';
            }

            // Build display content
            String content;
            if (text.isNotEmpty) {
              content = '"$text"';
            } else if (contentDesc.isNotEmpty) {
              content = '"$contentDesc"';
            } else {
              content = '[No text]';
            }

            buffer.writeln('[$i] $type: $content | $clickable $scrollable | $boundsInfo');
          } catch (e) {
            print('Error processing element $i: $e');
            buffer.writeln('[$i] Error: Could not process element');
          }
        }
      } catch (e) {
        print('Error formatting element $i: $e');
        buffer.writeln('[$i] Error: Could not format element');
      }
    }
    return buffer.toString();
  }

  /// Format accessibility tree for AI prompt with precise targeting info
  String _formatAccessibilityTree(List tree) {
    if (tree.isEmpty) return 'No accessibility tree available.';

    final buffer = StringBuffer();
    buffer.writeln('ACCESSIBILITY TREE (use index numbers for tap_element_by_index):');

    for (int i = 0; i < tree.length; i++) {
      try {
        final node = tree[i];
        if (node is Map) {
          try {
            final nodeMap = node is Map<String, dynamic>
                ? node
                : Map<String, dynamic>.from(node);
            final text = nodeMap['text']?.toString() ?? '';
            final contentDescription = nodeMap['contentDescription']?.toString() ?? '';
            final className = nodeMap['className']?.toString() ?? nodeMap['type']?.toString() ?? 'Node';
            final clickable = nodeMap['clickable'] == true ? '✓ CLICKABLE' : '✗ Not clickable';
            final scrollable = nodeMap['scrollable'] == true ? '��' : '';
            final editable = nodeMap['editable'] == true ? '✏️' : '';
            final bounds = nodeMap['bounds'];

            // Only show elements with meaningful content or interactions
            if (text.isNotEmpty || contentDescription.isNotEmpty ||
                nodeMap['clickable'] == true || nodeMap['scrollable'] == true || nodeMap['editable'] == true) {

              // Format bounds for precise tapping
              String boundsInfo = '';
              if (bounds != null && bounds is Map) {
                final boundsMap = Map<String, dynamic>.from(bounds);
                final left = boundsMap['left'] ?? 0;
                final top = boundsMap['top'] ?? 0;
                final right = boundsMap['right'] ?? 0;
                final bottom = boundsMap['bottom'] ?? 0;
                final centerX = boundsMap['x'] ?? boundsMap['centerX'] ?? ((left + right) / 2).round();
                final centerY = boundsMap['y'] ?? boundsMap['centerY'] ?? ((top + bottom) / 2).round();
                boundsInfo = ' | bounds(L:$left,T:$top,R:$right,B:$bottom) center($centerX,$centerY)';
              }

              // Show content
              String content = '';
              if (text.isNotEmpty && contentDescription.isNotEmpty && text != contentDescription) {
                content = '"$text" / "$contentDescription"';
              } else if (text.isNotEmpty) {
                content = '"$text"';
              } else if (contentDescription.isNotEmpty) {
                content = '"$contentDescription"';
              } else {
                content = '[No text]';
              }

              buffer.writeln('[$i] $className: $content | $clickable $scrollable $editable$boundsInfo');
            }
          } catch (e) {
            print('Error processing tree node $i: $e');
            buffer.writeln('[$i] Error: Could not process tree node');
          }
        }
      } catch (e) {
        print('Error formatting tree node $i: $e');
        // Still show the problematic element for debugging
        buffer.writeln('[$i] Error: Could not format tree node');
      }
    }
    return buffer.toString();
  }

  Map<String, dynamic> _getBoundsFromOcrBlock(Map block) {
    try {
      if (block['boundingBox'] is Map) {
        final bb = Map<String, dynamic>.from(block['boundingBox']);
        return {
          'left': (bb['left'] as num?)?.toDouble() ?? 0.0,
          'top': (bb['top'] as num?)?.toDouble() ?? 0.0,
          'right': (bb['right'] as num?)?.toDouble() ?? 0.0,
          'bottom': (bb['bottom'] as num?)?.toDouble() ?? 0.0,
        };
      }
    } catch (_) {}
    return { 'left': 0.0, 'top': 0.0, 'right': 0.0, 'bottom': 0.0 };
  }

  Map<String, double> _getCenterFromBounds(Map bounds) {
    final left = (bounds['left'] as num?)?.toDouble() ?? 0.0;
    final top = (bounds['top'] as num?)?.toDouble() ?? 0.0;
    final right = (bounds['right'] as num?)?.toDouble() ?? 0.0;
    final bottom = (bounds['bottom'] as num?)?.toDouble() ?? 0.0;
    double x = left + (right - left) / 2.0;
    double y = top + (bottom - top) / 2.0;
    // Normalize to device-screen coordinates if OCR image dimensions differ from screen
    final screenDims = _estimateScreenSizeFromLastA11y();
    final ocrW = (_lastContext?['ocr_image_width'] as num?)?.toDouble() ?? 0.0;
    final ocrH = (_lastContext?['ocr_image_height'] as num?)?.toDouble() ?? 0.0;
    if (screenDims != null && ocrW > 0 && ocrH > 0) {
      final sx = screenDims['width'];
      final sy = screenDims['height'];
      if (sx != null && sy != null && sx > 0 && sy > 0) {
        final scaleX = sx / ocrW;
        final scaleY = sy / ocrH;
        x = x * scaleX;
        y = y * scaleY;
      }
    }
    return { 'x': x, 'y': y };
  }

  Map<String, double>? _estimateScreenSizeFromLastA11y() {
    try {
      final a11y = _lastContext?['accessibility_tree'];
      if (a11y is List && a11y.isNotEmpty) {
        double maxRight = 0.0;
        double maxBottom = 0.0;
        for (final n in a11y) {
          if (n is Map) {
            final m = Map<String, dynamic>.from(n);
            final b = m['bounds'];
            if (b is Map) {
              final right = (b['right'] as num?)?.toDouble() ?? 0.0;
              final bottom = (b['bottom'] as num?)?.toDouble() ?? 0.0;
              if (right > maxRight) maxRight = right;
              if (bottom > maxBottom) maxBottom = bottom;
            }
          }
        }
        if (maxRight > 0 && maxBottom > 0) {
          return {'width': maxRight, 'height': maxBottom};
        }
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  Map<String, dynamic>? _findBestOcrBlock(List blocks, String target) {
    try {
      final normTarget = _normalizeText(target);
      Map<String, dynamic>? best;
      double bestScore = 0.0;
      for (final b in blocks) {
        if (b is Map && b['text'] is String) {
          final text = (b['text'] as String);
          final score = _textMatchScore(_normalizeText(text), normTarget);
          if (score > bestScore) {
            bestScore = score;
            best = Map<String, dynamic>.from(b);
          }
        }
      }
      // Require minimal score to avoid false taps
      if (bestScore < 0.25) return null;
      print('🔎 OCR match score=$bestScore for "$target"');
      return best;
    } catch (e) {
      print('Error selecting OCR block: $e');
      return null;
    }
  }

  String _normalizeText(String s) {
    return s.toLowerCase().replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  double _textMatchScore(String text, String target) {
    if (text.contains(target)) return 1.0;
    // token overlap
    final textTokens = text.split(' ').where((t) => t.isNotEmpty).toSet();
    final targetTokens = target.split(' ').where((t) => t.isNotEmpty).toSet();
    if (targetTokens.isEmpty) return 0.0;
    final overlap = textTokens.intersection(targetTokens).length.toDouble();
    final score = overlap / targetTokens.length;
    // also consider prefix similarity
    final prefix = _commonPrefixLength(text, target).toDouble();
    return (score * 0.7) + ((prefix / (target.length == 0 ? 1 : target.length)) * 0.3);
  }

  int _commonPrefixLength(String a, String b) {
    final n = a.length < b.length ? a.length : b.length;
    int i = 0;
    while (i < n && a.codeUnitAt(i) == b.codeUnitAt(i)) {
      i++;
    }
    return i;
  }

  /// Send message to UI
  void _notifyMessage(String message) {
    print('📱 UI Message: $message');
    onMessage?.call(message);
  }

  /// Send error to UI
  void _notifyError(String error) {
    print('❌ UI Error: $error');
    onError?.call(error);
    
    // Notify Android to send broadcast
    _notifyAndroidCompletion(false, error);
  }

  /// Notify task completion
  void _notifyComplete() {
    print('✅ Task automation completed');
    onComplete?.call();
    
    // Notify Android to send broadcast
    _notifyAndroidCompletion(true);
  }

  /// Notify Android of automation completion/failure
  Future<void> _notifyAndroidCompletion(bool success, [String? error]) async {
    try {
      if (success) {
        await _channel.invokeMethod('notifyAutomationComplete');
        print('🔔 Notified Android of automation completion');
      } else {
        await _channel.invokeMethod('notifyAutomationError', {'error': error ?? 'Unknown error'});
        print('🔔 Notified Android of automation error: $error');
      }
    } catch (e) {
      print('❌ Failed to notify Android: $e');
    }
  }

  /// Cleanup resources
  void dispose() {
    _isAutomating = false;
    _isInitialized = false;
    _aiClient = null;
    onMessage = null;
    onError = null;
    onComplete = null;
  }

  /// Ensure an input field is focused before typing by tapping OCR targets like 'search'
  Future<void> _ensureInputFocusBeforeTyping(String intendedText, {bool force = false}) async {
    try {
      // Always tap on input fields before typing to ensure proper focus
      // This prevents typing failures when no focused field is found

      // Use latest context to avoid duplicate capture; capture only if missing
      var context = _lastContext ?? await _captureScreenContext();
      // If we have accessibility elements, try to focus an editable node first
      final a11y = context['accessibility_tree'];
      if (a11y is List && a11y.isNotEmpty) {
        final editableIdx = _findBestUnprocessedEditableIndex(a11y);
        if (editableIdx != -1) {
          final fieldId = 'a11y_$editableIdx';
          // Check if this field was already processed
          if (!_processedFields.contains(fieldId)) {
            _processedFields.add(fieldId);
            _lastTappedField = {'type': 'accessibility', 'index': editableIdx};
            await _executeAction('tap_element_by_index', {'index': editableIdx});
            await Future.delayed(const Duration(milliseconds: 250));
            return;
          }
        }
      }

      List blocks = (context['ocr_blocks'] is List) ? List.from(context['ocr_blocks']) : <dynamic>[];
      if (blocks.isEmpty && context['screenshot_available'] == true && ((a11y is! List) || a11y.isEmpty || _lastContextLooksLikeWeb())) {
        final ss = await ToolsManager.executeTool('take_screenshot', {});
        if (ss['success'] == true && ss['data'] is String) {
          final ocrRes = await ToolsManager.executeTool('perform_ocr', { 'screenshot': ss['data'] });
          if (ocrRes['success'] == true && ocrRes['data'] is Map) {
            final data = Map<String, dynamic>.from(ocrRes['data']);
            _lastContext ??= <String, dynamic>{};
            _lastContext!['ocr_text'] = data['text']?.toString() ?? '';
            _lastContext!['ocr_blocks'] = (data['blocks'] is List) ? List.from(data['blocks']) : <dynamic>[];
            blocks = List.from(_lastContext!['ocr_blocks'] as List);
          }
        }
      }

      if (blocks.isEmpty) return;

      // Target common input/search cues
      final candidates = <String>['search', 'search for', 'type', 'enter', 'find', 'go', 'ok', 'submit'];
      Map<String, dynamic>? best;
      double bestScore = 0.0;
      for (final cue in candidates) {
        final b = _findBestOcrBlock(blocks, cue);
        if (b != null) {
          // Prefer cues close to top areas where search bars usually are
          final bb = _getBoundsFromOcrBlock(b);
          final score = (cue == 'search' || cue == 'search for') ? 1.0 : 0.7;
          final yBonus = (bb['top'] as double) < 400 ? 0.2 : 0.0; // heuristic
          final total = score + yBonus;
          if (total > bestScore) {
            bestScore = total;
            best = b;
          }
        }
      }

      if (best != null) {
        final center = _getCenterFromBounds(_getBoundsFromOcrBlock(best));
        final fieldId = 'ocr_${center['x']!.round()}_${center['y']!.round()}';
        
        // Check if this OCR field was already processed
        if (!_processedFields.contains(fieldId)) {
          _processedFields.add(fieldId);
          _lastTappedField = {'type': 'ocr', 'x': center['x'], 'y': center['y']};
          _notifyMessage('🖱️ Focusing input via OCR at (${center['x']!.round()}, ${center['y']!.round()})');
          await ToolsManager.executeTool('perform_tap', { 'x': center['x'], 'y': center['y'] });
          await Future.delayed(const Duration(milliseconds: 600));
        } else {
          _notifyMessage('⏭️ Skipping already processed OCR field at (${center['x']!.round()}, ${center['y']!.round()})');
        }
      }
    } catch (_) {}
  }

  int _findFirstEditableIndex(List a11y) {
    for (int i = 0; i < a11y.length; i++) {
      try {
        final node = a11y[i];
        if (node is Map) {
          final m = Map<String, dynamic>.from(node);
          if (m['editable'] == true || (m['className']?.toString().toLowerCase().contains('edittext') ?? false)) {
            return i;
          }
        }
      } catch (_) {}
    }
    return -1;
  }

  int _findBestUnprocessedEditableIndex(List a11y) {
    for (int i = 0; i < a11y.length; i++) {
      try {
        final node = a11y[i];
        if (node is Map) {
          final m = Map<String, dynamic>.from(node);
          if (m['editable'] == true || (m['className']?.toString().toLowerCase().contains('edittext') ?? false)) {
            final fieldId = 'a11y_$i';
            // Return first unprocessed editable field
            if (!_processedFields.contains(fieldId)) {
              return i;
            }
          }
        }
      } catch (_) {}
    }
    return -1;
  }

  // === Helpers to reuse latest captured context and avoid duplicate tool calls ===
  List<dynamic> _getElementsFromLastContext() {
    final ctx = _lastContext;
    if (ctx == null) return <dynamic>[];
    final a11y = ctx['accessibility_tree'];
    if (a11y is List && a11y.isNotEmpty) {
      return List.from(a11y);
    }
    final screenElements = ctx['screen_elements'];
    if (screenElements is List) return List.from(screenElements);
    return <dynamic>[];
  }

  /// Reset field tracking when screen changes or new form is detected
  void _resetFieldTracking() {
    _processedFields.clear();
    _lastTappedField?.clear();
    _notifyMessage('🔄 Field tracking reset for new screen/form');
  }

  /// Check if screen has changed significantly (new form detected)
  bool _hasScreenChanged(Map<String, dynamic> newContext) {
    if (_lastContext == null) return true;
    
    final oldActivity = _lastContext!['current_activity']?.toString() ?? '';
    final newActivity = newContext['current_activity']?.toString() ?? '';
    
    // Reset tracking if activity changed
    if (oldActivity != newActivity && newActivity.isNotEmpty) {
      return true;
    }
    
    return false;
  }

  /// Detect system dialogs in accessibility tree
  List<Map<String, dynamic>> _detectSystemDialogs(List<dynamic> accessibilityTree, Map<String, dynamic> currentApp) {
    final systemDialogs = <Map<String, dynamic>>[];
    
    try {
      final currentPackage = currentApp['packageName']?.toString() ?? '';
      
      for (int i = 0; i < accessibilityTree.length; i++) {
        final element = accessibilityTree[i];
        if (element is! Map) continue;
        
        final elementMap = Map<String, dynamic>.from(element);
        final className = elementMap['className']?.toString() ?? '';
        final packageName = elementMap['packageName']?.toString() ?? '';
        final text = elementMap['text']?.toString() ?? '';
        final contentDesc = elementMap['contentDescription']?.toString() ?? '';
        
        // Detect system dialogs by various indicators
        bool isSystemDialog = false;
        String dialogType = 'unknown';
        
        // Check for system UI package
        if (packageName == 'com.android.systemui') {
          isSystemDialog = true;
          dialogType = 'system_ui';
        }
        // Check for dialog class names
        else if (className.contains('Dialog') || className.contains('AlertDialog')) {
          isSystemDialog = true;
          dialogType = 'alert_dialog';
        }
        // Check for permission dialogs
        else if (text.toLowerCase().contains('permission') || 
                 contentDesc.toLowerCase().contains('permission') ||
                 text.toLowerCase().contains('allow') ||
                 text.toLowerCase().contains('deny')) {
          isSystemDialog = true;
          dialogType = 'permission_dialog';
        }
        // Check for system settings dialogs
        else if (packageName == 'com.android.settings' && 
                 (className.contains('Dialog') || text.toLowerCase().contains('settings'))) {
          isSystemDialog = true;
          dialogType = 'settings_dialog';
        }
        // Check for notification dialogs
        else if (text.toLowerCase().contains('notification') ||
                 contentDesc.toLowerCase().contains('notification')) {
          isSystemDialog = true;
          dialogType = 'notification_dialog';
        }
        
        if (isSystemDialog) {
          systemDialogs.add({
            'index': i,
            'type': dialogType,
            'className': className,
            'packageName': packageName,
            'text': text,
            'contentDescription': contentDesc,
            'bounds': elementMap['bounds'] ?? {},
            'clickable': elementMap['clickable'] ?? false,
          });
        }
      }
    } catch (e) {
      print('❌ Error detecting system dialogs: $e');
    }
    
    return systemDialogs;
  }
  
  /// Format system dialogs for AI prompt
  String _formatSystemDialogs(List<Map<String, dynamic>> systemDialogs) {
    if (systemDialogs.isEmpty) return '[No system dialogs detected]';
    
    final buffer = StringBuffer();
    buffer.writeln('DETECTED SYSTEM DIALOGS:');
    
    for (final dialog in systemDialogs) {
      final index = dialog['index'] ?? -1;
      final type = dialog['type'] ?? 'unknown';
      final text = dialog['text']?.toString() ?? '';
      final contentDesc = dialog['contentDescription']?.toString() ?? '';
      final packageName = dialog['packageName']?.toString() ?? '';
      final clickable = dialog['clickable'] == true ? 'CLICKABLE' : 'Not clickable';
      
      buffer.writeln('[$index] $type - Package: $packageName');
      if (text.isNotEmpty) buffer.writeln('    Text: "$text"');
      if (contentDesc.isNotEmpty) buffer.writeln('    Description: "$contentDesc"');
      buffer.writeln('    Interaction: $clickable');
      buffer.writeln();
    }
    
    return buffer.toString().trim();
  }
  
  /// Dynamic scroll logic with target condition checking and tree change detection
  Future<bool> performDynamicScroll({
    required String direction,
    required String targetText,
    int maxScrollAttempts = 5,
    int consecutiveIdenticalThreshold = 2,
    Duration scrollWaitDuration = const Duration(milliseconds: 1500),
  }) async {
    try {
      print('🔄 Starting dynamic scroll: direction=$direction, target="$targetText"');
      
      List<String> previousSnapshots = [];
      int consecutiveIdenticalCount = 0;
      bool targetFound = false;
      
      for (int attempt = 1; attempt <= maxScrollAttempts && !targetFound; attempt++) {
        print('📜 Scroll attempt $attempt/$maxScrollAttempts');
        
        // 1. Capture current accessibility tree and OCR snapshot
        final currentContext = await _captureScreenContext();
        final currentSnapshot = _createContextSnapshot(currentContext);
        
        // 2. Check if target condition is satisfied
        targetFound = _checkTargetCondition(currentContext, targetText);
        if (targetFound) {
          print('🎯 Target found before scrolling!');
          return true;
        }
        
        // 3. Check for consecutive identical snapshots
        if (previousSnapshots.isNotEmpty && 
            previousSnapshots.last == currentSnapshot) {
          consecutiveIdenticalCount++;
          print('⚠️ Identical snapshot detected ($consecutiveIdenticalCount/$consecutiveIdenticalThreshold)');
          
          if (consecutiveIdenticalCount >= consecutiveIdenticalThreshold) {
            print('🛑 Reached end of scrollable content in $direction direction');
            
            // Try reverse direction if target not found
            if (attempt < maxScrollAttempts) {
              final reverseDirection = _getReverseDirection(direction);
              print('🔄 Trying reverse direction: $reverseDirection');
              
              return await performDynamicScroll(
                direction: reverseDirection,
                targetText: targetText,
                maxScrollAttempts: maxScrollAttempts - attempt,
                consecutiveIdenticalThreshold: consecutiveIdenticalThreshold,
                scrollWaitDuration: scrollWaitDuration,
              );
            }
            break;
          }
        } else {
          consecutiveIdenticalCount = 0;
        }
        
        // 4. Perform scroll action
        final scrollResult = await ToolsManager.executeTool('perform_scroll', {
          'direction': direction,
        });
        
        if (scrollResult['success'] != true) {
          print('❌ Scroll failed: ${scrollResult['error']}');
          continue;
        }
        
        print('✅ Scroll $direction performed, waiting for UI update...');
        
        // 5. Wait for accessibility tree update or visual change
        await Future.delayed(scrollWaitDuration);
        
        // 6. Capture new accessibility tree and compare
        final newContext = await _captureScreenContext();
        final newSnapshot = _createContextSnapshot(newContext);
        
        // 7. Check target condition after scroll
        targetFound = _checkTargetCondition(newContext, targetText);
        if (targetFound) {
          print('🎯 Target found after scroll!');
          return true;
        }
        
        // Store snapshot for next iteration
        previousSnapshots.add(currentSnapshot);
        
        // Apply fuzzy matching between accessibility and OCR data if needed
        if (!targetFound && newContext['ocr_text'] != null) {
          targetFound = _fuzzyMatchTarget(newContext, targetText);
          if (targetFound) {
            print('🎯 Target found via fuzzy OCR matching!');
            return true;
          }
        }
      }
      
      print('❌ Target "$targetText" not found after $maxScrollAttempts scroll attempts');
      return false;
      
    } catch (e) {
      print('❌ Error in dynamic scroll: $e');
      return false;
    }
  }
  
  /// Create a snapshot of the current context for comparison
  String _createContextSnapshot(Map<String, dynamic> context) {
    try {
      final accessibilityTree = context['accessibility_tree'] as List? ?? [];
      final ocrText = context['ocr_text']?.toString() ?? '';
      
      // Create a hash-like representation of the current state
      final treeTexts = accessibilityTree
          .where((element) => element is Map)
          .map((element) {
            final elementMap = Map<String, dynamic>.from(element);
            final text = elementMap['text']?.toString() ?? '';
            final contentDesc = elementMap['contentDescription']?.toString() ?? '';
            return '$text|$contentDesc';
          })
          .where((text) => text.trim().isNotEmpty)
          .join('\n');
      
      return '$treeTexts\n---OCR---\n$ocrText';
    } catch (e) {
      print('❌ Error creating context snapshot: $e');
      return DateTime.now().millisecondsSinceEpoch.toString();
    }
  }
  
  /// Check if target condition is satisfied in current context
  bool _checkTargetCondition(Map<String, dynamic> context, String targetText) {
    try {
      final accessibilityTree = context['accessibility_tree'] as List? ?? [];
      final ocrText = context['ocr_text']?.toString() ?? '';
      
      // Check accessibility tree for target
      for (final element in accessibilityTree) {
        if (element is! Map) continue;
        
        final elementMap = Map<String, dynamic>.from(element);
        final text = elementMap['text']?.toString() ?? '';
        final contentDesc = elementMap['contentDescription']?.toString() ?? '';
        
        if (text.toLowerCase().contains(targetText.toLowerCase()) ||
            contentDesc.toLowerCase().contains(targetText.toLowerCase())) {
          return true;
        }
      }
      
      // Check OCR text for target
      if (ocrText.toLowerCase().contains(targetText.toLowerCase())) {
        return true;
      }
      
      return false;
    } catch (e) {
      print('❌ Error checking target condition: $e');
      return false;
    }
  }
  
  /// Apply fuzzy matching between accessibility and OCR data
  bool _fuzzyMatchTarget(Map<String, dynamic> context, String targetText) {
    try {
      final ocrText = context['ocr_text']?.toString() ?? '';
      final ocrBlocks = context['ocr_blocks'] as List? ?? [];
      
      // Simple fuzzy matching - check for partial matches
      final targetWords = targetText.toLowerCase().split(' ');
      final ocrWords = ocrText.toLowerCase().split(RegExp(r'\s+'));
      
      int matchedWords = 0;
      for (final targetWord in targetWords) {
        if (targetWord.length < 3) continue; // Skip very short words
        
        for (final ocrWord in ocrWords) {
          if (ocrWord.contains(targetWord) || targetWord.contains(ocrWord)) {
            matchedWords++;
            break;
          }
        }
      }
      
      // Consider it a match if at least 70% of target words are found
      final matchRatio = targetWords.isNotEmpty ? matchedWords / targetWords.length : 0.0;
      return matchRatio >= 0.7;
      
    } catch (e) {
      print('❌ Error in fuzzy matching: $e');
      return false;
    }
  }
  
  /// Get reverse direction for scrolling
  String _getReverseDirection(String direction) {
    switch (direction.toLowerCase()) {
      case 'up': return 'down';
      case 'down': return 'up';
      case 'left': return 'right';
      case 'right': return 'left';
      default: return 'up';
    }
  }

  bool _lastContextLooksLikeWeb() {
    try {
      final ctx = _lastContext;
      if (ctx == null) return false;
      final screenElements = ctx['screen_elements'];
      if (screenElements is! List) return false;
      final hints = _collectClassHints(List.from(screenElements));
      return hints.any((c) => c.contains('WebView') || c.contains('webview') || c.contains('ComposeView'));
    } catch (_) {
      return false;
    }
  }
}