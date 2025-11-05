import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../secure_storage.dart';

class PorcupineSetupScreen extends StatefulWidget {
  const PorcupineSetupScreen({Key? key}) : super(key: key);

  @override
  State<PorcupineSetupScreen> createState() => _PorcupineSetupScreenState();
}

class _PorcupineSetupScreenState extends State<PorcupineSetupScreen> {
  final TextEditingController _apiKeyController = TextEditingController();
  final SecureStorage _secureStorage = SecureStorage();
  bool _isLoading = false;
  bool _isObscured = true;
  String? _errorMessage;

  static const platform = MethodChannel('com.vibeagent.dude/voice');

  @override
  void initState() {
    super.initState();
    _loadExistingKey();
  }

  Future<void> _loadExistingKey() async {
    try {
      final existingKey = await _secureStorage.getPorcupineKey();
      if (existingKey != null && existingKey.isNotEmpty) {
        _apiKeyController.text = existingKey;
      }
    } catch (e) {
      print('Error loading existing key: $e');
    }
  }

  Future<void> _saveApiKey() async {
    if (_apiKeyController.text.trim().isEmpty) {
      setState(() {
        _errorMessage = 'Please enter a valid API key';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      print('Saving API key to secure storage...');
      // Save to secure storage
      await _secureStorage.savePorcupineKey(_apiKeyController.text.trim());
      print('API key saved to secure storage successfully');
      
      // Send to native Android service
      print('Sending API key to native Android service...');
      final result = await platform.invokeMethod('setPorcupineAccessKey', {
        'accessKey': _apiKeyController.text.trim()
      });
      print('Native service response: $result');

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('API key saved successfully!'),
            backgroundColor: Colors.green,
          ),
        );
        Navigator.of(context).pop(true);
      }
    } catch (e) {
      print('Error saving API key: $e');
      setState(() {
        _errorMessage = 'Failed to save API key: $e';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _testApiKey() async {
    final apiKey = _apiKeyController.text.trim();
    
    if (apiKey.isEmpty) {
      setState(() {
        _errorMessage = 'Please enter an API key first';
      });
      return;
    }

    // Client-side validation
    if (apiKey.length < 80) {
      setState(() {
        _errorMessage = 'API key must be at least 80 characters long';
      });
      return;
    }

    if (!RegExp(r'^[a-zA-Z0-9]+$').hasMatch(apiKey)) {
      setState(() {
        _errorMessage = 'API key must contain only alphanumeric characters';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      print('Testing API key: ${apiKey.substring(0, 10)}...');
      final result = await platform.invokeMethod('testPorcupineKey', {
        'accessKey': apiKey
      });
      print('Test result: $result');
      
      if (mounted) {
        final isSuccess = result is Map && result['success'] == true;
        
        String message;
        if (isSuccess) {
          message = 'API key is valid! ✓';
        } else {
          // Provide more specific error messages
          if (result is Map && result.containsKey('error')) {
            final error = result['error'].toString();
            if (error.contains('00000136')) {
              message = 'Invalid API key: Authentication failed. Please check your Picovoice Console for the correct key.';
            } else if (error.contains('ACCESS_KEY_TEST_ERROR')) {
              message = 'API key test failed: Network or service error';
            } else {
              message = 'API key test failed: $error';
            }
          } else {
            message = 'API key is invalid or expired';
          }
        }
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(message),
            backgroundColor: isSuccess ? Colors.green : Colors.red,
            duration: Duration(seconds: isSuccess ? 2 : 4),
          ),
        );
      }
    } catch (e) {
      print('Error testing API key: $e');
      String errorMessage = 'Failed to test API key';
      
      if (e.toString().contains('ACCESS_KEY_TEST_ERROR')) {
        errorMessage = 'API key validation failed: Please check your internet connection and try again';
      } else if (e.toString().contains('INVALID_ACCESS_KEY')) {
        errorMessage = 'Invalid API key format';
      } else {
        errorMessage = 'Failed to test API key: ${e.toString()}';
      }
      
      setState(() {
        _errorMessage = errorMessage;
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Porcupine Setup'),
        backgroundColor: Colors.blue.shade700,
        foregroundColor: Colors.white,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Voice Wake Word Setup',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Enter your Porcupine API key to enable voice wake word detection. You can get a free API key from Picovoice Console.',
              style: TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 24),
            TextField(
              controller: _apiKeyController,
              obscureText: _isObscured,
              decoration: InputDecoration(
                labelText: 'Porcupine Access Key',
                hintText: 'Enter your API key here',
                border: const OutlineInputBorder(),
                suffixIcon: IconButton(
                  icon: Icon(_isObscured ? Icons.visibility : Icons.visibility_off),
                  onPressed: () {
                    setState(() {
                      _isObscured = !_isObscured;
                    });
                  },
                ),
                errorText: _errorMessage,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _testApiKey,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.orange,
                      foregroundColor: Colors.white,
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                            ),
                          )
                        : const Text('Test Key'),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _saveApiKey,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.blue.shade700,
                      foregroundColor: Colors.white,
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                            ),
                          )
                        : const Text('Save Key'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.blue.shade50,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.blue.shade200),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info, color: Colors.blue.shade700),
                      const SizedBox(width: 8),
                      Text(
                        'How to get API Key',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Colors.blue.shade700,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '1. Visit console.picovoice.ai\n'
                    '2. Sign up for a free account\n'
                    '3. Create a new project\n'
                    '4. Copy your Access Key\n'
                    '5. Paste it above and save',
                    style: TextStyle(fontSize: 14),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    super.dispose();
  }
}