import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/data/repositories/ai_repository.dart';
import 'package:korofin_mobile/models/ai.dart';
import 'package:korofin_mobile/models/category.dart';
import 'package:korofin_mobile/state/ai/ai_controller.dart';

class _FakeAiRepository implements AiRepository {
  _FakeAiRepository({this.chatError});

  final String? chatError;
  final List<ChatMessage> historyStore = <ChatMessage>[
    const ChatMessage(role: ChatRole.assistant, content: 'Hola, ¿en qué ayudo?'),
  ];

  @override
  Future<List<ChatMessage>> history({int page = 0, int size = 50}) async =>
      List<ChatMessage>.of(historyStore);

  @override
  Future<ChatMessage> chat(String message) async {
    if (chatError != null) {
      throw ApiException(message: chatError!, statusCode: 429);
    }
    return ChatMessage(role: ChatRole.assistant, content: 'Respuesta a: $message');
  }

  @override
  Future<AiUsage> usage() async =>
      const AiUsage(used: 1, limit: 5, remaining: 4);

  @override
  Future<List<AiProviderStatus>> providersStatus() async => const [
        AiProviderStatus(name: 'gemini', configured: true, priority: 1),
      ];

  @override
  Future<CategorySuggestion> categorize({
    required String description,
    double? amount,
    CategoryKind type = CategoryKind.expense,
  }) async =>
      const CategorySuggestion(categoryId: 3, categoryName: 'Comida');

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

ProviderContainer _container(AiRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    aiRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build carga el historial', () async {
    final c = _container(_FakeAiRepository());
    final list = await c.read(assistantProvider.future);
    expect(list, hasLength(1));
  });

  test('send agrega el par usuario/asistente', () async {
    final c = _container(_FakeAiRepository());
    await c.read(assistantProvider.future);

    final error =
        await c.read(assistantProvider.notifier).send('¿Cómo voy?');

    expect(error, isNull);
    final list = c.read(assistantProvider).requireValue;
    expect(list.length, 3);
    expect(list[1].role, ChatRole.user);
    expect(list[2].role, ChatRole.assistant);
  });

  test('send devuelve el mensaje de error ante un 429 y deja el mensaje del usuario',
      () async {
    final c = _container(_FakeAiRepository(chatError: 'Alcanzaste tu cuota'));
    await c.read(assistantProvider.future);

    final error =
        await c.read(assistantProvider.notifier).send('otra pregunta');

    expect(error, 'Alcanzaste tu cuota');
    final list = c.read(assistantProvider).requireValue;
    expect(list.last.content, 'otra pregunta');
  });

  test('anyAiProviderConfiguredProvider refleja el estado', () async {
    final c = _container(_FakeAiRepository());
    await c.read(aiProvidersStatusProvider.future);
    expect(c.read(anyAiProviderConfiguredProvider), isTrue);
  });
}
