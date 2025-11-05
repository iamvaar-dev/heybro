import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/task.dart';

class TaskService {
  static const String _tasksKey = 'tasks';
  static TaskService? _instance;
  
  TaskService._internal();
  
  static TaskService get instance {
    _instance ??= TaskService._internal();
    return _instance!;
  }

  List<Task> _tasks = [];
  
  List<Task> get tasks => List.unmodifiable(_tasks);

  Future<void> loadTasks() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final tasksJson = prefs.getString(_tasksKey);
      
      if (tasksJson != null) {
        final List<dynamic> tasksList = json.decode(tasksJson);
        _tasks = tasksList.map((taskJson) => Task.fromJson(taskJson)).toList();
      }
    } catch (e) {
      print('Error loading tasks: $e');
      _tasks = [];
    }
  }

  Future<void> saveTasks() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final tasksJson = json.encode(_tasks.map((task) => task.toJson()).toList());
      await prefs.setString(_tasksKey, tasksJson);
    } catch (e) {
      print('Error saving tasks: $e');
    }
  }

  Future<Task> createTask(String title, String description) async {
    final task = Task(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      title: title,
      description: description,
      createdAt: DateTime.now(),
      messages: [],
    );
    
    _tasks.insert(0, task); // Add to beginning for newest first
    await saveTasks();
    return task;
  }

  Future<void> updateTask(Task updatedTask) async {
    final index = _tasks.indexWhere((task) => task.id == updatedTask.id);
    if (index != -1) {
      _tasks[index] = updatedTask;
      await saveTasks();
    }
  }

  Future<void> deleteTask(String taskId) async {
    _tasks.removeWhere((task) => task.id == taskId);
    await saveTasks();
  }

  Task? getTask(String taskId) {
    try {
      return _tasks.firstWhere((task) => task.id == taskId);
    } catch (e) {
      return null;
    }
  }

  Future<void> addMessageToTask(String taskId, ChatMessage message) async {
    final task = getTask(taskId);
    if (task != null) {
      final updatedMessages = List<ChatMessage>.from(task.messages);
      updatedMessages.add(message);
      
      final updatedTask = task.copyWith(messages: updatedMessages);
      await updateTask(updatedTask);
    }
  }

  Future<void> markAutomationStarted(String taskId) async {
    final task = getTask(taskId);
    if (task != null) {
      final updatedTask = task.copyWith(automationStarted: true);
      await updateTask(updatedTask);
    }
  }

  Future<void> markTaskCompleted(String taskId) async {
    final task = getTask(taskId);
    if (task != null) {
      final updatedTask = task.copyWith(status: TaskStatus.completed);
      await updateTask(updatedTask);
    }
  }

  List<Task> getTasksByStatus(TaskStatus status) {
    return _tasks.where((task) => task.status == status).toList();
  }

  List<Task> searchTasks(String query) {
    final lowercaseQuery = query.toLowerCase();
    return _tasks.where((task) {
      return task.title.toLowerCase().contains(lowercaseQuery) ||
             task.description.toLowerCase().contains(lowercaseQuery);
    }).toList();
  }
}