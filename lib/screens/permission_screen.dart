import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter_svg/flutter_svg.dart';
import '../secure_storage.dart';
import '../api_settings_screen.dart';
import 'task_list_screen.dart';

class PermissionScreen extends StatefulWidget {
  const PermissionScreen({super.key});

  @override
  State<PermissionScreen> createState() => _PermissionScreenState();
}

class _PermissionScreenState extends State<PermissionScreen>
    with TickerProviderStateMixin {
  bool _isLoading = true;
  bool _hasAccessibilityPermission = false;
  bool _hasOverlayPermission = false;
  bool _hasAudioPermission = false;
  bool _isApiConfigured = false;
  
  late AnimationController _animationController;
  late Animation<double> _fadeAnimation;
  
  final SecureStorage _secureStorage = SecureStorage();

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );
    _fadeAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeInOut,
    ));
    
    _checkAllPermissions();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  Future<void> _checkAllPermissions() async {
    setState(() {
      _isLoading = true;
    });

    try {
      // Check API configuration
      final config = await _secureStorage.getApiConfiguration();
      _isApiConfigured = config != null && 
          config.projectId.isNotEmpty && 
          config.serviceAccountJson != null;

      // Check accessibility permission
      _hasAccessibilityPermission = await _checkAccessibilityPermission();

      // Check overlay permission
      _hasOverlayPermission = await Permission.systemAlertWindow.isGranted;

      // Check audio permission
      _hasAudioPermission = await _checkAudioPermission();

      // Navigate to main app if all permissions are granted
      if (_isApiConfigured && _hasAccessibilityPermission && _hasOverlayPermission && _hasAudioPermission) {
        await Future.delayed(const Duration(seconds: 2));
        if (mounted) {
          Navigator.of(context).pushReplacement(
            MaterialPageRoute(builder: (context) => const TaskListScreen()),
          );
        }
      }
    } catch (e) {
      debugPrint('Error checking permissions: $e');
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        _animationController.forward();
      }
    }
  }

  Future<bool> _checkAccessibilityPermission() async {
    try {
      const platform = MethodChannel('com.vibeagent.dude/agent');
      final hasPermission = await platform.invokeMethod('checkAccessibilityPermission');
      
      // Save consent when permission is granted
      if (hasPermission) {
        await platform.invokeMethod('saveAccessibilityConsent', {'granted': true});
      }
      
      return hasPermission;
    } catch (e) {
      debugPrint('Error checking accessibility permission: $e');
      return false;
    }
  }

  Future<bool> _checkAudioPermission() async {
    try {
      const platform = MethodChannel('com.vibeagent.dude/agent');
      final hasPermission = await platform.invokeMethod('checkAudioPermissions');
      
      // Save consent when permission is granted
      if (hasPermission) {
        await platform.invokeMethod('saveAudioConsent', {'granted': true});
      }
      
      return hasPermission;
    } catch (e) {
      debugPrint('Error checking audio permission: $e');
      return false;
    }
  }

  Widget _buildPillButton({
    required String text,
    required VoidCallback? onPressed,
    required bool isPrimary,
    bool isLoading = false,
    IconData? icon,
  }) {
    final isTablet = MediaQuery.of(context).size.width > 600;
    
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
              horizontal: isTablet ? 32 : 28,
              vertical: isTablet ? 18 : 16,
            ),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(isTablet ? 30 : 25),
              gradient: isPrimary
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
              border: isPrimary 
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
      ),
    );
  }

  Future<void> _requestAccessibilityPermission() async {
    try {
      const platform = MethodChannel('com.vibeagent.dude/agent');
      await platform.invokeMethod('openAccessibilitySettings');
      await Future.delayed(const Duration(seconds: 1));
      
      // Check and save consent after user returns from settings
      await _checkAllPermissions();
    } catch (e) {
      debugPrint('Error requesting accessibility permission: $e');
    }
  }

  Future<void> _requestOverlayPermission() async {
    try {
      const platform = MethodChannel('com.vibeagent.dude/agent');
      await platform.invokeMethod('requestOverlayPermission');
      await Future.delayed(const Duration(seconds: 1));
      await _checkAllPermissions();
    } catch (e) {
      debugPrint('Error requesting overlay permission: $e');
    }
  }

  Future<void> _requestAudioPermission() async {
    try {
      const platform = MethodChannel('com.vibeagent.dude/agent');
      await platform.invokeMethod('requestAudioPermissions');
      await Future.delayed(const Duration(seconds: 1));
      await _checkAllPermissions();
    } catch (e) {
      debugPrint('Error requesting audio permission: $e');
    }
  }

  void _navigateToApiSettings() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const ApiSettingsScreen(),
      ),
    ).then((_) => _checkAllPermissions());
  }

  @override
  Widget build(BuildContext context) {
    final isTablet = MediaQuery.of(context).size.width > 600;
    final screenHeight = MediaQuery.of(context).size.height;

    return Scaffold(
      backgroundColor: Colors.white,
      body: _isLoading
          ? const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  CircularProgressIndicator(
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.green),
                    strokeWidth: 3,
                  ),
                  SizedBox(height: 24),
                  Text(
                    'Checking permissions...',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: Colors.black87,
                    ),
                  ),
                ],
              ),
            )
          : SafeArea(
              child: FadeTransition(
                opacity: _fadeAnimation,
                child: SingleChildScrollView(
                  padding: EdgeInsets.all(isTablet ? 32.0 : 24.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      SizedBox(height: screenHeight * 0.02),
                      
                      // App Logo
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: const LinearGradient(
                            colors: [Color(0xFF4CAF50), Color(0xFF2E7D32)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                        ),
                        child: Center(
                          child: Image.asset(
                            'assets/logo.png',
                            width: 65,
                            height: 65,
                            fit: BoxFit.contain,
                          ),
                        ),
                      ),
                      
                      const SizedBox(height: 24),
                      
                      const Text(
                        'heybro Setup',
                        style: TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                          color: Colors.black87,
                        ),
                      ),
                      
                      const SizedBox(height: 4),
                      
                      const Text(
                        'Complete the setup steps to start using your AI assistant',
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.black54,
                          height: 1.3,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      
                      const SizedBox(height: 12),
                      
                      // Permission list
                      _buildPermissionListItem(
                        title: 'API Configuration',
                        description: 'Configure Google Cloud Vertex AI credentials',
                        isCompleted: _isApiConfigured,
                        onTap: _navigateToApiSettings,
                        icon: Icons.cloud_outlined,
                        stepNumber: 1,
                        isLast: false,
                      ),
                      
                      _buildPermissionListItem(
                        title: 'Accessibility Service',
                        description: 'Allow AI to interact with other apps',
                        isCompleted: _hasAccessibilityPermission,
                        onTap: _requestAccessibilityPermission,
                        icon: Icons.accessibility_new_outlined,
                        stepNumber: 2,
                        isLast: false,
                      ),
                      
                      _buildPermissionListItem(
                        title: 'Display Overlay',
                        description: 'Show floating controls and indicators',
                        isCompleted: _hasOverlayPermission,
                        onTap: _requestOverlayPermission,
                        icon: Icons.layers_outlined,
                        stepNumber: 3,
                        isLast: false,
                      ),
                      
                      _buildPermissionListItem(
                        title: 'Audio Permission',
                        description: 'Allow voice commands and audio processing',
                        isCompleted: _hasAudioPermission,
                        onTap: _requestAudioPermission,
                        icon: Icons.mic_outlined,
                        stepNumber: 4,
                        isLast: true,
                      ),
                      
                      Container(
                        height: 1,
                        margin: const EdgeInsets.symmetric(vertical: 20),
                        color: Colors.green.withOpacity(0.3),
                      ),
                      
                      // Progress indicator with container box
                      Container(
                        margin: const EdgeInsets.symmetric(horizontal: 8),
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: Colors.green.withOpacity(0.2),
                            width: 1,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.green.withOpacity(0.08),
                              blurRadius: 8,
                              offset: const Offset(0, 2),
                            ),
                          ],
                        ),
                        child: _buildModernProgressIndicator(context),
                      ),
                      
                      const SizedBox(height: 20),
                      
                      // Consistent 3D Refresh button
                      Center(
                        child: Container(
                          margin: const EdgeInsets.symmetric(horizontal: 20),
                          child: _buildPillButton(
                            text: 'Refresh Status',
                            onPressed: _checkAllPermissions,
                            isPrimary: true,
                            icon: Icons.refresh_rounded,
                          ),
                        ),
                      ),
                      
                      const SizedBox(height: 12),
                    ],
                  ),
                ),
              ),
            ),
    );
  }

  Widget _buildPermissionCard({
    required String title,
    required String description,
    required bool isCompleted,
    required VoidCallback onTap,
    required IconData icon,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isCompleted ? Colors.green : Colors.grey.shade300,
          width: 2,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: isCompleted ? Colors.green : Colors.grey.shade100,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    icon,
                    color: isCompleted ? Colors.white : Colors.grey.shade600,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                          color: isCompleted ? Colors.green : Colors.black87,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        description,
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.black54,
                        ),
                      ),
                    ],
                  ),
                ),
                if (isCompleted)
                  Container(
                    width: 32,
                    height: 32,
                    decoration: const BoxDecoration(
                      color: Colors.green,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.check,
                      color: Colors.white,
                      size: 18,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            if (!isCompleted)
              SizedBox(
                width: double.infinity,
                child: _buildPillButton(
                  text: 'Configure',
                  onPressed: onTap,
                  isPrimary: true,
                  icon: Icons.settings,
                ),
              )
            else
              Row(
                children: [
                  Expanded(
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: Colors.green.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: Colors.green.withOpacity(0.3),
                        ),
                      ),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.check_circle,
                            color: Colors.green,
                            size: 20,
                          ),
                          SizedBox(width: 8),
                          Text(
                            'Completed',
                            style: TextStyle(
                              color: Colors.green,
                              fontWeight: FontWeight.w600,
                              fontSize: 16,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (title == 'API Configuration') ...[
                    const SizedBox(width: 12),
                    _buildPillButton(
                      text: 'Modify',
                      onPressed: onTap,
                      isPrimary: false,
                      icon: Icons.edit,
                    ),
                  ],
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionListItem({
    required String title,
    required String description,
    required IconData icon,
    required bool isCompleted,
    required VoidCallback onTap,
    required int stepNumber,
    required bool isLast,
  }) {
    return Column(
      children: [
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
            child: Row(
              children: [
                // Step number circle
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: isCompleted ? Colors.green : Colors.grey.shade400,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: (isCompleted ? Colors.green : Colors.grey.shade400).withOpacity(0.3),
                        blurRadius: 4,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Center(
                    child: isCompleted
                        ? const Icon(
                            Icons.check,
                            color: Colors.white,
                            size: 18,
                          )
                        : Text(
                            stepNumber.toString(),
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                            ),
                          ),
                  ),
                ),
                
                const SizedBox(width: 16),
                
                // Icon
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: (isCompleted ? Colors.green : Colors.grey.shade600).withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    icon,
                    color: isCompleted ? Colors.green : Colors.grey.shade600,
                    size: 22,
                  ),
                ),
                
                const SizedBox(width: 16),
                
                // Content
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: isCompleted ? Colors.green.shade700 : Colors.black87,
                          letterSpacing: 0.2,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        description,
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.black54,
                          height: 1.3,
                          fontWeight: FontWeight.w400,
                        ),
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(width: 8),
                
                // Chevron
                Icon(
                  Icons.chevron_right,
                  color: isCompleted ? Colors.green.shade600 : Colors.grey.shade400,
                  size: 24,
                ),
              ],
            ),
          ),
        ),
        if (!isLast)
          Container(
            height: 1,
            margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Colors.green.withOpacity(0.1),
                  Colors.green.withOpacity(0.3),
                  Colors.green.withOpacity(0.1),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildModernProgressIndicator(BuildContext context) {
    final completedCount = [
      _isApiConfigured,
      _hasAccessibilityPermission,
      _hasOverlayPermission,
      _hasAudioPermission,
    ].where((permission) => permission).length;
    
    final totalCount = 4;
    final progress = completedCount / totalCount;
    final isComplete = completedCount == totalCount;
    
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isComplete ? Icons.check_circle : Icons.trending_up,
                color: Colors.green,
                size: 20,
              ),
              const SizedBox(width: 8),
              const Text(
                'Setup Progress',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: Colors.black87,
                ),
              ),
              const Spacer(),
              Text(
                '$completedCount/$totalCount',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: Colors.green,
                ),
              ),
            ],
          ),
          
          const SizedBox(height: 8),
          
          // Progress bar
          Container(
            height: 4,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(2),
              color: Colors.grey.shade200,
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: progress,
                backgroundColor: Colors.transparent,
                valueColor: const AlwaysStoppedAnimation<Color>(Colors.green),
              ),
            ),
          ),
          
          const SizedBox(height: 8),
          
          // Status message
          Row(
            children: [
              Icon(
                isComplete ? Icons.rocket_launch : Icons.info_outline,
                color: Colors.green,
                size: 16,
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  isComplete
                      ? 'Ready to launch! All permissions are configured.'
                      : 'Complete all steps to continue.',
                  style: const TextStyle(
                    fontSize: 12,
                    color: Colors.green,
                  ),
                ),
              ),
            ],
          ),
          

        ],
      ),
    );
  }

  Widget _buildProgressIndicator(BuildContext context) {
    final completedCount = [_isApiConfigured, _hasAccessibilityPermission, _hasOverlayPermission]
        .where((permission) => permission)
        .length;
    final progress = completedCount / 3.0;
    final isComplete = completedCount == 3;
    
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isComplete ? Icons.check_circle : Icons.trending_up,
                color: Colors.green,
                size: 20,
              ),
              const SizedBox(width: 8),
              const Text(
                'Setup Progress',
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                  color: Colors.black87,
                ),
              ),
              const Spacer(),
              Text(
                '$completedCount/3',
                style: const TextStyle(
                  color: Colors.green,
                  fontWeight: FontWeight.w500,
                  fontSize: 12,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Container(
            height: 4,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(2),
              color: Colors.grey.shade200,
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: progress,
                backgroundColor: Colors.transparent,
                valueColor: const AlwaysStoppedAnimation<Color>(Colors.green),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(
                isComplete ? Icons.rocket_launch : Icons.info_outline,
                color: Colors.green,
                size: 16,
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  isComplete
                      ? '🎉 All set! You\'ll be redirected to the main app.'
                      : 'Complete the remaining steps to start using heybro.',
                  style: const TextStyle(
                    color: Colors.green,
                    fontSize: 12,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}