class Task {
  final String id;
  final String title;
  final String description;
  final DateTime createdAt;
  final List<ChatMessage> messages;
  final TaskStatus status;
  final bool automationStarted;

  Task({
    required this.id,
    required this.title,
    required this.description,
    required this.createdAt,
    required this.messages,
    this.status = TaskStatus.active,
    this.automationStarted = false,
  });

  Task copyWith({
    String? id,
    String? title,
    String? description,
    DateTime? createdAt,
    List<ChatMessage>? messages,
    TaskStatus? status,
    bool? automationStarted,
  }) {
    return Task(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      createdAt: createdAt ?? this.createdAt,
      messages: messages ?? this.messages,
      status: status ?? this.status,
      automationStarted: automationStarted ?? this.automationStarted,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'createdAt': createdAt.toIso8601String(),
      'messages': messages.map((m) => m.toJson()).toList(),
      'status': status.toString(),
      'automationStarted': automationStarted,
    };
  }

  factory Task.fromJson(Map<String, dynamic> json) {
    return Task(
      id: json['id'],
      title: json['title'],
      description: json['description'],
      createdAt: DateTime.parse(json['createdAt']),
      messages: (json['messages'] as List)
          .map((m) => ChatMessage.fromJson(m))
          .toList(),
      status: TaskStatus.values.firstWhere(
        (e) => e.toString() == json['status'],
        orElse: () => TaskStatus.active,
      ),
      automationStarted: json['automationStarted'] ?? false,
    );
  }
}

enum TaskStatus {
  active,
  completed,
  paused,
}

class ChatMessage {
  final String text;
  final bool isUser;
  final bool isSystem;
  final DateTime timestamp;
  final String? jsonData; // For displaying raw JSON tool calls

  ChatMessage({
    required this.text,
    this.isUser = false,
    this.isSystem = false,
    DateTime? timestamp,
    this.jsonData,
  }) : timestamp = timestamp ?? DateTime.now();

  Map<String, dynamic> toJson() {
    return {
      'text': text,
      'isUser': isUser,
      'isSystem': isSystem,
      'timestamp': timestamp.toIso8601String(),
      'jsonData': jsonData,
    };
  }

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      text: json['text'],
      isUser: json['isUser'] ?? false,
      isSystem: json['isSystem'] ?? false,
      timestamp: DateTime.parse(json['timestamp']),
      jsonData: json['jsonData'],
    );
  }
}