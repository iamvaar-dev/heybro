import 'dart:convert';
import 'dart:math';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure storage utility for API keys and sensitive configuration data
class SecureStorage {
  static const String _apiKeyKey = 'vertex_ai_api_key';
  static const String _projectIdKey = 'vertex_ai_project_id';
  static const String _modelConfigKey = 'vertex_ai_model_config';
  static const String _encryptionKeyKey = 'app_encryption_key';
  static const String _serviceAccountKey = 'vertex_ai_service_account';
  static const String _refreshTokenKey = 'vertex_ai_refresh_token';
  static const String _porcupineKeyKey = 'porcupine_access_key';

  final FlutterSecureStorage _secureStorage;

  /// Available Gemini models with their expiration dates
  static const Map<String, Map<String, String>> availableModels = {
    'gemini-2.5-pro': {
      'name': 'Gemini 2.5 Pro',
      'expiry': 'June 17, 2026',
      'capabilities': 'Advanced reasoning, complex tasks',
      'recommended': 'true',
    },
    'gemini-2.5-flash': {
      'name': 'Gemini 2.5 Flash',
      'expiry': 'June 17, 2026',
      'capabilities': 'Fast responses, efficient processing',
      'recommended': 'true',
    },
    'gemini-2.5-flash-lite': {
      'name': 'Gemini 2.5 Flash Lite',
      'expiry': 'July 22, 2026',
      'capabilities': 'Lightweight, mobile-optimized',
      'recommended': 'false',
    },
    'gemini-2.0-flash-001': {
      'name': 'Gemini 2.0 Flash',
      'expiry': 'February 5, 2026',
      'capabilities': 'Legacy model, stable',
      'recommended': 'false',
    },
    'gemini-2.0-flash-lite-001': {
      'name': 'Gemini 2.0 Flash Lite',
      'expiry': 'February 25, 2026',
      'capabilities': 'Legacy lightweight model',
      'recommended': 'false',
    },
  };

