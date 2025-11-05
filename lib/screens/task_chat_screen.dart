import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import '../models/task.dart';
import '../services/task_service.dart';
import '../services/automation_service.dart';
import '../services/foreground_automation_service.dart';

class TaskChatScreen extends StatefulWidget {
  final Task task;

  const TaskChatScreen({super.key, required this.task});

  @override
  State<TaskChatScreen> createState() => _TaskChatScreenState();
}

class _TaskChatScreenState extends State<TaskChatScreen>
    with TickerProviderStateMixin {
  final TaskService _taskService = TaskService.instance;
  final AutomationService _automationService = AutomationService();
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  
  late AnimationController _messageSlideController;
  late Animation<Offset> _messageSlideAnimation;
  
  bool _isProcessing = false;
  bool _useForegroundService = false;
  bool _isForegroundAutomating = false;
  Task? _currentTask;

  @override
  void initState() {
    super.initState();
    _currentTask = widget.task;
    
    _messageSlideController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _messageSlideAnimation = Tween<Offset>(
      begin: const Offset(0, 1),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _messageSlideController,
      curve: Curves.easeOutBack,
    ));
    
    // Start monitoring foreground automation status
    _startForegroundAutomationMonitoring();
    
    // Automatically start the task when screen loads (only if not started before)
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_currentTask!.automationStarted) {
        _startAutomationTask();
      }
    });
  }

  Timer? _foregroundMonitorTimer;

  void _startForegroundAutomationMonitoring() {
    // Check foreground automation status every 2 seconds
    _foregroundMonitorTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      if (_isForegroundAutomating) {
        final isStillRunning = await ForegroundAutomationService.isAutomating();
        if (!isStillRunning && mounted) {
          setState(() {
            _isForegroundAutomating = false;
          });
        }
      }
    });
  }

  @override
  void dispose() {
    // Stop monitoring timer
    _foregroundMonitorTimer?.cancel();
    
    // Stop any running automation
    if (_automationService.isAutomating) {
      _automationService.stopAutomation();
    }
    
    // Note: We don't stop foreground automation here as it should continue
    // running even when the app is closed. Users can manually stop it if needed.
    
    // Clear automation callbacks
    _automationService.onMessage = null;
    _automationService.onError = null;
    _automationService.onComplete = null;
    _automationService.onAutomationStateChanged = null;
    
    _messageSlideController.dispose();
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _startAutomationTask() async {
    if (_isProcessing || _currentTask!.automationStarted) return;
    
    setState(() {
      _isProcessing = true;
    });

    try {
      // Mark automation as started
      await TaskService.instance.markAutomationStarted(_currentTask!.id);
      
      // Reload the task to get updated state
      _currentTask = TaskService.instance.getTask(_currentTask!.id);
      
      // Add initial user message with the task description
      final userMessage = ChatMessage(
        text: _currentTask!.description,
        isUser: true,
      );
      
      await _taskService.addMessageToTask(_currentTask!.id, userMessage);
      _currentTask = _taskService.getTask(_currentTask!.id);
      
      setState(() {});
      _messageSlideController.forward().then((_) {
        _messageSlideController.reset();
      });
      _scrollToBottom();

      // Initialize AutomationService if not already done
      if (!_automationService.isInitialized) {
        final initMessage = ChatMessage(
          text: "🔧 Initializing automation system...",
          isUser: false,
        );
        await _taskService.addMessageToTask(_currentTask!.id, initMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        setState(() {});
        _scrollToBottom();
        
        await _automationService.initialize();
      }
      
      final aiMessage = ChatMessage(
        text: "🤖 Starting automation for: ${_currentTask!.description}",
        isUser: false,
      );
      
      await _taskService.addMessageToTask(_currentTask!.id, aiMessage);
      _currentTask = _taskService.getTask(_currentTask!.id);
      
      setState(() {});
      _messageSlideController.forward().then((_) {
        _messageSlideController.reset();
      });
      _scrollToBottom();
      
      // Start the real automation process
      await _performAutomation();
      
    } catch (e) {
      final errorMessage = ChatMessage(
        text: "Sorry, I encountered an error starting the automation: $e",
        isUser: false,
      );
      
      await _taskService.addMessageToTask(_currentTask!.id, errorMessage);
      _currentTask = _taskService.getTask(_currentTask!.id);
      setState(() {});
    } finally {
      setState(() {
        _isProcessing = false;
      });
    }
  }

  Future<void> _performAutomation() async {
    try {
      // Set up automation service callbacks to update the chat
      _automationService.onMessage = (message) async {
        // Only show tool calls and technical output, filter out conversational text
        String? jsonData;
        String? displayText;
        
        try {
           // Try to parse as JSON to detect tool calls
           final decoded = json.decode(message);
           if (decoded is Map<String, dynamic>) {
             // Check for task completion
             if (decoded.containsKey('task_completed') && decoded['task_completed'] == true) {
               await _taskService.markTaskCompleted(_currentTask!.id);
               _currentTask = _taskService.getTask(_currentTask!.id);
             }
             
             if (decoded.containsKey('tool_calls') || decoded.containsKey('function_call') || decoded.containsKey('action')) {
               // For tap_element_by_index actions, add saved coordinates from automation service
               if (decoded['action'] == 'tap_element_by_index' && _automationService.lastTapCoordinates != null) {
                 decoded['saved_coordinates'] = _automationService.lastTapCoordinates;
                 jsonData = json.encode(decoded);
               } else {
                 jsonData = message;
               }

               displayText = "🔧 ${_formatToolCall(decoded)}";
             }
           }
         } catch (e) {
           // Check if it's a technical message worth showing
           if (_isTechnicalMessage(message)) {
             displayText = message;
           }
           // Skip conversational messages
         }
        
        // Only add message if it's technical content
        if (displayText == null) return;
        
        final chatMessage = ChatMessage(
          text: displayText,
          isUser: false,
          jsonData: jsonData,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, chatMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        if (mounted) {
          setState(() {});
          _messageSlideController.forward().then((_) {
            _messageSlideController.reset();
          });
          _scrollToBottom();
        }
      };
      
      _automationService.onError = (error) async {
        final errorMessage = ChatMessage(
          text: "❌ Error: $error",
          isUser: false,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, errorMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        if (mounted) {
          setState(() {});
          _scrollToBottom();
        }
      };
      
      _automationService.onComplete = () async {
        // Mark task as completed
        await _taskService.markTaskCompleted(_currentTask!.id);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        final completeMessage = ChatMessage(
          text: "✅ Automation completed successfully!",
          isUser: false,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, completeMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        if (mounted) {
          setState(() {});
          _messageSlideController.forward().then((_) {
            _messageSlideController.reset();
          });
          _scrollToBottom();
        }
      };
      
      // Listen to automation state changes to update UI immediately
      _automationService.onAutomationStateChanged = (isAutomating) {
        if (mounted) {
          setState(() {
            // UI will automatically reflect the new automation state
            // This ensures stop button visibility is always accurate
          });
        }
      };
      
      // Start the automation - either foreground service or regular automation
      if (_useForegroundService) {
        final success = await ForegroundAutomationService.startForegroundAutomation(_currentTask!.description);
        if (success) {
          setState(() {
            _isForegroundAutomating = true;
          });
          final foregroundMessage = ChatMessage(
            text: "🚀 Task started in background service - it will continue even if you close the app!",
            isUser: false,
          );
          await _taskService.addMessageToTask(_currentTask!.id, foregroundMessage);
          _currentTask = _taskService.getTask(_currentTask!.id);
          if (mounted) {
            setState(() {});
            _scrollToBottom();
          }
        } else {
          throw Exception("Failed to start foreground automation service");
        }
      } else {
        await _automationService.startAutomation(_currentTask!.description);
        // Update UI to show stop button after automation starts
        if (mounted) {
          setState(() {});
        }
      }
      
    } catch (e) {
      final errorMessage = ChatMessage(
        text: "❌ Automation failed: $e",
        isUser: false,
      );
      
      await _taskService.addMessageToTask(_currentTask!.id, errorMessage);
      _currentTask = _taskService.getTask(_currentTask!.id);
      
      if (mounted) {
        setState(() {});
        _scrollToBottom();
      }
    }
  }

  Future<void> _sendMessage() async {
    final text = _messageController.text.trim();
    if (text.isEmpty || _isProcessing) return;

    final userMessage = ChatMessage(
      text: text,
      isUser: true,
    );

    setState(() {
      _isProcessing = true;
    });

    _messageController.clear();
    
    // Add user message
    await _taskService.addMessageToTask(_currentTask!.id, userMessage);
    _currentTask = _taskService.getTask(_currentTask!.id);
    
    setState(() {});
    _messageSlideController.forward().then((_) {
      _messageSlideController.reset();
    });
    _scrollToBottom();

    try {
      // If automation is already running, stop it first
      if (_automationService.isAutomating) {
        _automationService.stopAutomation();
        
        final stopMessage = ChatMessage(
          text: "🛑 Stopping current automation to handle your new request...",
          isUser: false,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, stopMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        setState(() {});
        _scrollToBottom();
        
        await Future.delayed(const Duration(seconds: 1));
      }
      
      // Initialize AutomationService if not already done
      if (!_automationService.isInitialized) {
        await _automationService.initialize();
      }
      
      final aiMessage = ChatMessage(
        text: "🤖 Processing: $text",
        isUser: false,
      );
      
      await _taskService.addMessageToTask(_currentTask!.id, aiMessage);
      _currentTask = _taskService.getTask(_currentTask!.id);
      
      setState(() {});
      _messageSlideController.forward().then((_) {
        _messageSlideController.reset();
      });
      _scrollToBottom();
      
      // Set up automation callbacks and start automation with the new message
      _automationService.onMessage = (message) async {
        // Only show tool calls and technical output, filter out conversational text
        String? jsonData;
        String? displayText;
        
        try {
           // Try to parse as JSON to detect tool calls
           final decoded = json.decode(message);
           if (decoded is Map<String, dynamic> && (decoded.containsKey('tool_calls') || decoded.containsKey('function_call') || decoded.containsKey('action'))) {
             jsonData = message;
             displayText = "🔧 ${_formatToolCall(decoded)}";
           }
         } catch (e) {
           // Check if it's a technical message worth showing
           if (_isTechnicalMessage(message)) {
             displayText = message;
           }
           // Skip conversational messages
         }
        
        // Only add message if it's technical content
         if (displayText == null) return;
         final chatMessage = ChatMessage(
           text: displayText,
          isUser: false,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, chatMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        if (mounted) {
          setState(() {});
          _messageSlideController.forward().then((_) {
            _messageSlideController.reset();
          });
          _scrollToBottom();
        }
      };
      
      _automationService.onError = (error) async {
        final errorMessage = ChatMessage(
          text: "❌ Error: $error",
          isUser: false,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, errorMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        if (mounted) {
          setState(() {});
          _scrollToBottom();
        }
      };
      
      _automationService.onComplete = () async {
        final completeMessage = ChatMessage(
          text: "✅ Task completed successfully!\n\nYou can send another message if you need any modifications or have additional tasks.",
          isUser: false,
        );
        
        await _taskService.addMessageToTask(_currentTask!.id, completeMessage);
        _currentTask = _taskService.getTask(_currentTask!.id);
        
        if (mounted) {
          setState(() {});
          _messageSlideController.forward().then((_) {
            _messageSlideController.reset();
          });
          _scrollToBottom();
        }
      };
      
      // Listen to automation state changes to update UI immediately
      _automationService.onAutomationStateChanged = (isAutomating) {
        if (mounted) {
          setState(() {
            // UI will automatically reflect the new automation state
            // This ensures stop button visibility is always accurate
          });
        }
      };
      
      // Start automation with the user's message
      await _automationService.startAutomation(text);
      
      // Update UI to show stop button after automation starts
      if (mounted) {
        setState(() {});
      }
      
    } catch (e) {
      final errorMessage = ChatMessage(
        text: "Sorry, I encountered an error: $e",
        isUser: false,
        isSystem: true,
      );
      
      await _taskService.addMessageToTask(_currentTask!.id, errorMessage);
      _currentTask = _taskService.getTask(_currentTask!.id);
      setState(() {});
    } finally {
      setState(() {
        _isProcessing = false;
      });
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  String _formatJsonForDisplay(Map<String, dynamic> jsonData) {
    try {
      // Pretty print JSON with indentation
      const encoder = JsonEncoder.withIndent('  ');
      return encoder.convert(jsonData);
    } catch (e) {
      return jsonData.toString();
    }
  }

  String _formatToolCall(Map<String, dynamic> toolData) {
    try {
      if (toolData.containsKey('tool_calls')) {
        final toolCalls = toolData['tool_calls'] as List;
        if (toolCalls.isNotEmpty) {
          final tool = toolCalls.first;
          final name = tool['function']?['name'] ?? 'Unknown Tool';
          return 'Tool: $name';
        }
      }
      if (toolData.containsKey('function_call')) {
        final name = toolData['function_call']?['name'] ?? 'Unknown Function';
        return 'Function: $name';
      }
      if (toolData.containsKey('action')) {
        final action = toolData['action'];
        return 'Action: $action';
      }
      return 'Tool Call: ${_formatJsonForDisplay(toolData)}';
    } catch (e) {
      return 'Tool Call: ${toolData.toString()}';
    }
  }

  bool _isTechnicalMessage(String message) {
    // Filter for technical messages only
    final technicalKeywords = [
      '🔧', '⚙️', '🛠️', '📱', '🖱️', '⌨️', '📋', '🔍', '✅', '❌', '⚠️',
      'screenshot', 'click', 'tap', 'swipe', 'scroll', 'type', 'input',
      'element', 'button', 'field', 'found', 'located', 'executed',
      'automation', 'tool', 'function', 'action', 'result', 'error',
      'success', 'failed', 'completed', 'processing'
    ];
    
    final lowerMessage = message.toLowerCase();
    return technicalKeywords.any((keyword) => lowerMessage.contains(keyword.toLowerCase()));
  }

  Future<void> _replayTask() async {
    if (_isProcessing || _currentTask?.status != TaskStatus.completed) return;
    
    setState(() {
      _isProcessing = true;
    });

    try {
      // Initialize AutomationService if not already done
      if (!_automationService.isInitialized) {
        await _automationService.initialize();
      }

      // Extract actions from original messages only (exclude replay messages)
      final actionsToReplay = <Map<String, dynamic>>[];
      for (final message in _currentTask!.messages) {
        // Skip replay messages to avoid duplication
        if (message.text.contains('⚡ Replaying') || 
            message.text.contains('🔧 Replaying') ||
            message.text.contains('✅ Task replay completed') ||
            message.text.contains('❌ Replay failed')) {
          continue;
        }
        
        if (message.jsonData != null) {
          try {
            final decoded = json.decode(message.jsonData!);
            if (decoded is Map<String, dynamic> && decoded.containsKey('action')) {
              actionsToReplay.add(decoded);
            }
          } catch (e) {
            // Skip invalid JSON
          }
        }
      }

      // Show a temporary overlay or status instead of adding messages
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('⚡ Replaying ${actionsToReplay.length} actions...'),
          duration: Duration(seconds: 2),
        ),
      );

      // Replay each action without adding messages to chat
      for (int i = 0; i < actionsToReplay.length; i++) {
        final actionData = actionsToReplay[i];
        
        // Show progress in snackbar
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('🔧 Executing action ${i + 1}/${actionsToReplay.length}'),
            duration: Duration(milliseconds: 800),
          ),
        );

        // Execute the action directly without LLM
        await _automationService.executeActionDirectly(actionData);
        
        // Small delay between actions
        await Future.delayed(const Duration(milliseconds: 500));
      }

      // Show completion status
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('✅ Task replay completed successfully!'),
          backgroundColor: Colors.green,
          duration: Duration(seconds: 3),
        ),
      );
      
    } catch (e) {
      // Show error status in snackbar instead of adding to chat
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('❌ Replay failed: $e'),
          backgroundColor: Colors.red,
          duration: Duration(seconds: 5),
        ),
      );
    } finally {
      setState(() {
        _isProcessing = false;
      });
    }
  }



  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final screenWidth = MediaQuery.of(context).size.width;
    final isTablet = screenWidth > 600;
    
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              _currentTask?.title ?? 'Task Chat',
              style: TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: isTablet ? 24 : 20,
                letterSpacing: -0.5,
                color: const Color(0xFF1B5E20),
              ),
            ),
            if (_currentTask?.description.isNotEmpty == true)
              Text(
                _currentTask!.description,
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.onSurface.withOpacity(0.7),
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
          ],
        ),
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF1B5E20),
        surfaceTintColor: Colors.transparent,
        leading: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => Navigator.of(context).pop(),
            borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
            splashColor: const Color(0xFF4CAF50).withOpacity(0.1),
            highlightColor: const Color(0xFF4CAF50).withOpacity(0.05),
            child: Container(
              width: isTablet ? 48 : 40,
              height: isTablet ? 48 : 40,
              alignment: Alignment.center,
              child: Icon(
                Icons.arrow_back_rounded,
                size: isTablet ? 24 : 20,
                color: const Color(0xFF1B5E20),
              ),
            ),
          ),
        ),
        actions: [
          // Foreground automation toggle
          if (!_automationService.isAutomating && !_isProcessing)
            Padding(
              padding: EdgeInsets.only(right: isTablet ? 8 : 6),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: () {
                    setState(() {
                      _useForegroundService = !_useForegroundService;
                    });
                  },
                  borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
                  splashColor: const Color(0xFF4CAF50).withOpacity(0.1),
                  highlightColor: const Color(0xFF4CAF50).withOpacity(0.05),
                  child: Container(
                    width: isTablet ? 48 : 40,
                    height: isTablet ? 48 : 40,
                    alignment: Alignment.center,
                    child: Icon(
                      _useForegroundService ? Icons.cloud_done_rounded : Icons.cloud_off_rounded,
                      size: isTablet ? 24 : 20,
                      color: _useForegroundService ? const Color(0xFF4CAF50) : Colors.grey,
                    ),
                  ),
                ),
              ),
            ),
          if (_currentTask?.status == TaskStatus.completed && !_isProcessing)
            Padding(
              padding: EdgeInsets.only(right: isTablet ? 8 : 6),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: _replayTask,
                  borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
                  splashColor: const Color(0xFF4CAF50).withOpacity(0.1),
                  highlightColor: const Color(0xFF4CAF50).withOpacity(0.05),
                  child: Container(
                    width: isTablet ? 48 : 40,
                    height: isTablet ? 48 : 40,
                    alignment: Alignment.center,
                    child: Image.asset(
                      'assets/strike.png',
                      width: isTablet ? 24 : 20,
                      height: isTablet ? 24 : 20,
                      color: const Color(0xFF4CAF50),
                    ),
                  ),
                ),
              ),
            ),
          if (_automationService.isAutomating || _isForegroundAutomating)
            Padding(
              padding: EdgeInsets.only(right: isTablet ? 16 : 12),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: () {
                    if (_isForegroundAutomating) {
                      ForegroundAutomationService.stopForegroundAutomation();
                      setState(() {
                        _isForegroundAutomating = false;
                      });
                    } else {
                      _automationService.stopAutomation();
                    }
                    setState(() {});
                  },
                  borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
                  splashColor: Colors.red.withOpacity(0.1),
                  highlightColor: Colors.red.withOpacity(0.05),
                  child: Container(
                    width: isTablet ? 48 : 40,
                    height: isTablet ? 48 : 40,
                    alignment: Alignment.center,
                    child: Icon(
                      Icons.stop_circle_rounded,
                      size: isTablet ? 24 : 20,
                      color: Colors.red.shade600,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),

      body: Column(
        children: [
          // Foreground Automation Mode Banner
          if (_useForegroundService && !_automationService.isAutomating)
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              width: double.infinity,
              padding: EdgeInsets.all(isTablet ? 16 : 12),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF2196F3), Color(0xFF1976D2)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: const BorderRadius.only(
                  bottomLeft: Radius.circular(16),
                  bottomRight: Radius.circular(16),
                ),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF2196F3).withOpacity(0.25),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                    spreadRadius: 0,
                  ),
                ],
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.cloud_done_rounded,
                    color: Colors.white,
                    size: isTablet ? 20 : 16,
                  ),
                  SizedBox(width: isTablet ? 16 : 12),
                  Text(
                    '🚀 Background Mode: Task will continue even if app is closed',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                      fontSize: isTablet ? 14 : 12,
                    ),
                  ),
                ],
              ),
            ),
          // Automation Status Banner
          if (_automationService.isAutomating || _isForegroundAutomating)
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              width: double.infinity,
              padding: EdgeInsets.all(isTablet ? 16 : 12),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF66BB6A), Color(0xFF4CAF50)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: const BorderRadius.only(
                  bottomLeft: Radius.circular(16),
                  bottomRight: Radius.circular(16),
                ),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF4CAF50).withOpacity(0.25),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                    spreadRadius: 0,
                  ),
                  BoxShadow(
                    color: const Color(0xFF4CAF50).withOpacity(0.1),
                    blurRadius: 6,
                    offset: const Offset(0, 2),
                    spreadRadius: 0,
                  ),
                ],
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox(
                    width: isTablet ? 20 : 16,
                    height: isTablet ? 20 : 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  ),
                  SizedBox(width: isTablet ? 16 : 12),
                  Text(
                    '🤖 AI Agent is automating this task...',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          
          // Messages List
          Expanded(
            child: _currentTask?.messages.isEmpty == true
                ? _buildEmptyState(theme, isTablet)
                : ListView.builder(
                    controller: _scrollController,
                    padding: EdgeInsets.symmetric(
                      horizontal: isTablet ? 24 : 16,
                      vertical: isTablet ? 16 : 8,
                    ),
                    itemCount: _currentTask?.messages.length ?? 0,
                    itemBuilder: (context, index) {
                      final message = _currentTask!.messages[index];
                      return SlideTransition(
                        position: index == _currentTask!.messages.length - 1 
                            ? _messageSlideAnimation 
                            : const AlwaysStoppedAnimation(Offset.zero),
                        child: FadeTransition(
                          opacity: index == _currentTask!.messages.length - 1 
                              ? _messageSlideController 
                              : const AlwaysStoppedAnimation(1.0),
                          child: _buildChatBubble(message, theme, isTablet),
                        ),
                      );
                    },
                  ),
          ),
          
          // Typing Indicator
          if (_isProcessing)
            Container(
              padding: EdgeInsets.symmetric(
                horizontal: isTablet ? 24 : 16,
                vertical: isTablet ? 12 : 8,
              ),
              child: Row(
                children: [
                  Container(
                    width: isTablet ? 48 : 40,
                    height: isTablet ? 48 : 40,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [Color(0xFF66BB6A), Color(0xFF4CAF50)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(isTablet ? 24 : 20),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(isTablet ? 24 : 20),
                      child: Image.asset(
                        'assets/logo.png',
                        width: isTablet ? 24 : 20,
                        height: isTablet ? 24 : 20,
                        fit: BoxFit.contain,
                      ),
                    ),
                  ),
                  SizedBox(width: isTablet ? 16 : 12),
                  Container(
                    padding: EdgeInsets.symmetric(
                      horizontal: isTablet ? 20 : 16,
                      vertical: isTablet ? 16 : 12,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(isTablet ? 24 : 20),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.05),
                          blurRadius: isTablet ? 6 : 4,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: isTablet ? 16 : 12,
                          height: isTablet ? 16 : 12,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor: const AlwaysStoppedAnimation<Color>(
                              Color(0xFF4CAF50),
                            ),
                          ),
                        ),
                        SizedBox(width: isTablet ? 12 : 8),
                        Text(
                          'AI is thinking...',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: Colors.black54,
                            fontSize: isTablet ? 16 : 14,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          
          // Message Input
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: EdgeInsets.symmetric(
              horizontal: isTablet ? 24 : 20,
              vertical: isTablet ? 20 : 16,
            ),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(24),
                topRight: Radius.circular(24),
              ),
              border: Border(
                top: BorderSide(
                  color: const Color(0xFF4CAF50).withOpacity(0.1),
                  width: 1,
                ),
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.08),
                  blurRadius: 12,
                  offset: const Offset(0, -4),
                  spreadRadius: 0,
                ),
                BoxShadow(
                  color: const Color(0xFF4CAF50).withOpacity(0.05),
                  blurRadius: 6,
                  offset: const Offset(0, -1),
                  spreadRadius: 0,
                ),
              ],
            ),
            child: Row(
              children: [
                Expanded(
                  child: Material(
                    color: Colors.transparent,
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(isTablet ? 36 : 32),
                        border: Border.all(
                          color: _messageController.text.isNotEmpty
                              ? const Color(0xFF4CAF50).withOpacity(0.3)
                              : const Color(0xFF4CAF50).withOpacity(0.2),
                          width: 1.5,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.04),
                            blurRadius: 12,
                            offset: const Offset(0, 3),
                            spreadRadius: 0,
                          ),
                          BoxShadow(
                            color: const Color(0xFF4CAF50).withOpacity(0.08),
                            blurRadius: 6,
                            offset: const Offset(0, 1),
                            spreadRadius: 0,
                          ),
                        ],
                      ),
                      child: TextField(
                        controller: _messageController,
                        decoration: InputDecoration(
                          hintText: 'Ask me anything...',
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(
                            horizontal: isTablet ? 24 : 20,
                            vertical: isTablet ? 18 : 14,
                          ),
                          hintStyle: TextStyle(
                            color: Colors.black54,
                            fontSize: isTablet ? 16 : 14,
                            fontWeight: FontWeight.w400,
                          ),
                        ),
                        style: TextStyle(
                          fontSize: isTablet ? 16 : 14,
                          fontWeight: FontWeight.w500,
                        ),
                        maxLines: 4,
                        minLines: 1,
                        textCapitalization: TextCapitalization.sentences,
                        onSubmitted: (_) => _sendMessage(),
                      ),
                    ),
                  ),
                ),
                SizedBox(width: isTablet ? 16 : 12),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  width: isTablet ? 56 : 48,
                  height: isTablet ? 56 : 48,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                    colors: _isProcessing
                        ? [Colors.grey.shade400, Colors.grey.shade500]
                        : [const Color(0xFF66BB6A), const Color(0xFF4CAF50)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                    borderRadius: BorderRadius.circular(isTablet ? 28 : 24),
                    boxShadow: [
                      BoxShadow(
                        color: (_isProcessing ? Colors.grey : const Color(0xFF4CAF50)).withOpacity(0.35),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                        spreadRadius: 0,
                      ),
                      BoxShadow(
                        color: (_isProcessing ? Colors.grey : const Color(0xFF4CAF50)).withOpacity(0.15),
                        blurRadius: 6,
                        offset: const Offset(0, 2),
                        spreadRadius: 0,
                      ),
                    ],
                  ),
                  child: Material(
                    color: Colors.transparent,
                    borderRadius: BorderRadius.circular(isTablet ? 28 : 24),
                    child: InkWell(
                      onTap: _isProcessing ? null : _sendMessage,
                      borderRadius: BorderRadius.circular(isTablet ? 28 : 24),
                      splashColor: Colors.white.withOpacity(0.2),
                      highlightColor: Colors.white.withOpacity(0.1),
                      child: Center(
                        child: _isProcessing
                            ? SizedBox(
                                width: isTablet ? 20 : 18,
                                height: isTablet ? 20 : 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
                                ),
                              )
                            : Icon(
                                Icons.send_rounded,
                                color: Colors.white,
                                size: isTablet ? 24 : 20,
                              ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),

    );
  }

  Widget _buildEmptyState(ThemeData theme, bool isTablet) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.chat_bubble_outline,
            size: isTablet ? 80 : 60,
            color: const Color(0xFF4CAF50).withOpacity(0.3),
          ),
          SizedBox(height: isTablet ? 24 : 16),
          Text(
            'Start the conversation',
            style: theme.textTheme.headlineSmall?.copyWith(
              color: Colors.black54,
              fontWeight: FontWeight.w600,
            ),
          ),
          SizedBox(height: isTablet ? 12 : 8),
          Text(
            'Send a message to begin automating\nthis task with AI assistance',
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: Colors.black45,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildChatBubble(ChatMessage message, ThemeData theme, bool isTablet) {
    final avatarSize = isTablet ? 48.0 : 40.0;
    final iconSize = isTablet ? 24.0 : 20.0;
    final padding = isTablet ? 20.0 : 16.0;
    final maxBubbleWidth = MediaQuery.of(context).size.width * 0.75;

    if (message.isSystem) {
      return AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        margin: EdgeInsets.symmetric(vertical: isTablet ? 8 : 6),
        padding: EdgeInsets.all(isTablet ? 16 : 12),
        decoration: BoxDecoration(
          color: const Color(0xFF4CAF50).withOpacity(0.1),
          borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
          border: Border.all(
            color: const Color(0xFF4CAF50).withOpacity(0.2),
            width: 1,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.info_outline_rounded,
              size: isTablet ? 20 : 16,
              color: const Color(0xFF4CAF50),
            ),
            SizedBox(width: isTablet ? 12 : 8),
            Expanded(
              child: Text(
                message.text,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: const Color(0xFF2E7D32),
                  fontStyle: FontStyle.italic,
                  fontSize: isTablet ? 14 : 12,
                  height: 1.3,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      margin: EdgeInsets.symmetric(vertical: isTablet ? 8 : 6),
      child: Row(
        mainAxisAlignment:
            message.isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!message.isUser) ...[
            Semantics(
              label: 'AI Assistant avatar',
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                width: avatarSize,
                height: avatarSize,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF66BB6A), Color(0xFF4CAF50)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(avatarSize / 2),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF4CAF50).withOpacity(0.3),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(avatarSize / 2),
                  child: Image.asset(
                    'assets/logo.png',
                    width: iconSize,
                    height: iconSize,
                    fit: BoxFit.contain,
                  ),
                ),
              ),
            ),
            SizedBox(width: isTablet ? 16 : 12),
          ],
          Flexible(
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              constraints: BoxConstraints(
                maxWidth: maxBubbleWidth,
              ),
              padding: EdgeInsets.symmetric(
                horizontal: padding, 
                vertical: isTablet ? 16 : 12,
              ),
              decoration: BoxDecoration(
                gradient: message.isUser
                    ? const LinearGradient(
                        colors: [Color(0xFF66BB6A), Color(0xFF4CAF50)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      )
                    : null,
                color: message.isUser
                    ? null
                    : Colors.white,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(isTablet ? 28 : 24),
                  topRight: Radius.circular(isTablet ? 28 : 24),
                  bottomLeft: Radius.circular(message.isUser ? (isTablet ? 28 : 24) : (isTablet ? 8 : 6)),
                  bottomRight: Radius.circular(message.isUser ? (isTablet ? 8 : 6) : (isTablet ? 28 : 24)),
                ),
                border: message.isUser
                    ? null
                    : Border.all(
                        color: const Color(0xFF4CAF50).withOpacity(0.1),
                        width: 1,
                      ),
                boxShadow: [
                  BoxShadow(
                    color: message.isUser
                        ? const Color(0xFF4CAF50).withOpacity(0.25)
                        : Colors.black.withOpacity(0.06),
                    blurRadius: isTablet ? 16 : 12,
                    offset: Offset(0, isTablet ? 6 : 4),
                    spreadRadius: 0,
                  ),
                  BoxShadow(
                    color: message.isUser
                        ? const Color(0xFF4CAF50).withOpacity(0.1)
                        : Colors.black.withOpacity(0.03),
                    blurRadius: isTablet ? 8 : 6,
                    offset: Offset(0, isTablet ? 2 : 1),
                    spreadRadius: 0,
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    message.text,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: message.isUser
                          ? Colors.white
                          : Colors.black87,
                      height: 1.4,
                      fontSize: isTablet ? 16 : 14,
                    ),
                  ),
                  if (message.jsonData != null) ...[
                    const SizedBox(height: 8),
                    ExpansionTile(
                      title: Text(
                        'Raw JSON Data',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: message.isUser
                              ? Colors.white70
                              : Colors.black54,
                          fontSize: isTablet ? 12 : 10,
                        ),
                      ),
                      iconColor: message.isUser
                          ? Colors.white70
                          : Colors.black54,
                      collapsedIconColor: message.isUser
                          ? Colors.white70
                          : Colors.black54,
                      children: [
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 300),
                          width: double.infinity,
                          padding: EdgeInsets.all(isTablet ? 12 : 8),
                          decoration: BoxDecoration(
                            color: message.isUser
                                ? Colors.white.withOpacity(0.15)
                                : const Color(0xFF4CAF50).withOpacity(0.05),
                            borderRadius: BorderRadius.circular(isTablet ? 12 : 8),
                            border: Border.all(
                              color: message.isUser
                                  ? Colors.white.withOpacity(0.2)
                                  : const Color(0xFF4CAF50).withOpacity(0.2),
                              width: 1,
                            ),
                          ),
                          child: SelectableText(
                            message.jsonData!,
                            style: theme.textTheme.bodySmall?.copyWith(
                              fontFamily: 'monospace',
                              color: message.isUser
                                  ? Colors.white70
                                  : Colors.black54,
                              fontSize: isTablet ? 12 : 10,
                              height: 1.3,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (message.isUser) ...[
            SizedBox(width: isTablet ? 16 : 12),
            Semantics(
              label: 'Your avatar',
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                width: avatarSize,
                height: avatarSize,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [theme.colorScheme.secondary, theme.colorScheme.secondary.withOpacity(0.8)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(avatarSize / 2),
                  boxShadow: [
                    BoxShadow(
                      color: theme.colorScheme.secondary.withOpacity(0.3),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Icon(
                  Icons.person_rounded,
                  color: Colors.white,
                  size: iconSize,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}