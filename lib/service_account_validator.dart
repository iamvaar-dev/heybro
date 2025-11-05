import 'dart:convert';

/// Utility class for validating Google Cloud Service Account JSON files
class ServiceAccountValidator {
  /// Required fields that must be present in a valid service account JSON
  static const List<String> requiredFields = [
    'type',
    'project_id',
    'private_key_id',
    'private_key',
    'client_email',
    'client_id',
    'auth_uri',
    'token_uri',
  ];

  /// Validates a service account JSON string and returns validation result
  static ServiceAccountValidationResult validateServiceAccount(String json) {
    if (json.trim().isEmpty) {
      return ServiceAccountValidationResult.error(
          'Service account JSON cannot be empty');
    }

    Map<String, dynamic> decoded;
    try {
      decoded = jsonDecode(json);
    } catch (e) {
      return ServiceAccountValidationResult.error(
          'Invalid JSON format: ${e.toString()}');
    }

    // Check if it's a service account
    if (decoded['type'] != 'service_account') {
      return ServiceAccountValidationResult.error(
          'Not a service account. The "type" field must be "service_account"');
    }

    // Check for required fields
    final missingFields = <String>[];
    for (final field in requiredFields) {
      if (!decoded.containsKey(field) ||
          decoded[field] == null ||
          decoded[field].toString().trim().isEmpty) {
        missingFields.add(field);
      }
    }

    if (missingFields.isNotEmpty) {
      return ServiceAccountValidationResult.error(
          'Missing required fields: ${missingFields.join(', ')}');
    }

    // Validate project ID format
    final projectId = decoded['project_id'].toString();
    if (!isValidProjectIdFormat(projectId)) {
      return ServiceAccountValidationResult.error(
          'Invalid project_id format: "$projectId". Must be 6-30 characters, lowercase letters, numbers, and hyphens only.');
    }

    // Validate client email format
    final clientEmail = decoded['client_email'].toString();
    if (!clientEmail.contains('@') || !clientEmail.contains('.')) {
      return ServiceAccountValidationResult.error(
          'Invalid client_email format: "$clientEmail"');
    }

    // Validate private key format
    final privateKey = decoded['private_key'].toString();
    if (!privateKey.contains('BEGIN PRIVATE KEY') ||
        !privateKey.contains('END PRIVATE KEY')) {
      return ServiceAccountValidationResult.error(
          'Invalid private_key format. Must be a PEM-formatted private key.');
    }

    return ServiceAccountValidationResult.success(
      projectId: projectId,
      clientEmail: clientEmail,
      privateKeyId: decoded['private_key_id'].toString(),
    );
  }

  /// Extracts project ID from service account JSON
  static String? extractProjectId(String json) {
    try {
      final decoded = jsonDecode(json);
      if (decoded is Map<String, dynamic> &&
          decoded.containsKey('project_id')) {
        final projectId = decoded['project_id']?.toString();
        return projectId?.isNotEmpty == true ? projectId : null;
      }
    } catch (e) {
      // Ignore JSON parsing errors
    }
    return null;
  }

  /// Validates Google Cloud project ID format
  static bool isValidProjectIdFormat(String projectId) {
    if (projectId.isEmpty || projectId.length < 6 || projectId.length > 30) {
      return false;
    }

    // Must contain only lowercase letters, numbers, and hyphens
    final regex = RegExp(r'^[a-z0-9\-]+$');
    if (!regex.hasMatch(projectId)) {
      return false;
    }

    // Cannot start or end with a hyphen
    if (projectId.startsWith('-') || projectId.endsWith('-')) {
      return false;
    }

    // Cannot contain consecutive hyphens
    if (projectId.contains('--')) {
      return false;
    }

    return true;
  }

  /// Gets a human-readable summary of the service account
  static String getServiceAccountSummary(String json) {
    try {
      final validation = validateServiceAccount(json);
      if (validation.isValid) {
        return 'Valid service account for project: ${validation.projectId}\n'
            'Client: ${validation.clientEmail}\n'
            'Key ID: ${validation.privateKeyId?.substring(0, 8)}...';
      } else {
        return 'Invalid service account: ${validation.error}';
      }
    } catch (e) {
      return 'Error reading service account: $e';
    }
  }

  /// Checks if the service account has the necessary scopes for Vertex AI
  static bool hasVertexAIScopes(String json) {
    // This is a basic check - in practice, scopes are granted at the API level
    // not embedded in the service account JSON
    try {
      final decoded = jsonDecode(json);
      final clientEmail = decoded['client_email']?.toString() ?? '';

      // Service accounts ending with .iam.gserviceaccount.com typically have
      // the ability to be granted Vertex AI scopes
      return clientEmail.endsWith('.iam.gserviceaccount.com');
    } catch (e) {
      return false;
    }
  }
}

/// Result of service account validation
class ServiceAccountValidationResult {
  final bool isValid;
  final String? error;
  final String? projectId;
  final String? clientEmail;
  final String? privateKeyId;

  const ServiceAccountValidationResult._({
    required this.isValid,
    this.error,
    this.projectId,
    this.clientEmail,
    this.privateKeyId,
  });

  factory ServiceAccountValidationResult.success({
    required String projectId,
    required String clientEmail,
    required String privateKeyId,
  }) {
    return ServiceAccountValidationResult._(
      isValid: true,
      projectId: projectId,
      clientEmail: clientEmail,
      privateKeyId: privateKeyId,
    );
  }

  factory ServiceAccountValidationResult.error(String error) {
    return ServiceAccountValidationResult._(
      isValid: false,
      error: error,
    );
  }

  @override
  String toString() {
    if (isValid) {
      return 'Valid service account for project: $projectId';
    } else {
      return 'Invalid service account: $error';
    }
  }
}
