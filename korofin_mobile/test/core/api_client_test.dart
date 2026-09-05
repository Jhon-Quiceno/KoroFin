import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';

/// Adaptador de HTTP falso: registra la última request y responde con un
/// estado/cuerpo fijos, o lanza un [DioException] de transporte.
class _CapturingAdapter implements HttpClientAdapter {
  _CapturingAdapter({this.status = 200, this.body, this.throwType});

  final int status;
  final Object? body;
  final DioExceptionType? throwType;

  RequestOptions? lastRequest;
  int calls = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    lastRequest = options;
    calls++;
    if (throwType != null) {
      throw DioException(requestOptions: options, type: throwType!);
    }
    return ResponseBody.fromString(
      jsonEncode(body ?? <String, dynamic>{}),
      status,
      headers: <String, List<String>>{
        Headers.contentTypeHeader: <String>[Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

ApiClient _clientWith(_CapturingAdapter adapter, {String? token}) {
  final Dio dio = Dio();
  dio.httpClientAdapter = adapter;
  return ApiClient(readAccessToken: () => token, dio: dio);
}

void main() {
  test('agrega Authorization: Bearer cuando hay access token', () async {
    final adapter = _CapturingAdapter(body: <String, dynamic>{'ok': true});
    final client = _clientWith(adapter, token: 'token-123');

    await client.get('/api/categories');

    expect(adapter.lastRequest!.headers['Authorization'], 'Bearer token-123');
  });

  test('omite Authorization cuando no hay sesión', () async {
    final adapter = _CapturingAdapter(body: <String, dynamic>{'ok': true});
    final client = _clientWith(adapter, token: null);

    await client.get('/api/categories');

    expect(adapter.lastRequest!.headers.containsKey('Authorization'), isFalse);
  });

  test('un 404 con message del backend se traduce a ApiException', () async {
    final adapter = _CapturingAdapter(
      status: 404,
      body: <String, dynamic>{'status': 404, 'message': 'Deuda no encontrada'},
    );
    final client = _clientWith(adapter, token: 'x');

    expect(
      () => client.get('/api/debts/999'),
      throwsA(
        isA<ApiException>()
            .having((e) => e.statusCode, 'statusCode', 404)
            .having((e) => e.message, 'message', 'Deuda no encontrada'),
      ),
    );
  });

  test('un error de conexión se traduce a ApiException de red', () async {
    final adapter = _CapturingAdapter(throwType: DioExceptionType.connectionError);
    final client = _clientWith(adapter, token: 'x');

    expect(
      () => client.get('/api/categories'),
      throwsA(
        isA<ApiException>().having((e) => e.isNetworkError, 'isNetworkError', isTrue),
      ),
    );
  });

  test('sin onRefresh cableado, un 401 se propaga como ApiException', () async {
    final adapter = _CapturingAdapter(
      status: 401,
      body: <String, dynamic>{'status': 401, 'message': 'Token vencido'},
    );
    final client = _clientWith(adapter, token: 'viejo');

    expect(
      () => client.get('/api/expenses'),
      throwsA(isA<ApiException>().having((e) => e.isUnauthorized, 'isUnauthorized', isTrue)),
    );
  });

  test('con onRefresh, un 401 dispara un refresh y reintenta la request', () async {
    final adapter = _CapturingAdapter(
      status: 401,
      body: <String, dynamic>{'message': 'Token vencido'},
    );
    final Dio dio = Dio()..httpClientAdapter = adapter;
    String? currentToken = 'viejo';
    final client = ApiClient(readAccessToken: () => currentToken, dio: dio);

    var refreshCalls = 0;
    client.onRefresh = () async {
      refreshCalls++;
      currentToken = 'token-nuevo'; // igual que hará el controlador de sesión
      return currentToken;
    };

    // El adaptador sigue devolviendo 401, así que tras el reintento vuelve a
    // fallar — lo que se verifica es que el refresh se llamó UNA vez y que la
    // request se reintentó (2 llamadas al adaptador en total).
    await expectLater(
      () => client.get('/api/expenses'),
      throwsA(isA<ApiException>()),
    );

    expect(refreshCalls, 1);
    expect(adapter.calls, 2);
    expect(adapter.lastRequest!.headers['Authorization'], 'Bearer token-nuevo');
  });

  test('si el refresh devuelve null, se llama a onSessionExpired', () async {
    final adapter = _CapturingAdapter(
      status: 401,
      body: <String, dynamic>{'message': 'Token vencido'},
    );
    final client = _clientWith(adapter, token: 'viejo');

    var expired = false;
    client.onRefresh = () async => null;
    client.onSessionExpired = () => expired = true;

    await expectLater(() => client.get('/api/expenses'), throwsA(isA<ApiException>()));

    expect(expired, isTrue);
  });
}
