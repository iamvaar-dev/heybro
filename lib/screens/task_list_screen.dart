import 'package:flutter/material.dart';
import '../models/task.dart';
import '../services/task_service.dart';
import '../screens/task_chat_screen.dart';
import '../api_settings_screen.dart';
import '../services/automation_service.dart';
import '../services/foreground_automation_service.dart';
import 'dart:async';

class TaskListScreen extends StatefulWidget {
  const TaskListScreen({super.key});

  @override
  State<TaskListScreen> createState() => _TaskListScreenState();
}

class _TaskListScreenState extends State<TaskListScreen>
    with TickerProviderStateMixin {
  final TaskService _taskService = TaskService.instance;
  final AutomationService _automationService = AutomationService();
  final TextEditingController _taskController = TextEditingController();
  late AnimationController _fabAnimationController;
  late AnimationController _listAnimationController;
  late Animation<double> _fabAnimation;
  late Animation<double> _listAnimation;
  bool _isCreatingTask = false;
  bool _isLoading = true;
  bool _isAutomationRunning = false;
  Timer? _automationStatusTimer;

  @override
  void initState() {
    super.initState();
    _fabAnimationController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _listAnimationController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _fabAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _fabAnimationController, curve: Curves.easeInOut),
    );
    _listAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _listAnimationController, curve: Curves.easeOutCubic),
    );
    _loadTasks();
    _startAutomationStatusMonitoring();
  }

  void _startAutomationStatusMonitoring() {
    // Check automation status every 2 seconds
    _automationStatusTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      final isRegularAutomating = _automationService.isAutomating;
      final isForegroundAutomating = await ForegroundAutomationService.isAutomating();
      final isRunning = isRegularAutomating || isForegroundAutomating;
      
      if (_isAutomationRunning != isRunning && mounted) {
        setState(() {
          _isAutomationRunning = isRunning;
        });
      }
    });
  }

  @override
  void dispose() {
    _automationStatusTimer?.cancel();
    _fabAnimationController.dispose();
    _listAnimationController.dispose();
    _taskController.dispose();
    super.dispose();
  }

  Future<void> _loadTasks() async {
    await _taskService.loadTasks();
    if (mounted) {
      setState(() {
        _isLoading = false;
      });
      _fabAnimationController.forward();
      _listAnimationController.forward();
    }
  }

  void _showCreateTaskDialog() {
    _taskController.clear();
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create New Task'),
        content: TextField(
          controller: _taskController,
          decoration: const InputDecoration(
            labelText: 'What would you like me to automate?',
            hintText: 'e.g., Order food from Zomato, Book an Uber ride, Send a message...',
          ),
          maxLines: 3,
          autofocus: true,
          textCapitalization: TextCapitalization.sentences,
        ),
        actions: [
          _buildPillButton(
            text: 'Cancel',
            onPressed: () => Navigator.of(context).pop(),
            isPrimary: false,
          ),
          _buildPillButton(
            text: 'Create & Start',
            onPressed: () {
              if (_taskController.text.trim().isNotEmpty) {
                _createTask();
              }
            },
            isPrimary: true,
            icon: Icons.add_task,
          ),
        ],
      ),
    );
  }

  Future<void> _createTask() async {
    final taskDescription = _taskController.text.trim();
    
    if (taskDescription.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a task to automate')),
      );
      return;
    }

    setState(() {
      _isCreatingTask = true;
    });

    try {
      final task = await _taskService.createTask(
        taskDescription,
        taskDescription,
      );

      if (mounted) {
        Navigator.of(context).pop(); // Close dialog
        Navigator.pushNamed(
          context,
          '/task-chat',
          arguments: {'task': task},
        ).then((_) {
          if (mounted) {
            setState(() {});
          }
        }); // Refresh list when returning
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error creating task: $e')),
        );
      }
    } finally {
      setState(() {
        _isCreatingTask = false;
      });
    }
  }

  void _openTask(Task task) {
    Navigator.pushNamed(
      context,
      '/task-chat',
      arguments: {'task': task},
    ).then((_) {
      if (mounted) {
        setState(() {});
      }
    }); // Refresh list when returning
  }

  Widget _buildPillButton({
    required String text,
    required VoidCallback? onPressed,
    required bool isPrimary,
    bool isLoading = false,
    IconData? icon,
  }) {
    final isTablet = MediaQuery.of(context).size.width > 600;
    
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
        splashColor: isPrimary 
            ? Colors.white.withOpacity(0.2)
            : const Color(0xFF4CAF50).withOpacity(0.1),
        highlightColor: isPrimary 
            ? Colors.white.withOpacity(0.1)
            : const Color(0xFF4CAF50).withOpacity(0.05),
        child: Container(
          padding: EdgeInsets.symmetric(
            horizontal: isTablet ? 24 : 20,
            vertical: isTablet ? 16 : 14,
          ),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
            gradient: isPrimary
                ? const LinearGradient(
                    colors: [
                      Color(0xFF66BB6A),
                      Color(0xFF4CAF50),
                      Color(0xFF2E7D32),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: [0.0, 0.5, 1.0],
                  )
                : const LinearGradient(
                    colors: [
                      Color(0xFFFFFFFF),
                      Color(0xFFF8F9FA),
                      Color(0xFFE5E7EB),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: [0.0, 0.7, 1.0],
                  ),
            border: isPrimary 
                ? Border.all(
                    color: const Color(0xFF4CAF50).withOpacity(0.3),
                    width: 0.5,
                  )
                : Border.all(
                    color: const Color(0xFF4CAF50),
                    width: 1.5,
                  ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.15),
                offset: const Offset(0, 4),
                blurRadius: 8,
                spreadRadius: 0,
              ),
            ],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (isLoading)
                SizedBox(
                  width: isTablet ? 20 : 18,
                  height: isTablet ? 20 : 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation<Color>(
                      isPrimary ? Colors.white : const Color(0xFF4CAF50),
                    ),
                  ),
                )
              else if (icon != null)
                Icon(
                  icon,
                  color: isPrimary ? Colors.white : const Color(0xFF4CAF50),
                  size: isTablet ? 22 : 20,
                ),
              if ((isLoading || icon != null) && text.isNotEmpty)
                SizedBox(width: isTablet ? 12 : 10),
              if (text.isNotEmpty)
                Flexible(
                  child: Text(
                    text,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: isPrimary ? Colors.white : const Color(0xFF4CAF50),
                      fontWeight: FontWeight.w700,
                      fontSize: isTablet ? 15 : 14,
                      letterSpacing: 0.3,
                      shadows: isPrimary ? [
                        Shadow(
                          color: Colors.black.withOpacity(0.3),
                          offset: const Offset(0, 1),
                          blurRadius: 2,
                        ),
                      ] : null,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _deleteTask(Task task) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Task'),
        content: Text('Are you sure you want to delete "${task.title}"?'),
        actions: [
          _buildPillButton(
            text: 'Cancel',
            onPressed: () => Navigator.of(context).pop(false),
            isPrimary: false,
          ),
          _buildPillButton(
            text: 'Delete',
            onPressed: () => Navigator.of(context).pop(true),
            isPrimary: true,
            icon: Icons.delete,
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await _taskService.deleteTask(task.id);
      if (mounted) {
        setState(() {});
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final isTablet = screenWidth > 600;
    
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: Text(
          'heybro',
          style: TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: isTablet ? 32 : 28,
            letterSpacing: -0.8,
            color: const Color(0xFF1B5E20),
          ),
        ),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF1B5E20),
        surfaceTintColor: Colors.transparent,
        toolbarHeight: isTablet ? 80 : 64,
        actions: [
          if (_automationService.isAutomating)
            Container(
              margin: EdgeInsets.only(right: isTablet ? 12 : 8),
              decoration: BoxDecoration(
                color: Colors.red.withOpacity(0.1),
                borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
                border: Border.all(
                  color: Colors.red.withOpacity(0.3),
                  width: 1.5,
                ),
              ),
              child: Material(
                color: Colors.transparent,
                borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
                child: InkWell(
                  borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
                  splashColor: Colors.red.withOpacity(0.1),
                  highlightColor: Colors.red.withOpacity(0.05),
                  onTap: () {
                    _automationService.stopAutomation();
                    setState(() {});
                  },
                  child: Padding(
                    padding: EdgeInsets.all(isTablet ? 12 : 10),
                    child: Icon(
                      Icons.stop_circle_rounded,
                      color: Colors.red.shade700,
                      size: isTablet ? 24 : 20,
                    ),
                  ),
                ),
              ),
            ),
          Container(
            margin: EdgeInsets.only(right: isTablet ? 16 : 12),
            decoration: BoxDecoration(
              color: const Color(0xFF4CAF50).withOpacity(0.1),
              borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
              border: Border.all(
                color: const Color(0xFF4CAF50).withOpacity(0.3),
                width: 1.5,
              ),
            ),
            child: Material(
              color: Colors.transparent,
              borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
              child: PopupMenuButton<String>(
                onSelected: (value) {
                  if (value == 'api-settings') {
                    Navigator.pushNamed(context, '/api-settings');
                  } else if (value == 'porcupine-setup') {
                    Navigator.pushNamed(context, '/porcupine-setup');
                  }
                },
                itemBuilder: (context) => [
                  const PopupMenuItem(
                    value: 'api-settings',
                    child: Row(
                      children: [
                        Icon(Icons.api, color: Color(0xFF2E7D32), size: 20),
                        SizedBox(width: 12),
                        Text('API Settings', style: TextStyle(fontWeight: FontWeight.w500)),
                      ],
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'porcupine-setup',
                    child: Row(
                      children: [
                        Icon(Icons.mic, color: Color(0xFF2E7D32), size: 20),
                        SizedBox(width: 12),
                        Text('Voice Setup', style: TextStyle(fontWeight: FontWeight.w500)),
                      ],
                    ),
                  ),
                ],
                child: Padding(
                  padding: EdgeInsets.all(isTablet ? 12 : 10),
                  child: Icon(
                    Icons.settings_rounded,
                    color: const Color(0xFF2E7D32),
                    size: isTablet ? 24 : 20,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // Compact Automation Status Banner
          if (_isAutomationRunning)
            AnimatedContainer(
              duration: const Duration(milliseconds: 400),
              curve: Curves.easeInOut,
              width: double.infinity,
              margin: EdgeInsets.symmetric(
                horizontal: isTablet ? 16 : 12,
                vertical: isTablet ? 8 : 6,
              ),
              padding: EdgeInsets.symmetric(
                horizontal: isTablet ? 16 : 12,
                vertical: isTablet ? 12 : 10,
              ),
              decoration: BoxDecoration(
                color: const Color(0xFF4CAF50).withOpacity(0.1),
                borderRadius: BorderRadius.circular(isTablet ? 12 : 10),
                border: Border.all(
                  color: const Color(0xFF4CAF50).withOpacity(0.2),
                  width: 1,
                ),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF4CAF50).withOpacity(0.06),
                    offset: const Offset(0, 2),
                    blurRadius: 6,
                    spreadRadius: 0,
                  ),
                ],
              ),
              child: Row(
                children: [
                  SizedBox(
                    width: isTablet ? 16 : 14,
                    height: isTablet ? 16 : 14,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF2E7D32)),
                    ),
                  ),
                  SizedBox(width: isTablet ? 12 : 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '🤖 AI Agent Active',
                          style: TextStyle(
                            color: const Color(0xFF1B5E20),
                            fontWeight: FontWeight.w600,
                            fontSize: isTablet ? 14 : 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                  // Compact status indicator
                  Container(
                    width: isTablet ? 8 : 6,
                    height: isTablet ? 8 : 6,
                    decoration: BoxDecoration(
                      color: const Color(0xFF4CAF50),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ],
              ),
            ),
          
          // Enhanced Tasks List with improved layout
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: const Color(0xFFFAFAFA),
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(isTablet ? 24 : 20),
                  topRight: Radius.circular(isTablet ? 24 : 20),
                ),
              ),
              child: _isLoading
                  ? _buildLoadingState(isTablet)
                  : _taskService.tasks.isEmpty
                      ? FadeTransition(
                          opacity: _listAnimation,
                          child: _buildEmptyState(isTablet),
                        )
                      : FadeTransition(
                          opacity: _listAnimation,
                          child: ListView.builder(
                            padding: EdgeInsets.only(
                              left: isTablet ? 24 : 16,
                              right: isTablet ? 24 : 16,
                              top: isTablet ? 24 : 20,
                              bottom: isTablet ? 120 : 100, // Extra space for FAB
                            ),
                            itemCount: _taskService.tasks.length,
                            physics: const BouncingScrollPhysics(
                              parent: AlwaysScrollableScrollPhysics(),
                            ),
                            cacheExtent: 500.0,
                            addAutomaticKeepAlives: false,
                            addRepaintBoundaries: true,
                            addSemanticIndexes: false,
                            itemBuilder: (context, index) {
                              final task = _taskService.tasks[index];
                              return AnimatedBuilder(
                                animation: _listAnimation,
                                builder: (context, child) {
                                  return SlideTransition(
                                    position: Tween<Offset>(
                                      begin: Offset(0, 0.3 * (1 - _listAnimation.value)),
                                      end: Offset.zero,
                                    ).animate(CurvedAnimation(
                                      parent: _listAnimationController,
                                      curve: Interval(
                                        (index * 0.1).clamp(0.0, 1.0),
                                        ((index * 0.1) + 0.3).clamp(0.0, 1.0),
                                        curve: Curves.easeOutCubic,
                                      ),
                                    )),
                                    child: FadeTransition(
                                      opacity: Tween<double>(
                                        begin: 0.0,
                                        end: 1.0,
                                      ).animate(CurvedAnimation(
                                        parent: _listAnimationController,
                                        curve: Interval(
                                          (index * 0.1).clamp(0.0, 1.0),
                                          ((index * 0.1) + 0.3).clamp(0.0, 1.0),
                                          curve: Curves.easeOut,
                                        ),
                                      )),
                                      child: _buildTaskCard(task, isTablet),
                                    ),
                                  );
                                },
                              );
                            },
                          ),
                        ),
            ),
          ),
        ],
      ),
      floatingActionButton: ScaleTransition(
        scale: _fabAnimation,
        child: _build3DPillButton(
          onTap: _isCreatingTask ? null : _showCreateTaskDialog,
          isTablet: isTablet,
          isPurple: true,
          text: _isCreatingTask ? 'Creating...' : 'New Task',
          isLoading: _isCreatingTask,
        ),
      ),
    );
  }

  Widget _buildLoadingState(bool isTablet) {
    return Center(
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: isTablet ? 48 : 32,
          vertical: isTablet ? 32 : 24,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Enhanced loading indicator with background
            Container(
              padding: EdgeInsets.all(isTablet ? 32 : 24),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    const Color(0xFF4CAF50).withOpacity(0.08),
                    const Color(0xFF4CAF50).withOpacity(0.04),
                  ],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(isTablet ? 32 : 24),
                border: Border.all(
                  color: const Color(0xFF4CAF50).withOpacity(0.15),
                  width: 1.5,
                ),
              ),
              child: CircularProgressIndicator(
                valueColor: const AlwaysStoppedAnimation<Color>(
                  Color(0xFF4CAF50),
                ),
                strokeWidth: isTablet ? 4 : 3,
                strokeCap: StrokeCap.round,
              ),
            ),
            
            SizedBox(height: isTablet ? 32 : 24),
            
            // Enhanced loading text
            Text(
              'Loading Tasks...',
              style: TextStyle(
                fontSize: isTablet ? 20 : 18,
                color: const Color(0xFF2E7D32),
                fontWeight: FontWeight.w600,
                letterSpacing: -0.3,
              ),
            ),
            
            SizedBox(height: isTablet ? 12 : 8),
            
            Text(
              'Please wait while we fetch your tasks',
              style: TextStyle(
                fontSize: isTablet ? 16 : 14,
                color: const Color(0xFF424242),
                fontWeight: FontWeight.w400,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState(bool isTablet) {
    return Center(
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: isTablet ? 48 : 32,
          vertical: isTablet ? 32 : 24,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Enhanced icon with background
            Container(
              padding: EdgeInsets.all(isTablet ? 32 : 24),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    const Color(0xFF4CAF50).withOpacity(0.1),
                    const Color(0xFF4CAF50).withOpacity(0.05),
                  ],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(isTablet ? 32 : 24),
                border: Border.all(
                  color: const Color(0xFF4CAF50).withOpacity(0.2),
                  width: 2,
                ),
              ),
              child: Icon(
                Icons.task_alt_rounded,
                size: isTablet ? 80 : 64,
                color: const Color(0xFF4CAF50).withOpacity(0.7),
              ),
            ),
            
            SizedBox(height: isTablet ? 40 : 32),
            
            // Enhanced title
            Text(
              'No Tasks Yet',
              style: TextStyle(
                fontSize: isTablet ? 28 : 24,
                color: const Color(0xFF1B5E20),
                fontWeight: FontWeight.w700,
                letterSpacing: -0.5,
              ),
            ),
            
            SizedBox(height: isTablet ? 16 : 12),
            
            // Enhanced description
            Text(
              'Create your first task to get started\nwith AI automation and boost your productivity',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: isTablet ? 18 : 16,
                color: const Color(0xFF424242),
                height: 1.6,
                fontWeight: FontWeight.w400,
              ),
            ),
            
            SizedBox(height: isTablet ? 56 : 40),
            
            // Enhanced call-to-action button
            Container(
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF4CAF50), Color(0xFF2E7D32)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(isTablet ? 20 : 16),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF4CAF50).withOpacity(0.3),
                    blurRadius: isTablet ? 16 : 12,
                    offset: const Offset(0, 6),
                    spreadRadius: 0,
                  ),
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    blurRadius: isTablet ? 8 : 6,
                    offset: const Offset(0, 2),
                    spreadRadius: 0,
                  ),
                ],
              ),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: _showCreateTaskDialog,
                  borderRadius: BorderRadius.circular(isTablet ? 20 : 16),
                  child: Padding(
                    padding: EdgeInsets.symmetric(
                      horizontal: isTablet ? 40 : 32,
                      vertical: isTablet ? 20 : 16,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.add_rounded,
                          color: Colors.white,
                          size: isTablet ? 24 : 20,
                        ),
                        SizedBox(width: isTablet ? 12 : 10),
                        Text(
                          'Create New Task',
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w600,
                            fontSize: isTablet ? 18 : 16,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTaskCard(Task task, bool isTablet) {
    final lastMessage = task.messages.isNotEmpty ? task.messages.last : null;
    final messageCount = task.messages.length;
    
    return Container(
      margin: EdgeInsets.only(
        bottom: isTablet ? 16 : 12,
        left: isTablet ? 4 : 2,
        right: isTablet ? 4 : 2,
      ),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
        border: Border.all(
          color: const Color(0xFF4CAF50).withOpacity(0.15),
          width: 1,
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF4CAF50).withOpacity(0.06),
            blurRadius: isTablet ? 8 : 6,
            offset: const Offset(0, 2),
            spreadRadius: 0,
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => _openTask(task),
          borderRadius: BorderRadius.circular(isTablet ? 16 : 12),
          splashColor: const Color(0xFF4CAF50).withOpacity(0.08),
          highlightColor: const Color(0xFF4CAF50).withOpacity(0.04),
          hoverColor: const Color(0xFF4CAF50).withOpacity(0.02),
          child: Padding(
            padding: EdgeInsets.all(isTablet ? 16 : 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header with title and menu
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            task.title,
                            style: TextStyle(
                              fontWeight: FontWeight.w700,
                              fontSize: isTablet ? 20 : 18,
                              color: const Color(0xFF1B5E20),
                              letterSpacing: -0.5,
                              height: 1.2,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          if (task.description.isNotEmpty) ...[
                            SizedBox(height: isTablet ? 8 : 6),
                            Text(
                              task.description,
                              style: TextStyle(
                                color: const Color(0xFF424242),
                                fontSize: isTablet ? 16 : 15,
                                height: 1.5,
                                fontWeight: FontWeight.w400,
                              ),
                              maxLines: 3,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ],
                      ),
                    ),
                    SizedBox(width: isTablet ? 12 : 8),
                    Container(
                      decoration: BoxDecoration(
                        color: Colors.grey.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(isTablet ? 10 : 8),
                      ),
                      child: PopupMenuButton<String>(
                        onSelected: (value) {
                          if (value == 'delete') {
                            _deleteTask(task);
                          }
                        },
                        itemBuilder: (context) => [
                          const PopupMenuItem(
                            value: 'delete',
                            child: Row(
                              children: [
                                Icon(Icons.delete_outline, color: Colors.red, size: 20),
                                SizedBox(width: 12),
                                Text('Delete Task', style: TextStyle(fontWeight: FontWeight.w500)),
                              ],
                            ),
                          ),
                        ],
                        child: Padding(
                          padding: EdgeInsets.all(isTablet ? 8 : 6),
                          child: Icon(
                            Icons.more_horiz,
                            color: Colors.grey[600],
                            size: isTablet ? 18 : 16,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                
                SizedBox(height: isTablet ? 12 : 8),
                
                // Stats and metadata row
                Row(
                  children: [
                    // Message count with enhanced styling
                    Container(
                      padding: EdgeInsets.symmetric(
                        horizontal: isTablet ? 8 : 6,
                        vertical: isTablet ? 4 : 3,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFF4CAF50).withOpacity(0.1),
                        borderRadius: BorderRadius.circular(isTablet ? 12 : 10),
                        border: Border.all(
                          color: const Color(0xFF4CAF50).withOpacity(0.2),
                          width: 1,
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.chat_bubble_outline_rounded,
                            size: isTablet ? 14 : 12,
                            color: const Color(0xFF2E7D32),
                          ),
                          SizedBox(width: isTablet ? 4 : 3),
                          Text(
                            '$messageCount',
                            style: TextStyle(
                              color: const Color(0xFF2E7D32),
                              fontWeight: FontWeight.w600,
                              fontSize: isTablet ? 12 : 11,
                            ),
                          ),
                        ],
                      ),
                    ),
                    
                    const Spacer(),
                    
                    // Date with improved styling
                    Container(
                      padding: EdgeInsets.symmetric(
                        horizontal: isTablet ? 6 : 5,
                        vertical: isTablet ? 3 : 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.grey.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(isTablet ? 10 : 8),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.schedule_rounded,
                            size: isTablet ? 12 : 10,
                            color: Colors.grey[600],
                          ),
                          SizedBox(width: isTablet ? 3 : 2),
                          Text(
                            _formatDate(task.createdAt),
                            style: TextStyle(
                              color: Colors.grey[600],
                              fontSize: isTablet ? 11 : 10,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                
                // Last message preview with compact design
                if (lastMessage != null) ...[
                  SizedBox(height: isTablet ? 8 : 6),
                  Divider(color: const Color(0xFF4CAF50).withOpacity(0.2), height: 1),
                  SizedBox(height: isTablet ? 8 : 6),
                  Row(
                    children: [
                      Container(
                        padding: EdgeInsets.all(isTablet ? 4 : 3),
                        decoration: BoxDecoration(
                          color: lastMessage.isUser 
                              ? const Color(0xFF2E7D32).withOpacity(0.1)
                              : const Color(0xFF4CAF50).withOpacity(0.1),
                          borderRadius: BorderRadius.circular(isTablet ? 6 : 4),
                        ),
                        child: Icon(
                          lastMessage.isUser ? Icons.person_rounded : Icons.smart_toy_rounded,
                          size: isTablet ? 12 : 10,
                          color: lastMessage.isUser 
                              ? const Color(0xFF2E7D32)
                              : const Color(0xFF4CAF50),
                        ),
                      ),
                      SizedBox(width: isTablet ? 8 : 6),
                      Expanded(
                        child: Text(
                          lastMessage.text,
                          style: TextStyle(
                            color: const Color(0xFF424242),
                            fontSize: isTablet ? 13 : 12,
                            height: 1.3,
                            fontWeight: FontWeight.w400,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final difference = now.difference(date);
    
    if (difference.inDays == 0) {
      if (difference.inHours == 0) {
        return '${difference.inMinutes}m ago';
      }
      return '${difference.inHours}h ago';
    } else if (difference.inDays == 1) {
      return 'Yesterday';
    } else if (difference.inDays < 7) {
      return '${difference.inDays}d ago';
    } else {
      return '${date.day}/${date.month}/${date.year}';
    }
  }

  Widget _build3DPillButton({
    required VoidCallback? onTap,
    required bool isTablet,
    required bool isPurple,
    required String text,
    required bool isLoading,
  }) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
        boxShadow: [
          // Single subtle shadow for 3D effect without glow
          BoxShadow(
            color: Colors.black.withOpacity(0.15),
            offset: const Offset(0, 4),
            blurRadius: 8,
            spreadRadius: 0,
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
          splashColor: isPurple 
              ? Colors.white.withOpacity(0.2)
              : const Color(0xFF4CAF50).withOpacity(0.1),
          highlightColor: isPurple 
              ? Colors.white.withOpacity(0.1)
              : const Color(0xFF4CAF50).withOpacity(0.05),
          child: Container(
            padding: EdgeInsets.symmetric(
              horizontal: isTablet ? 32 : 28,
              vertical: isTablet ? 18 : 16,
            ),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
              gradient: isPurple
                  ? const LinearGradient(
                      colors: [
                        Color(0xFF66BB6A), // Lighter green at top for 3D effect
                        Color(0xFF4CAF50), // Medium green
                        Color(0xFF2E7D32), // Darker green at bottom
                      ],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      stops: [0.0, 0.5, 1.0],
                    )
                  : const LinearGradient(
                      colors: [
                        Color(0xFFFFFFFF), // Pure white at top
                        Color(0xFFF8F9FA), // Light gray
                        Color(0xFFE5E7EB), // Slightly darker at bottom for depth
                      ],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      stops: [0.0, 0.7, 1.0],
                    ),
              border: isPurple 
                  ? Border.all(
                      color: const Color(0xFF4CAF50).withOpacity(0.3),
                      width: 0.5,
                    )
                  : Border.all(
                      color: const Color(0xFFD1D5DB),
                      width: 1,
                    ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (isLoading)
                  SizedBox(
                    width: isTablet ? 20 : 18,
                    height: isTablet ? 20 : 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        isPurple ? Colors.white : const Color(0xFF6B46C1),
                      ),
                    ),
                  )
                else
                  Icon(
                    isPurple ? Icons.add_rounded : Icons.login_rounded,
                    color: isPurple ? Colors.white : const Color(0xFF374151),
                    size: isTablet ? 22 : 20,
                  ),
                SizedBox(width: isTablet ? 12 : 10),
                Text(
                  text,
                  style: TextStyle(
                    color: isPurple ? Colors.white : const Color(0xFF374151),
                    fontWeight: FontWeight.w700,
                    fontSize: isTablet ? 16 : 15,
                    letterSpacing: 0.3,
                    shadows: isPurple ? [
                      Shadow(
                        color: Colors.black.withOpacity(0.3),
                        offset: const Offset(0, 1),
                        blurRadius: 2,
                      ),
                    ] : null,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}