  SecureStorage({
    FlutterSecureStorage? storage,
  }) : _secureStorage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(
                encryptedSharedPreferences: true,
                keyCipherAlgorithm: KeyCipherAlgorithm.RSA_ECB_PKCS1Padding,
                storageCipherAlgorithm:
                    StorageCipherAlgorithm.AES_GCM_NoPadding,
              ),
              iOptions: IOSOptions(
                accessibility: KeychainAccessibility.first_unlock_this_device,
              ),
            );

  /// Initialize secure storage and generate encryption key if needed
  Future<void> initialize() async {
    try {
      // Generate app-specific encryption key if it doesn't exist
      final existingKey = await _secureStorage.read(key: _encryptionKeyKey);
      if (existingKey == null) {
        final encryptionKey = _generateEncryptionKey();
        await _secureStorage.write(
            key: _encryptionKeyKey, value: encryptionKey);
      } else {}
    } catch (e) {
      rethrow;
    }
  }

  /// Save Vertex AI Service Account configuration securely
  Future<void> saveApiConfiguration({
    String apiKey = 'service_account_auth', // Placeholder for compatibility
    required String projectId,
    String modelId = 'gemini-2.5-pro',
    String region = 'us-central1',
    required String serviceAccountJson,
    String? refreshToken,
    Map<String, dynamic>? additionalConfig,
  }) async {
    try {
      // Validate inputs
      if (!_isValidProjectId(projectId)) {
        throw ArgumentError('Invalid Google Cloud project ID format');
      }
      if (!availableModels.containsKey(modelId)) {
        throw ArgumentError('Unsupported model ID: $modelId');
      }
      if (!_isValidServiceAccount(serviceAccountJson)) {
        throw ArgumentError('Invalid Service Account JSON format');
      }

      // Save placeholder API key for compatibility
      final encryptedApiKey = await _encryptData(apiKey);
      await _secureStorage.write(key: _apiKeyKey, value: encryptedApiKey);

      // Save project ID (less sensitive, but still encrypted)
      final encryptedProjectId = await _encryptData(projectId);
      await _secureStorage.write(key: _projectIdKey, value: encryptedProjectId);

      // Save model configuration
      final modelConfig = {
        'model_id': modelId,
        'model_name': availableModels[modelId]!['name'],
        'region': region,
        'saved_at': DateTime.now().toIso8601String(),
        'additional_config': additionalConfig ?? {},
      };
      final encryptedModelConfig = await _encryptData(jsonEncode(modelConfig));
      await _secureStorage.write(
          key: _modelConfigKey, value: encryptedModelConfig);

      // Save service account JSON (required)
      final encryptedServiceAccount = await _encryptData(serviceAccountJson);
      await _secureStorage.write(
          key: _serviceAccountKey, value: encryptedServiceAccount);

      // Save refresh token if provided
      if (refreshToken != null && refreshToken.isNotEmpty) {
        final encryptedRefreshToken = await _encryptData(refreshToken);
        await _secureStorage.write(
            key: _refreshTokenKey, value: encryptedRefreshToken);
      }
    } catch (e) {
      rethrow;
    }
  }

  /// Retrieve Service Account configuration
  Future<ApiConfiguration?> getApiConfiguration() async {
    try {
      final encryptedApiKey = await _secureStorage.read(key: _apiKeyKey);
      final encryptedProjectId = await _secureStorage.read(key: _projectIdKey);
      final encryptedModelConfig =
          await _secureStorage.read(key: _modelConfigKey);

      if (encryptedApiKey == null || encryptedProjectId == null) {
        return null;
      }

      final apiKey = await _decryptData(encryptedApiKey);
      final projectId = await _decryptData(encryptedProjectId);

      Map<String, dynamic> modelConfig = {};
      if (encryptedModelConfig != null) {
        final decryptedConfig = await _decryptData(encryptedModelConfig);
        modelConfig = jsonDecode(decryptedConfig);
      }

      // Get service account (required)
      String? serviceAccountJson;
      final encryptedServiceAccount =
          await _secureStorage.read(key: _serviceAccountKey);
      if (encryptedServiceAccount != null) {
        serviceAccountJson = await _decryptData(encryptedServiceAccount);
      } else {
        return null;
      }

      // Get optional refresh token
      String? refreshToken;
      final encryptedRefreshToken =
          await _secureStorage.read(key: _refreshTokenKey);
      if (encryptedRefreshToken != null) {
        refreshToken = await _decryptData(encryptedRefreshToken);
      }

      return ApiConfiguration(
        apiKey: apiKey,
        projectId: projectId,
        modelId: modelConfig['model_id'] ?? 'gemini-2.5-pro',
        modelName: modelConfig['model_name'] ?? 'Gemini 2.5 Pro',
        region: modelConfig['region'] ?? 'us-central1',
        serviceAccountJson: serviceAccountJson,
        refreshToken: refreshToken,
        savedAt: DateTime.tryParse(modelConfig['saved_at'] ?? ''),
        additionalConfig: Map<String, dynamic>.from(
          modelConfig['additional_config'] ?? {},
        ),
      );
    } catch (e) {
      return null;
    }
  }

  /// Check if Service Account configuration exists
  Future<bool> hasApiConfiguration() async {
    try {
      final projectId = await _secureStorage.read(key: _projectIdKey);
      final serviceAccount = await _secureStorage.read(key: _serviceAccountKey);
      return projectId != null && serviceAccount != null;
    } catch (e) {
      return false;
    }
  }

  /// Delete all stored Service Account configuration
  Future<void> clearApiConfiguration() async {
    try {
      await _secureStorage.delete(key: _apiKeyKey);
      await _secureStorage.delete(key: _projectIdKey);
      await _secureStorage.delete(key: _modelConfigKey);
      await _secureStorage.delete(key: _serviceAccountKey);
      await _secureStorage.delete(key: _refreshTokenKey);
    } catch (e) {
      rethrow;
    }
  }

  /// Update only the model configuration
  Future<void> updateModelConfiguration(String modelId) async {
    try {
      if (!availableModels.containsKey(modelId)) {
        throw ArgumentError('Unsupported model ID: $modelId');
      }

      final existingConfig = await getApiConfiguration();
      if (existingConfig == null) {
        throw StateError('No existing Service Account configuration found');
      }

      final modelConfig = {
        'model_id': modelId,
        'model_name': availableModels[modelId]!['name'],
        'saved_at': DateTime.now().toIso8601String(),
        'additional_config': existingConfig.additionalConfig,
      };

      final encryptedModelConfig = await _encryptData(jsonEncode(modelConfig));
      await _secureStorage.write(
          key: _modelConfigKey, value: encryptedModelConfig);
    } catch (e) {
      rethrow;
    }
  }

  /// Validate Service Account JSON format
  bool _isValidServiceAccount(String serviceAccountJson) {
    if (serviceAccountJson.isEmpty) return false;

    try {
      final decoded = jsonDecode(serviceAccountJson);
      return decoded is Map<String, dynamic> &&
          decoded['type'] == 'service_account' &&
          decoded['private_key'] != null &&
          decoded['client_email'] != null &&
          decoded['project_id'] != null;
    } catch (e) {
      return false;
    }
  }

  /// Validate Google Cloud project ID format
  bool _isValidProjectId(String projectId) {
    // Project IDs must be 6-30 characters, lowercase letters, digits, and hyphens
    final regex = RegExp(r'^[a-z0-9\-]{6,30}$');
    return regex.hasMatch(projectId);
  }

  /// Generate a random encryption key
  String _generateEncryptionKey() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (i) => random.nextInt(256));
    return base64Encode(bytes);
  }

  /// Encrypt data using app-specific key
  Future<String> _encryptData(String data) async {
    try {
      final encryptionKey = await _secureStorage.read(key: _encryptionKeyKey);
      if (encryptionKey == null) {
        throw StateError('Encryption key not found');
      }

      // Simple XOR encryption with base64 encoding for additional obfuscation
      final keyBytes = base64Decode(encryptionKey);
      final dataBytes = utf8.encode(data);
      final encryptedBytes = <int>[];

      for (int i = 0; i < dataBytes.length; i++) {
        encryptedBytes.add(dataBytes[i] ^ keyBytes[i % keyBytes.length]);
      }

      // Add timestamp and random salt for additional security
      final timestamp = DateTime.now().millisecondsSinceEpoch.toString();
      final salt = _generateRandomString(8);
      final combined = '$timestamp|$salt|${base64Encode(encryptedBytes)}';

      return base64Encode(utf8.encode(combined));
    } catch (e) {
      rethrow;
    }
  }

  /// Decrypt data using app-specific key
  Future<String> _decryptData(String encryptedData) async {
    try {
      final encryptionKey = await _secureStorage.read(key: _encryptionKeyKey);
      if (encryptionKey == null) {
        throw StateError('Encryption key not found');
      }

      // Decode the combined data
      final combinedBytes = base64Decode(encryptedData);
      final combined = utf8.decode(combinedBytes);
      final parts = combined.split('|');

      if (parts.length != 3) {
        throw FormatException('Invalid encrypted data format');
      }

      final encryptedBytes = base64Decode(parts[2]);
      final keyBytes = base64Decode(encryptionKey);
      final decryptedBytes = <int>[];

      for (int i = 0; i < encryptedBytes.length; i++) {
        decryptedBytes.add(encryptedBytes[i] ^ keyBytes[i % keyBytes.length]);
      }

      return utf8.decode(decryptedBytes);
    } catch (e) {
      rethrow;
    }
  }

  /// Generate random string for salt
  String _generateRandomString(int length) {
    const chars =
        'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    final random = Random.secure();
    return String.fromCharCodes(
      Iterable.generate(
          length, (_) => chars.codeUnitAt(random.nextInt(chars.length))),
    );
  }

  /// Save Porcupine API key securely
  Future<void> savePorcupineKey(String apiKey) async {
    try {
      final encryptedKey = await _encryptData(apiKey);
      await _secureStorage.write(key: _porcupineKeyKey, value: encryptedKey);
    } catch (e) {
      rethrow;
    }
  }

  /// Get Porcupine API key
  Future<String?> getPorcupineKey() async {
    try {
      final encryptedKey = await _secureStorage.read(key: _porcupineKeyKey);
      if (encryptedKey == null) {
        return null;
      }
      return await _decryptData(encryptedKey);
    } catch (e) {
      return null;
    }
  }

  /// Clear all secure storage (use with caution)
  Future<void> clearAll() async {
    try {
      await _secureStorage.deleteAll();
    } catch (e) {
      rethrow;
    }
  }

  /// Get storage statistics
  Future<Map<String, dynamic>> getStorageInfo() async {
    try {
      final hasApi = await hasApiConfiguration();
      final config = await getApiConfiguration();

      return {
        'has_configuration': hasApi,
        'model_id': config?.modelId,
        'model_name': config?.modelName,
        'project_id_set': config?.projectId != null,
        'service_account_set': config?.serviceAccountJson != null,
        'refresh_token_set': config?.refreshToken != null,
        'saved_at': config?.savedAt?.toIso8601String(),
        'available_models': availableModels.keys.toList(),
        'auth_method': 'service_account',
      };
    } catch (e) {
      return {'error': e.toString()};
    }
  }
}

