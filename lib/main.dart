import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'screens/task_list_screen.dart';
import 'screens/task_chat_screen.dart';
import 'screens/permission_screen.dart';
import 'screens/porcupine_setup_screen.dart';
import 'api_settings_screen.dart';
import 'secure_storage.dart';
import 'tools/tools_manager.dart';
import 'services/automation_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize AutomationService to set up method channel handler
  try {
    final automationService = AutomationService();
    await automationService.initialize();
    print('✅ AutomationService initialized during app startup');
  } catch (e) {
    print('❌ Failed to initialize AutomationService during startup: $e');
  }
  
  runApp(const MyApp());
}

class HomeWrapper extends StatefulWidget {
  const HomeWrapper({super.key});

  @override
  State<HomeWrapper> createState() => _HomeWrapperState();
}

class _HomeWrapperState extends State<HomeWrapper> {
  bool _isLoading = true;
  bool _allPermissionsGranted = false;

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    try {
      // Check API configuration
      final secureStorage = SecureStorage();
      final apiConfigured = await secureStorage.hasApiConfiguration();
      
      // Check accessibility permission
      final accessibilityResult = await ToolsManager.executeTool(
        'check_accessibility_permission',
        {},
      );
      final hasAccessibility = accessibilityResult['success'] == true &&
          accessibilityResult['data'] == true;
      
      // Check overlay permission
      final overlayResult = await ToolsManager.executeTool(
        'check_overlay_permission',
        {},
      );
      final hasOverlay = overlayResult['success'] == true &&
          overlayResult['data'] == true;
      
      setState(() {
        _allPermissionsGranted = apiConfigured && hasAccessibility && hasOverlay;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _allPermissionsGranted = false;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }
    
    return _allPermissionsGranted 
        ? const TaskListScreen()
        : const PermissionScreen();
  }
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'heybro',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
        textTheme: const TextTheme(
          headlineLarge: TextStyle(
            fontSize: 32,
            fontWeight: FontWeight.bold,
            color: Colors.black87,
          ),
          headlineMedium: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w600,
            color: Colors.black87,
          ),
          headlineSmall: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w500,
            color: Colors.black87,
          ),
          titleLarge: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w600,
            color: Colors.black87,
          ),
          titleMedium: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w500,
            color: Colors.black87,
          ),
          titleSmall: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: Colors.black54,
          ),
          bodyLarge: TextStyle(
            fontSize: 16,
            color: Colors.black87,
          ),
          bodyMedium: TextStyle(
            fontSize: 14,
            color: Colors.black87,
          ),
          bodySmall: TextStyle(
            fontSize: 12,
            color: Colors.black54,
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
          ),
        ),
        cardTheme: CardThemeData(
           elevation: 2,
           shape: RoundedRectangleBorder(
             borderRadius: BorderRadius.circular(12),
           ),
         ),
        inputDecorationTheme: InputDecorationTheme(
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
          ),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        ),
      ),
      home: const HomeWrapper(),
      onGenerateRoute: (settings) {
        switch (settings.name) {
          case '/task-chat':
             final args = settings.arguments as Map<String, dynamic>?;
             return MaterialPageRoute(
               builder: (context) => TaskChatScreen(
                 task: args?['task'],
               ),
             );
          case '/api-settings':
            return MaterialPageRoute(
              builder: (context) => const ApiSettingsScreen(),
            );
          case '/porcupine-setup':
            return MaterialPageRoute(
              builder: (context) => const PorcupineSetupScreen(),
            );
          case '/':
            return MaterialPageRoute(
              builder: (context) => const TaskListScreen(),
            );
          default:
            return MaterialPageRoute(
              builder: (context) => const TaskListScreen(),
            );
        }
      },
    );
  }
}
