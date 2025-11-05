import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:jose/jose.dart';
import 'secure_storage.dart';

class VertexAIClient {
  static const String _defaultModel = 'gemini-2.5-pro';
  static const String _defaultRegion = 'us-central1';

  // Model availability by region
  static const Map<String, List<String>> _modelAvailability = {
    'us-central1': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'us-east1': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'us-east4': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'us-west1': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'us-west2': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'us-west3': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'us-west4': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'northamerica-northeast1': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'northamerica-northeast2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'europe-west1': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'europe-west2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'europe-west3': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'europe-west4': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'europe-central2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-east1': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-east2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-south1': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-south2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-northeast1': ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-northeast2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-northeast3': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-southeast1': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'asia-southeast2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'australia-southeast1': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'australia-southeast2': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
    'southamerica-east1': ['gemini-2.5-flash', 'gemini-2.5-flash-lite'],
  };

  // Singleton pattern
  static final VertexAIClient _instance = VertexAIClient._internal();
  factory VertexAIClient() => _instance;
  VertexAIClient._internal();

  // Configuration
  String _projectId = '';
  String _region = _defaultRegion;
  String _model = _defaultModel;
  String? _serviceAccountJson;
  String? _accessToken;
  DateTime? _tokenExpiry;

  // HTTP client
  final http.Client _httpClient = http.Client();
  final SecureStorage _secureStorage = SecureStorage();

  // State
  bool _isInitialized = false;

  /// Initialize the client with stored configuration
  Future<bool> initialize() async {
    try {
      await _secureStorage.initialize();
      return await _loadConfiguration();
    } catch (e) {
      print('❌ VertexAI: Initialization failed: $e');
      return false;
    }
  }

  /// Load configuration from secure storage
  Future<bool> _loadConfiguration() async {
    try {
      final config = await _secureStorage.getApiConfiguration();

      if (config == null || config.projectId.isEmpty || config.serviceAccountJson?.isEmpty != false) {
        print('⚠️ VertexAI: Configuration incomplete');
        return false;
      }

      _projectId = config.projectId;
      _serviceAccountJson = config.serviceAccountJson;
      _region = config.region; // Use saved region
      _model = _getAvailableModel(config.modelId, _region);

      // Clear cached token to force refresh
      _accessToken = null;
      _tokenExpiry = null;

      _isInitialized = true;
      return true;
    } catch (e) {
      print('❌ VertexAI: Failed to load configuration: $e');
      return false;
    }
  }

  /// Get the base URL for the current region
  String get _baseUrl => 'https://$_region-aiplatform.googleapis.com';

  /// Get an available model for the given region
  String _getAvailableModel(String requestedModel, String region) {
    final availableModels = _modelAvailability[region] ?? ['gemini-2.5-flash-lite'];

    // If requested model is available in this region, use it
    if (availableModels.contains(requestedModel)) {
      print('✅ VertexAI: Using requested model $requestedModel in $region');
      return requestedModel;
    }

    // Otherwise, use the best available model
    String fallbackModel;
    if (availableModels.contains('gemini-2.5-pro')) {
      fallbackModel = 'gemini-2.5-pro';
    } else if (availableModels.contains('gemini-2.5-flash')) {
      fallbackModel = 'gemini-2.5-flash';
    } else {
      fallbackModel = 'gemini-2.5-flash-lite';
    }

    print('⚠️ VertexAI: Model $requestedModel not available in $region, using $fallbackModel');
    return fallbackModel;
  }

  /// Generate content with text prompt only
  Future<String?> generateContent(String prompt) async {
    return await _generateContent(prompt, null);
  }

  /// Generate content with text prompt and image
  Future<String?> generateContentWithImage(String prompt, String base64Image) async {
    return await _generateContent(prompt, base64Image);
  }

  /// Internal method to generate content
  Future<String?> _generateContent(String prompt, String? base64Image) async {
    if (!_isInitialized) {
      print('❌ VertexAI: Client not initialized');
      return null;
    }

    try {
      // Ensure we have a valid access token
      if (!await _ensureValidToken()) {
        print('❌ VertexAI: Failed to get valid token');
        return null;
      }

      final requestBody = _buildRequestBody(prompt, base64Image);
      final response = await _makeRequest(requestBody);

      if (response != null) {
        return _parseResponse(response);
      }

      return null;
    } catch (e) {
      print('❌ VertexAI: Error generating content: $e');
      return null;
    }
  }

  /// Build request body for Vertex AI API
  Map<String, dynamic> _buildRequestBody(String prompt, String? base64Image) {
    final contents = <Map<String, dynamic>>[];
    final parts = <Map<String, dynamic>>[];

    // Add text part
    parts.add({
      'text': prompt,
    });

    // Add image part if provided
    if (base64Image != null && base64Image.isNotEmpty) {
      parts.add({
        'inline_data': {
          'mime_type': 'image/png',
          'data': base64Image,
        },
      });
    }

    contents.add({
      'role': 'user',
      'parts': parts,
    });

    return {
      'contents': contents,
      'generation_config': {
        'temperature': 0.1,
        'top_p': 0.8,
        'top_k': 40,
        'max_output_tokens': 8192,
      },
      'safety_settings': [
        {
          'category': 'HARM_CATEGORY_HARASSMENT',
          'threshold': 'BLOCK_MEDIUM_AND_ABOVE',
        },
        {
          'category': 'HARM_CATEGORY_HATE_SPEECH',
          'threshold': 'BLOCK_MEDIUM_AND_ABOVE',
        },
        {
          'category': 'HARM_CATEGORY_SEXUALLY_EXPLICIT',
          'threshold': 'BLOCK_MEDIUM_AND_ABOVE',
        },
        {
          'category': 'HARM_CATEGORY_DANGEROUS_CONTENT',
          'threshold': 'BLOCK_MEDIUM_AND_ABOVE',
        },
      ],
    };
  }

