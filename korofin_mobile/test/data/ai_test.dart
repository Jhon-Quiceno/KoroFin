import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/data/repositories/ai_repository.dart';
import 'package:korofin_mobile/models/ai.dart';
import 'package:korofin_mobile/models/category.dart';

import '../support/capturing_adapter.dart';

AiRepository _repo(CapturingAdapter a) => AiRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  test('chat manda el mensaje y devuelve la respuesta del asistente', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'reply': 'Gastaste 300k en comida.',
      'providerName': 'gemini',
      'model': 'gemini-2.0',
      'createdAt': '2026-09-05T10:00:00Z',
    });
    final m = await _repo(a).chat('¿Cuánto gasté en comida?');
    expect(a.lastRequest.path, '/api/ai/chat');
    expect((a.lastRequest.data as Map)['message'], '¿Cuánto gasté en comida?');
    expect(m.role, ChatRole.assistant);
    expect(m.content, 'Gastaste 300k en comida.');
  });

  test('history invierte el orden DESC del backend a ASC', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'content': <dynamic>[
        <String, dynamic>{
          'id': 2,
          'role': 'ASSISTANT',
          'content': 'segundo',
          'createdAt': '2026-09-05T10:01:00Z',
        },
        <String, dynamic>{
          'id': 1,
          'role': 'USER',
          'content': 'primero',
          'createdAt': '2026-09-05T10:00:00Z',
        },
      ],
    });
    final list = await _repo(a).history();
    expect(list.map((m) => m.content), <String>['primero', 'segundo']);
  });

  test('latestInsight devuelve null ante un 204', () async {
    final a = CapturingAdapter(status: 204);
    final insight = await _repo(a).latestInsight();
    expect(insight, isNull);
  });

  test('categorize manda description/amount/type', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'categoryId': 5,
      'categoryName': 'Comida',
    });
    final s = await _repo(a).categorize(
        description: 'McDonalds', amount: 25000, type: CategoryKind.expense);
    final body = a.lastRequest.data as Map<String, dynamic>;
    expect(body['description'], 'McDonalds');
    expect(body['amount'], 25000);
    expect(body['type'], 'EXPENSE');
    expect(s.hasSuggestion, isTrue);
    expect(s.categoryName, 'Comida');
  });

  test('providersStatus parsea la lista y anyConfigured funciona', () async {
    final a = CapturingAdapter(body: <dynamic>[
      <String, dynamic>{'name': 'gemini', 'configured': false, 'priority': 1},
      <String, dynamic>{'name': 'nvidia', 'configured': true, 'priority': 2},
    ]);
    final list = await _repo(a).providersStatus();
    expect(list, hasLength(2));
    expect(list.any((p) => p.configured), isTrue);
  });

  test('un 503 (sin proveedores) se propaga como ApiException', () async {
    final a = CapturingAdapter(
      status: 503,
      body: <String, dynamic>{'message': 'IA no disponible'},
    );
    expect(() => _repo(a).chat('hola'),
        throwsA(isA<ApiException>().having((e) => e.statusCode, 's', 503)));
  });
}
