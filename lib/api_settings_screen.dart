import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'secure_storage.dart';
import 'vertex_ai_client.dart';

class ApiSettingsScreen extends StatefulWidget {
  const ApiSettingsScreen({super.key});

  @override
  State<ApiSettingsScreen> createState() => _ApiSettingsScreenState();
}

class _ApiSettingsScreenState extends State<ApiSettingsScreen> {
  final _formKey = GlobalKey<FormState>();
  final _projectIdController = TextEditingController();
  final _serviceAccountController = TextEditingController();

  final SecureStorage _secureStorage = SecureStorage();
  final VertexAIClient _vertexAI = VertexAIClient();

  String _selectedModel = 'gemini-2.5-pro';
  String _selectedRegion = 'us-central1';
  bool _isLoading = false;
  bool _isTesting = false;
  String _statusMessage = '';
  Color _statusColor = Colors.grey;
  bool _useServiceAccount = false;

  final Map<String, List<String>> _regionModels = {
    'asia-east1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-east2': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-northeast1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-northeast2': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-northeast3': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-south1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-south2': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-southeast1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'asia-southeast2': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'australia-southeast1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'australia-southeast2': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-central1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-east1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-east4': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-east5': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-south1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-west1': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-west2': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-west3': ['gemini-2.5-pro', 'gemini-2.5-flash'],
    'us-west4': ['gemini-2.5-pro', 'gemini-2.5-flash'],
  };

  @override
  void initState() {
    super.initState();
    _initializeStorage();
  }

  Future<void> _initializeStorage() async {
    try {
      await _secureStorage.initialize();
      await _loadExistingConfig();
    } catch (e) {
      _setStatus('Error initializing storage: $e', Colors.red);
    }
  }

  @override
  void dispose() {
    _projectIdController.dispose();
    _serviceAccountController.dispose();
    super.dispose();
  }

  Future<void> _loadExistingConfig() async {
    setState(() => _isLoading = true);
    try {
      final config = await _secureStorage.getApiConfiguration();
      if (config != null) {
        _projectIdController.text = config.projectId;
        setState(() {
          if (_regionModels.containsKey(config.region)) {
            _selectedRegion = config.region;
          } else {
            _selectedRegion = 'us-central1';
          }
          
          if (_regionModels[_selectedRegion]?.contains(config.modelId) == true) {
            _selectedModel = config.modelId;
          } else {
            _selectedModel = _regionModels[_selectedRegion]?.first ?? 'gemini-2.5-pro';
          }
          _useServiceAccount = config.serviceAccountJson?.isNotEmpty == true;
        });
        if (config.serviceAccountJson?.isNotEmpty == true) {
          _serviceAccountController.text = config.serviceAccountJson!;
        }
      }
    } catch (e) {
      _setStatus('Error loading configuration: $e', Colors.red);
    } finally {
      setState(() => _isLoading = false);
    }
  }

  void _setStatus(String message, Color color) {
    setState(() {
      _statusMessage = message;
      _statusColor = color;
    });
  }

