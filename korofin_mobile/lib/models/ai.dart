/// Rol de un mensaje del chat de IA. En el backend es `AiMessageRole`.
enum ChatRole { user, assistant }

/// Un mensaje del chat con el asistente: `ChatMessageResponse` (historial) o
/// derivado de `ChatReplyResponse` (respuesta recién generada).
class ChatMessage {
  const ChatMessage({
    required this.role,
    required this.content,
    this.id,
    this.createdAt,
    this.providerName,
  });

  final int? id;
  final ChatRole role;
  final String content;
  final DateTime? createdAt;
  final String? providerName;

  bool get isUser => role == ChatRole.user;

  factory ChatMessage.fromHistoryJson(Map<String, dynamic> json) => ChatMessage(
        id: (json['id'] as num?)?.toInt(),
        role: (json['role'] as String?) == 'USER'
            ? ChatRole.user
            : ChatRole.assistant,
        content: json['content'] as String? ?? '',
        providerName: json['providerName'] as String?,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );

  factory ChatMessage.assistantReply(Map<String, dynamic> json) => ChatMessage(
        role: ChatRole.assistant,
        content: json['reply'] as String? ?? '',
        providerName: json['providerName'] as String?,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

/// Cuota mensual de mensajes de IA: `GET /api/ai/chat/usage`.
class AiUsage {
  const AiUsage({
    required this.used,
    required this.limit,
    required this.remaining,
  });

  final int used;
  final int limit;
  final int remaining;

  factory AiUsage.fromJson(Map<String, dynamic> json) => AiUsage(
        used: (json['used'] as num?)?.toInt() ?? 0,
        limit: (json['limit'] as num?)?.toInt() ?? 0,
        remaining: (json['remaining'] as num?)?.toInt() ?? 0,
      );
}

/// Un insight financiero generado por IA: `GET/POST /api/ai/insights`.
class AiInsight {
  const AiInsight({required this.content, this.providerName, this.createdAt});

  final String content;
  final String? providerName;
  final DateTime? createdAt;

  factory AiInsight.fromJson(Map<String, dynamic> json) => AiInsight(
        content: json['content'] as String? ?? '',
        providerName: json['providerName'] as String?,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

/// Sugerencia de categoría de `POST /api/ai/categorize`. Ambos campos pueden
/// venir `null` si la IA no pudo clasificar.
class CategorySuggestion {
  const CategorySuggestion({this.categoryId, this.categoryName});

  final int? categoryId;
  final String? categoryName;

  bool get hasSuggestion => categoryId != null;

  factory CategorySuggestion.fromJson(Map<String, dynamic> json) =>
      CategorySuggestion(
        categoryId: (json['categoryId'] as num?)?.toInt(),
        categoryName: json['categoryName'] as String?,
      );
}

/// Resultado de escanear un recibo: `POST /api/receipts/scan`.
/// `isReceipt` es `false` cuando la imagen no parece un recibo (no es error).
class ReceiptExtraction {
  const ReceiptExtraction({
    required this.isReceipt,
    this.description,
    this.amount,
    this.isIncome = false,
    this.categoryId,
    this.categoryName,
  });

  final bool isReceipt;
  final String? description;
  final double? amount;
  final bool isIncome;
  final int? categoryId;
  final String? categoryName;

  factory ReceiptExtraction.fromJson(Map<String, dynamic> json) =>
      ReceiptExtraction(
        isReceipt: json['isReceipt'] as bool? ?? false,
        description: json['description'] as String?,
        amount: (json['amount'] as num?)?.toDouble(),
        isIncome: (json['movementType'] as String?) == 'INCOME',
        categoryId: (json['categoryId'] as num?)?.toInt(),
        categoryName: json['categoryName'] as String?,
      );
}

/// Estado de un proveedor de IA: `GET /api/ai/providers/status`.
class AiProviderStatus {
  const AiProviderStatus({
    required this.name,
    required this.configured,
    this.priority,
  });

  final String name;
  final bool configured;
  final int? priority;

  factory AiProviderStatus.fromJson(Map<String, dynamic> json) =>
      AiProviderStatus(
        name: json['name'] as String? ?? '',
        configured: json['configured'] as bool? ?? false,
        priority: (json['priority'] as num?)?.toInt(),
      );
}
