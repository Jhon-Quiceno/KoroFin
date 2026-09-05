import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../../data/repositories/ai_repository.dart';
import '../../models/ai.dart';

final aiRepositoryProvider = Provider<AiRepository>(
  (ref) => AiRepository(ref.read(apiClientProvider)),
);

/// Cuota mensual de mensajes de IA (para el indicador del asistente).
final aiUsageProvider =
    FutureProvider<AiUsage>((ref) => ref.read(aiRepositoryProvider).usage());

/// Estado de los proveedores de IA. Si ninguno está `configured`, la UI avisa
/// que el asistente no está disponible.
final aiProvidersStatusProvider = FutureProvider<List<AiProviderStatus>>(
  (ref) => ref.read(aiRepositoryProvider).providersStatus(),
);

final anyAiProviderConfiguredProvider = Provider<bool>((ref) {
  return ref.watch(aiProvidersStatusProvider).maybeWhen(
        data: (list) => list.any((p) => p.configured),
        orElse: () => true, // optimista mientras carga
      );
});

/// Último insight financiero. Se regenera con `refresh()`.
final latestInsightProvider = FutureProvider<AiInsight?>(
  (ref) => ref.read(aiRepositoryProvider).latestInsight(),
);

/// Conversación con el asistente. Carga el historial y agrega el par
/// usuario/asistente en cada envío.
final assistantProvider =
    AsyncNotifierProvider<AssistantController, List<ChatMessage>>(
  AssistantController.new,
);

class AssistantController extends AsyncNotifier<List<ChatMessage>> {
  AiRepository get _repo => ref.read(aiRepositoryProvider);

  bool _sending = false;
  bool get isSending => _sending;

  @override
  Future<List<ChatMessage>> build() => _repo.history();

  List<ChatMessage> get _current => state.valueOrNull ?? const <ChatMessage>[];

  /// Devuelve `null` si salió bien, o el mensaje de error para mostrar.
  Future<String?> send(String text) async {
    final String message = text.trim();
    if (message.isEmpty || _sending) return null;
    _sending = true;
    state = AsyncValue<List<ChatMessage>>.data(<ChatMessage>[
      ..._current,
      ChatMessage(role: ChatRole.user, content: message),
    ]);
    try {
      final ChatMessage reply = await _repo.chat(message);
      state = AsyncValue<List<ChatMessage>>.data(<ChatMessage>[
        ..._current,
        reply,
      ]);
      ref.invalidate(aiUsageProvider);
      return null;
    } on ApiException catch (e) {
      return e.message;
    } finally {
      _sending = false;
    }
  }

  Future<void> refresh() async {
    state = const AsyncValue<List<ChatMessage>>.loading();
    state = await AsyncValue.guard(_repo.history);
  }
}
