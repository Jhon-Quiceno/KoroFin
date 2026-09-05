import '../../core/network/api_client.dart';
import '../../models/ai.dart';
import '../../models/category.dart';

/// Acceso a `/api/ai/*` (chat, cuota, insights, categorización, proveedores).
class AiRepository {
  AiRepository(this._client);

  final ApiClient _client;

  Future<ChatMessage> chat(String message) async {
    final response =
        await _client.post('/api/ai/chat', body: <String, dynamic>{
      'message': message,
    });
    return ChatMessage.assistantReply(response.data as Map<String, dynamic>);
  }

  Future<List<ChatMessage>> history({int page = 0, int size = 50}) async {
    final response = await _client.get(
      '/api/ai/chat/history',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    final List<dynamic> content =
        (response.data as Map<String, dynamic>)['content'] as List<dynamic>? ??
            const <dynamic>[];
    // El backend lo entrega DESC; se invierte para mostrar el más viejo arriba.
    return content
        .map((e) => ChatMessage.fromHistoryJson(e as Map<String, dynamic>))
        .toList()
        .reversed
        .toList(growable: false);
  }

  Future<AiUsage> usage() async {
    final response = await _client.get('/api/ai/chat/usage');
    return AiUsage.fromJson(response.data as Map<String, dynamic>);
  }

  /// `null` si todavía no hay ningún insight (el backend responde 204).
  Future<AiInsight?> latestInsight() async {
    final response = await _client.get('/api/ai/insights');
    if (response.statusCode == 204 || response.data == null) return null;
    return AiInsight.fromJson(response.data as Map<String, dynamic>);
  }

  Future<AiInsight> generateInsight() async {
    final response = await _client.post('/api/ai/insights/generate');
    return AiInsight.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CategorySuggestion> categorize({
    required String description,
    double? amount,
    CategoryKind type = CategoryKind.expense,
  }) async {
    final response =
        await _client.post('/api/ai/categorize', body: <String, dynamic>{
      'description': description,
      'amount': ?amount,
      'type': type.wire,
    });
    return CategorySuggestion.fromJson(response.data as Map<String, dynamic>);
  }

  /// Escanea un recibo. `imageDataUri` debe ser un data URI
  /// (`data:image/jpeg;base64,...`).
  Future<ReceiptExtraction> scanReceipt(String imageDataUri) async {
    final response = await _client.post(
      '/api/receipts/scan',
      body: <String, dynamic>{'imageDataUri': imageDataUri},
    );
    return ReceiptExtraction.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<AiProviderStatus>> providersStatus() async {
    final response = await _client.get('/api/ai/providers/status');
    return (response.data as List<dynamic>)
        .map((e) => AiProviderStatus.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }
}