  Future<void> _saveConfiguration() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isLoading = true);
    _setStatus('Saving configuration...', Colors.blue);

    try {
      // Ensure storage is initialized before saving
      await _secureStorage.initialize();
      
      await _secureStorage.saveApiConfiguration(
        projectId: _projectIdController.text.trim(),
        region: _selectedRegion,
        modelId: _selectedModel,
        serviceAccountJson: _useServiceAccount ? _serviceAccountController.text.trim() : '',
      );
      _setStatus('Configuration saved successfully!', const Color(0xFF4CAF50));
    } catch (e) {
      _setStatus('Error saving configuration: $e', Colors.red);
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _testConnection() async {
    if (!_formKey.currentState!.validate()) {
      _setStatus('Please fill in all required fields', Colors.orange);
      return;
    }

    setState(() => _isTesting = true);
    _setStatus('Testing connection...', Colors.blue);

    try {
      // Ensure storage is initialized before saving
      await _secureStorage.initialize();
      
      // First save the configuration, then test
      await _secureStorage.saveApiConfiguration(
        projectId: _projectIdController.text.trim(),
        region: _selectedRegion,
        modelId: _selectedModel,
        serviceAccountJson: _useServiceAccount ? _serviceAccountController.text.trim() : '',
      );
      
      // Reinitialize the client with new config
      await _vertexAI.initialize();
      
      // Test the connection
      final success = await _vertexAI.testConnection();
      if (!success) {
        throw Exception('Connection test failed');
      }
      _setStatus('Connection test successful!', const Color(0xFF4CAF50));
    } catch (e) {
      _setStatus('Connection test failed: $e', Colors.red);
    } finally {
      setState(() => _isTesting = false);
    }
  }

  Future<void> _pasteFromClipboard() async {
    try {
      final clipboardData = await Clipboard.getData(Clipboard.kTextPlain);
      if (clipboardData?.text != null) {
        _serviceAccountController.text = clipboardData!.text!;
        _setStatus('Pasted from clipboard', const Color(0xFF4CAF50));
      }
    } catch (e) {
      _setStatus('Failed to paste from clipboard', Colors.red);
    }
  }

  void _clearServiceAccount() {
    _serviceAccountController.clear();
    _setStatus('Service account cleared', Colors.grey);
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

  @override
  Widget build(BuildContext context) {
    final isTablet = MediaQuery.of(context).size.width > 600;
    
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios, color: Color(0xFF2E7D32)),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          'API Settings',
          style: TextStyle(
            color: Color(0xFF2E7D32),
            fontWeight: FontWeight.w700,
            fontSize: 24,
          ),
        ),
        centerTitle: true,
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(
                valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF4CAF50)),
              ),
            )
          : SingleChildScrollView(
              padding: EdgeInsets.all(isTablet ? 32 : 24),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Header
                    Text(
                      'Google Cloud Vertex AI',
                      style: TextStyle(
                        fontSize: isTablet ? 28 : 24,
                        fontWeight: FontWeight.w700,
                        color: const Color(0xFF1B5E20),
                      ),
                    ),
                    SizedBox(height: isTablet ? 8 : 6),
                    Text(
                      'Configure your AI model settings',
                      style: TextStyle(
                        fontSize: isTablet ? 16 : 14,
                        color: Colors.grey[600],
                        fontWeight: FontWeight.w400,
                      ),
                    ),
                    SizedBox(height: isTablet ? 40 : 32),

                    // Project ID
                    Text(
                      'Project ID',
                      style: TextStyle(
                        fontSize: isTablet ? 18 : 16,
                        fontWeight: FontWeight.w600,
                        color: const Color(0xFF2E7D32),
                      ),
                    ),
                    SizedBox(height: isTablet ? 12 : 8),
                    TextFormField(
                      controller: _projectIdController,
                      decoration: InputDecoration(
                        hintText: 'Enter your Google Cloud Project ID',
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFE0E0E0)),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFF4CAF50), width: 2),
                        ),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: isTablet ? 20 : 16,
                          vertical: isTablet ? 20 : 16,
                        ),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Project ID is required';
                        }
                        return null;
                      },
                    ),
                    SizedBox(height: isTablet ? 32 : 24),

                    // Region Selection
                    Text(
                      'Region',
                      style: TextStyle(
                        fontSize: isTablet ? 18 : 16,
                        fontWeight: FontWeight.w600,
                        color: const Color(0xFF2E7D32),
                      ),
                    ),
                    SizedBox(height: isTablet ? 12 : 8),
                    DropdownButtonFormField<String>(
                      value: _selectedRegion,
                      decoration: InputDecoration(
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFE0E0E0)),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFF4CAF50), width: 2),
                        ),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: isTablet ? 20 : 16,
                          vertical: isTablet ? 20 : 16,
                        ),
                      ),
                      items: _regionModels.keys.map((region) {
                        return DropdownMenuItem(
                          value: region,
                          child: Text(region),
                        );
                      }).toList(),
                      onChanged: (value) {
                        if (value != null) {
                          setState(() {
                            _selectedRegion = value;
                            _selectedModel = _regionModels[value]?.first ?? 'gemini-2.5-pro';
                          });
                        }
                      },
                    ),
                    SizedBox(height: isTablet ? 32 : 24),

                    // Model Selection
                    Text(
                      'AI Model',
                      style: TextStyle(
                        fontSize: isTablet ? 18 : 16,
                        fontWeight: FontWeight.w600,
                        color: const Color(0xFF2E7D32),
                      ),
                    ),
                    SizedBox(height: isTablet ? 12 : 8),
                    DropdownButtonFormField<String>(
                      value: _selectedModel,
                      decoration: InputDecoration(
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFFE0E0E0)),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: Color(0xFF4CAF50), width: 2),
                        ),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: isTablet ? 20 : 16,
                          vertical: isTablet ? 20 : 16,
                        ),
                      ),
                      items: (_regionModels[_selectedRegion] ?? []).map((model) {
                        return DropdownMenuItem(
                          value: model,
                          child: Text(model),
                        );
                      }).toList(),
                      onChanged: (value) {
                        if (value != null) {
                          setState(() {
                            _selectedModel = value;
                          });
                        }
                      },
                    ),
                    SizedBox(height: isTablet ? 32 : 24),

                    // Service Account Toggle
                    Row(
                      children: [
                        Switch(
                          value: _useServiceAccount,
                          onChanged: (value) {
                            setState(() {
                              _useServiceAccount = value;
                              if (!value) {
                                _serviceAccountController.clear();
                              }
                            });
                          },
                          activeColor: const Color(0xFF4CAF50),
                        ),
                        SizedBox(width: isTablet ? 16 : 12),
                        Expanded(
                          child: Text(
                            'Use Service Account JSON',
                            style: TextStyle(
                              fontSize: isTablet ? 18 : 16,
                              fontWeight: FontWeight.w600,
                              color: const Color(0xFF2E7D32),
                            ),
                          ),
                        ),
                      ],
                    ),

                    // Service Account JSON Field
                    if (_useServiceAccount) ...[
                      SizedBox(height: isTablet ? 24 : 16),
                      Text(
                        'Service Account JSON',
                        style: TextStyle(
                          fontSize: isTablet ? 18 : 16,
                          fontWeight: FontWeight.w600,
                          color: const Color(0xFF2E7D32),
                        ),
                      ),
                      SizedBox(height: isTablet ? 12 : 8),
                      TextFormField(
                        controller: _serviceAccountController,
                        decoration: InputDecoration(
                          hintText: 'Paste your service account JSON here',
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: const BorderSide(color: Color(0xFFE0E0E0)),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: const BorderSide(color: Color(0xFF4CAF50), width: 2),
                          ),
                          contentPadding: EdgeInsets.symmetric(
                            horizontal: isTablet ? 20 : 16,
                            vertical: isTablet ? 20 : 16,
                          ),
                        ),
                        maxLines: 8,
                        validator: _useServiceAccount
                            ? (value) {
                                if (value == null || value.trim().isEmpty) {
                                  return 'Service Account JSON is required';
                                }
                                try {
                                  json.decode(value);
                                } catch (e) {
                                  return 'Invalid JSON format';
                                }
                                return null;
                              }
                            : null,
                      ),
                      SizedBox(height: isTablet ? 16 : 12),
                      Row(
                        children: [
                          Expanded(
                            child: _buildPillButton(
                              text: 'Paste',
                              onPressed: _pasteFromClipboard,
                              isPrimary: false,
                              icon: Icons.paste,
                            ),
                          ),
                          SizedBox(width: isTablet ? 16 : 12),
                          Expanded(
                            child: _buildPillButton(
                              text: 'Clear',
                              onPressed: _clearServiceAccount,
                              isPrimary: false,
                              icon: Icons.clear,
                            ),
                          ),
                        ],
                      ),
                    ],

                    SizedBox(height: isTablet ? 40 : 32),

                    // Status Message
                    if (_statusMessage.isNotEmpty) ...[
                      Text(
                        _statusMessage,
                        style: TextStyle(
                          color: _statusColor,
                          fontWeight: FontWeight.w500,
                          fontSize: isTablet ? 16 : 14,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      SizedBox(height: isTablet ? 24 : 16),
                    ],

                    // Action Buttons
                    Row(
                      children: [
                        Expanded(
                          child: _buildPillButton(
                            text: 'Save',
                            onPressed: _isLoading ? null : _saveConfiguration,
                            isPrimary: true,
                            isLoading: _isLoading,
                            icon: Icons.save,
                          ),
                        ),
                        SizedBox(width: isTablet ? 16 : 12),
                        Expanded(
                          child: _buildPillButton(
                            text: 'Test',
                            onPressed: _isTesting ? null : _testConnection,
                            isPrimary: false,
                            isLoading: _isTesting,
                            icon: Icons.wifi_tethering,
                          ),
                        ),
                      ],
                    ),

                    SizedBox(height: isTablet ? 40 : 32),

                    // Setup Instructions
                    Text(
                      'Setup Instructions',
                      style: TextStyle(
                        fontSize: isTablet ? 20 : 18,
                        fontWeight: FontWeight.w700,
                        color: const Color(0xFF1B5E20),
                      ),
                    ),
                    SizedBox(height: isTablet ? 16 : 12),
                    Text(
                      '1. Create a Google Cloud Project\n'
                      '2. Enable the Vertex AI API\n'
                      '3. Create a Service Account (optional)\n'
                      '4. Download the JSON key file\n'
                      '5. Paste the JSON content above',
                      style: TextStyle(
                        fontSize: isTablet ? 16 : 14,
                        color: Colors.grey[700],
                        height: 1.6,
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}