  /// Make HTTP request to Vertex AI API
  Future<Map<String, dynamic>?> _makeRequest(Map<String, dynamic> requestBody) async {
    try {
      final url = '$_baseUrl/v1/projects/$_projectId/locations/$_region/publishers/google/models/$_model:generateContent';

      Future<Map<String, dynamic>?> doPost() async {
        final response = await _httpClient.post(
          Uri.parse(url),
          headers: {
            'Authorization': 'Bearer $_accessToken',
            'Content-Type': 'application/json',
          },
          body: jsonEncode(requestBody),
        );
        if (response.statusCode == 200) {
          return jsonDecode(response.body) as Map<String, dynamic>;
        }
        // Handle 429 with a single retry after short backoff
        if (response.statusCode == 429) {
          print('⚠️ VertexAI: 429 RESOURCE_EXHAUSTED, retrying after backoff...');
          await Future.delayed(const Duration(seconds: 2));
          final retry = await _httpClient.post(
            Uri.parse(url),
            headers: {
              'Authorization': 'Bearer $_accessToken',
              'Content-Type': 'application/json',
            },
            body: jsonEncode(requestBody),
          );
          if (retry.statusCode == 200) {
            return jsonDecode(retry.body) as Map<String, dynamic>;
          }
          print('❌ VertexAI: Retry failed ${retry.statusCode}: ${retry.body}');
          return null;
        }
        print('❌ VertexAI: API error ${response.statusCode}: ${response.body}');
        return null;
      }

      return await doPost();
    } catch (e) {
      print('❌ VertexAI: Request failed: $e');
      return null;
    }
  }

  /// Parse API response and extract generated text
  String? _parseResponse(Map<String, dynamic> response) {
    try {
      final candidates = response['candidates'] as List<dynamic>?;
      if (candidates == null || candidates.isEmpty) {
        print('❌ VertexAI: No candidates in response');
        return null;
      }

      final content = candidates[0]['content'] as Map<String, dynamic>?;
      if (content == null) {
        print('❌ VertexAI: No content in candidate');
        return null;
      }

      final parts = content['parts'] as List<dynamic>?;
      if (parts == null || parts.isEmpty) {
        print('❌ VertexAI: No parts in content');
        return null;
      }

      final text = parts[0]['text'] as String?;
      if (text == null || text.isEmpty) {
        print('❌ VertexAI: No text in part');
        return null;
      }

      return text;
    } catch (e) {
      print('❌ VertexAI: Error parsing response: $e');
      return null;
    }
  }

  /// Ensure we have a valid access token
  Future<bool> _ensureValidToken() async {
    // Check if current token is still valid
    if (_accessToken != null && _tokenExpiry != null) {
      if (DateTime.now().isBefore(_tokenExpiry!.subtract(const Duration(minutes: 5)))) {
        return true; // Token is still valid
      }
    }

    // Generate new token
    return await _generateAccessToken();
  }

  /// Generate new access token using service account
  Future<bool> _generateAccessToken() async {
    if (_serviceAccountJson == null) {
      print('❌ VertexAI: No service account JSON');
      return false;
    }

    try {
      final serviceAccount = jsonDecode(_serviceAccountJson!);
      final privateKey = serviceAccount['private_key'] as String;
      final clientEmail = serviceAccount['client_email'] as String;
      final privateKeyId = serviceAccount['private_key_id'] as String;

      // Create JWT
      final claims = JsonWebTokenClaims.fromJson({
        'iss': clientEmail,
        'scope': 'https://www.googleapis.com/auth/cloud-platform',
        'aud': 'https://oauth2.googleapis.com/token',
        'exp': (DateTime.now().add(const Duration(hours: 1)).millisecondsSinceEpoch / 1000).round(),
        'iat': (DateTime.now().millisecondsSinceEpoch / 1000).round(),
      });

      final key = JsonWebKey.fromPem(privateKey, keyId: privateKeyId);
      final builder = JsonWebSignatureBuilder()
        ..jsonContent = claims.toJson()
        ..addRecipient(key, algorithm: 'RS256');

      final jws = builder.build();
      final jwt = jws.toCompactSerialization();

      // Exchange JWT for access token
      final response = await _httpClient.post(
        Uri.parse('https://oauth2.googleapis.com/token'),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: {
          'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
          'assertion': jwt,
        },
      );

      if (response.statusCode == 200) {
        final tokenData = jsonDecode(response.body);
        _accessToken = tokenData['access_token'];
        final expiresIn = tokenData['expires_in'] as int;
        _tokenExpiry = DateTime.now().add(Duration(seconds: expiresIn));

        print('✅ VertexAI: Access token generated successfully');
        return true;
      } else {
        print('❌ VertexAI: Token generation failed: ${response.statusCode} ${response.body}');
        return false;
      }
    } catch (e) {
      print('❌ VertexAI: Error generating token: $e');
      return false;
    }
  }

  /// Test the client connection
  Future<bool> testConnection() async {
    try {
      final response = await generateContent('Hello, respond with "Connection successful"');
      return response != null && response.contains('successful');
    } catch (e) {
      print('❌ VertexAI: Connection test failed: $e');
      return false;
    }
  }

  /// Get current configuration status
  Map<String, dynamic> getStatus() {
    return {
      'initialized': _isInitialized,
      'has_token': _accessToken != null,
      'token_expires': _tokenExpiry?.toIso8601String(),
      'project_id': _projectId,
      'region': _region,
      'model': _model,
    };
  }

  /// Dispose resources
  void dispose() {
    _httpClient.close();
    _accessToken = null;
    _tokenExpiry = null;
    _isInitialized = false;
  }
}