/// Data class for Service Account configuration
class ApiConfiguration {
  final String apiKey;
  final String projectId;
  final String modelId;
  final String modelName;
  final String region;
  final String? serviceAccountJson;
  final String? refreshToken;
  final DateTime? savedAt;
  final Map<String, dynamic> additionalConfig;

  const ApiConfiguration({
    required this.apiKey,
    required this.projectId,
    required this.modelId,
    required this.modelName,
    this.region = 'us-central1',
    this.serviceAccountJson,
    this.refreshToken,
    this.savedAt,
    this.additionalConfig = const {},
  });

  /// Get Vertex AI endpoint URL
  String getEndpointUrl({String? location}) {
    final loc = location ?? region;
    return 'https://$loc-aiplatform.googleapis.com/v1/projects/$projectId/locations/$loc/publishers/google/models/$modelId:predict';
  }

  /// Check if configuration is valid
  bool get isValid {
    return projectId.isNotEmpty &&
        serviceAccountJson != null &&
        serviceAccountJson!.isNotEmpty &&
        SecureStorage.availableModels.containsKey(modelId);
  }

  /// Check if model is recommended
  bool get isRecommendedModel {
    return SecureStorage.availableModels[modelId]?['recommended'] == 'true';
  }

  /// Get model capabilities
  String get modelCapabilities {
    return SecureStorage.availableModels[modelId]?['capabilities'] ??
        'Unknown capabilities';
  }

  /// Get model expiry date
  String get modelExpiry {
    return SecureStorage.availableModels[modelId]?['expiry'] ??
        'Unknown expiry';
  }

  @override
  String toString() {
    return 'ApiConfiguration(projectId: $projectId, modelId: $modelId, hasServiceAccount: ${serviceAccountJson != null})';
  }
}
