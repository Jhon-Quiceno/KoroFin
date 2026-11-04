import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/config/app_config.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';

import '../support/capturing_adapter.dart';

ApiClient _clientWith(CapturingAdapter adapter, {String? token}) {
  final Dio dio = Dio()..httpClientAdapter = adapter;
  return ApiClient(readAccessToken: () => token, dio: dio);
}

void main() {
  test('agrega Authorization: Bearer cuando hay access token', () async {
    final adapter = CapturingAdapter(body: <String, dynamic>{'ok': true});
    final client = _clientWith(adapter, token: 'token-123');

    await client.get('/api/categories');

    expect(adapter.lastRequest.headers['Authorization'], 'Bearer token-123');
  });

  test('omite Authorization cuando no hay sesión', () async {
    final adapter = CapturingAdapter(body: <String, dynamic>{'ok': true});
    final client = _clientWith(adapter, token: null);

    await client.get('/api/categories');

    expect(adapter.lastRequest.headers.containsKey('Authorization'), isFalse);
  });

  test('un 404 con message del backend se traduce a ApiException', () async {
    final adapter = CapturingAdapter(
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
    final adapter =
        CapturingAdapter(throwType: DioExceptionType.connectionError);
    final client = _clientWith(adapter, token: 'x');

    expect(
      () => client.get('/api/categories'),
      throwsA(
        isA<ApiException>()
            .having((e) => e.isNetworkError, 'isNetworkError', isTrue),
      ),
    );
  });

  test('sin onRefresh cableado, un 401 se propaga como ApiException', () async {
    final adapter = CapturingAdapter(
      status: 401,
      body: <String, dynamic>{'status': 401, 'message': 'Token vencido'},
    );
    final client = _clientWith(adapter, token: 'viejo');

    expect(
      () => client.get('/api/expenses'),
      throwsA(isA<ApiException>()
          .having((e) => e.isUnauthorized, 'isUnauthorized', isTrue)),
    );
  });

  test('con onRefresh, un 401 dispara un refresh y reintenta con el token nuevo',
      () async {
    final adapter = CapturingAdapter()
      ..queue.addAll(<Map<String, dynamic>>[
        <String, dynamic>{'status': 401, 'body': {'message': 'Token vencido'}},
        <String, dynamic>{'status': 200, 'body': {'ok': true}},
      ]);
    final Dio dio = Dio()..httpClientAdapter = adapter;
    String? currentToken = 'viejo';
    final client = ApiClient(readAccessToken: () => currentToken, dio: dio);

    var refreshCalls = 0;
    client.onRefresh = () async {
      refreshCalls++;
      currentToken = 'token-nuevo'; // igual que hará el controlador de sesión
      return currentToken;
    };

    final response = await client.get('/api/expenses');

    expect(refreshCalls, 1);
    expect(adapter.calls, 2);
    expect(adapter.lastRequest.headers['Authorization'], 'Bearer token-nuevo');
    expect((response.data as Map)['ok'], isTrue);
  });

  test('si el refresh devuelve null, se llama a onSessionExpired', () async {
    final adapter = CapturingAdapter(
      status: 401,
      body: <String, dynamic>{'message': 'Token vencido'},
    );
    final client = _clientWith(adapter, token: 'viejo');

    var expired = false;
    client.onRefresh = () async => null;
    client.onSessionExpired = () => expired = true;

    await expectLater(
        () => client.get('/api/expenses'), throwsA(isA<ApiException>()));

    expect(expired, isTrue);
  });

  group('certificate pinning', () {
    test('con PINNED_CERT_SHA256 vacío no se toca el httpClientAdapter',
        () async {
      final adapter = CapturingAdapter(body: <String, dynamic>{'ok': true});
      final Dio dio = Dio()..httpClientAdapter = adapter;

      ApiClient(readAccessToken: () => null, dio: dio, pinnedCertSha256: '');

      expect(dio.httpClientAdapter, same(adapter));
    });

    test('sin pasar pinnedCertSha256 tampoco se toca (default vacío en tests)',
        () async {
      final adapter = CapturingAdapter(body: <String, dynamic>{'ok': true});
      final Dio dio = Dio()..httpClientAdapter = adapter;

      ApiClient(readAccessToken: () => null, dio: dio);

      expect(dio.httpClientAdapter, same(adapter));
    });

    test(
        'con PINNED_CERT_SHA256 configurado, arma un IOHttpClientAdapter que '
        'valida el certificado hoja contra el hash pineado', () {
      final client = ApiClient(
        readAccessToken: () => null,
        pinnedCertSha256: 'a' * 64,
      );

      final adapter = client.dio.httpClientAdapter;
      expect(adapter, isA<IOHttpClientAdapter>());

      final validate = (adapter as IOHttpClientAdapter).validateCertificate;
      expect(validate, isNotNull);
      // Sin certificado (null) se rechaza por defecto: fail-closed.
      expect(validate!(null, 'api.korofin.com', 443), isFalse);
    });
  });

  group('despertar el backend tras un timeout (cold start del plan Free)', () {
    test('un GET que expira reintenta tras confirmar el health y devuelve los '
        'datos', () async {
      final adapter = CapturingAdapter();
      adapter.queue
        // 1) el GET original expira: el backend está arrancando.
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        // 2) el sondeo de /actuator/health espera el arranque y lo confirma.
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}})
        // 3) el reintento del GET original ya encuentra el backend despierto.
        ..add(<String, dynamic>{'body': <String, dynamic>{'total': 42}});
      final client = _clientWith(adapter, token: 'x');

      final Response<dynamic> response = await client.get('/api/expenses');

      expect(response.data, <String, dynamic>{'total': 42});
      expect(adapter.calls, 3);
      expect(adapter.requests[1].path, '/actuator/health');
      expect(adapter.requests[2].path, '/api/expenses');
    });

    test('el sondeo de health usa el timeout largo de cold start, no el normal',
        () async {
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}})
        ..add(<String, dynamic>{'body': <String, dynamic>{'ok': true}});
      final client = _clientWith(adapter, token: 'x');

      await client.get('/api/expenses');

      expect(adapter.requests[1].receiveTimeout, AppConfig.coldStartTimeout);
      expect(adapter.requests[1].receiveTimeout!.inSeconds,
          greaterThan(AppConfig.requestTimeout.inSeconds));
    });

    test('un POST que falla al CONECTAR no se marca ambiguo: nunca salió',
        () async {
      // La cola offline usa `ambiguousAfterSend` para decidir entre
      // reintentar sola y retener la entrada esperando confirmación manual.
      // Un fallo de conexión significa que el pedido no salió, así que
      // marcarlo ambiguo dejaría un movimiento parado hasta que alguien lo
      // toque a mano.
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.connectionError})
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}});
      final client = _clientWith(adapter, token: 'x');

      await expectLater(
        () => client.post('/api/expenses', body: <String, dynamic>{'amount': 1}),
        throwsA(isA<ApiException>()
            .having((e) => e.isNetworkError, 'isNetworkError', isTrue)
            .having((e) => e.ambiguousAfterSend, 'ambiguousAfterSend', isFalse)),
      );
      expect(adapter.calls, 2);
    });

    test('un POST que expira por recepción sí se marca ambiguo', () async {
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}});
      final client = _clientWith(adapter, token: 'x');

      await expectLater(
        () => client.post('/api/expenses', body: <String, dynamic>{'amount': 1}),
        throwsA(isA<ApiException>()
            .having((e) => e.ambiguousAfterSend, 'ambiguousAfterSend', isTrue)),
      );
    });

    test('varias requests en paralelo comparten un solo sondeo de health',
        () async {
      // Sin single-flight, N requests contra un backend dormido abrirían N
      // sondeos de 300 s compitiendo por el único décimo de CPU que el
      // contenedor necesita para arrancar.
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}})
        ..add(<String, dynamic>{'body': <String, dynamic>{'a': 1}})
        ..add(<String, dynamic>{'body': <String, dynamic>{'b': 2}});
      final client = _clientWith(adapter, token: 'x');

      await Future.wait<Response<dynamic>>(<Future<Response<dynamic>>>[
        client.get('/api/expenses'),
        client.get('/api/incomes'),
      ]);

      final int healthCalls = adapter.requests
          .where((r) => r.path == '/actuator/health')
          .length;
      expect(healthCalls, 1);
    });

    test('un POST que expira NO se reintenta solo: avisa que ya puede reintentar',
        () async {
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}});
      final client = _clientWith(adapter, token: 'x');

      await expectLater(
        () => client.post('/api/expenses', body: <String, dynamic>{'amount': 1}),
        throwsA(isA<ApiException>()
            .having((e) => e.isNetworkError, 'isNetworkError', isTrue)
            .having((e) => e.message, 'message', contains('despertando'))),
      );
      // Solo el POST original y el health: el POST jamás se repite solo, para
      // no duplicar un gasto que el backend sí pudo haber alcanzado a guardar.
      expect(adapter.calls, 2);
      expect(adapter.requests[1].path, '/actuator/health');
    });

    test('si el health tampoco responde, el error de red se propaga tal cual',
        () async {
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'throwType': DioExceptionType.connectionError});
      final client = _clientWith(adapter, token: 'x');

      await expectLater(
        () => client.get('/api/expenses'),
        throwsA(isA<ApiException>()
            .having((e) => e.isNetworkError, 'isNetworkError', isTrue)),
      );
      expect(adapter.calls, 2);
    });

    test('el sondeo de despertar se intenta una sola vez por request', () async {
      final adapter = CapturingAdapter();
      adapter.queue
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout})
        ..add(<String, dynamic>{'body': <String, dynamic>{'status': 'UP'}})
        // El reintento vuelve a expirar: no se dispara otro ciclo de despertar.
        ..add(<String, dynamic>{'throwType': DioExceptionType.receiveTimeout});
      final client = _clientWith(adapter, token: 'x');

      await expectLater(
        () => client.get('/api/expenses'),
        throwsA(isA<ApiException>()),
      );
      expect(adapter.calls, 3);
    });

    test('un 404 del backend no dispara ningún sondeo de despertar', () async {
      final adapter = CapturingAdapter(
        status: 404,
        body: <String, dynamic>{'status': 404, 'message': 'No encontrado'},
      );
      final client = _clientWith(adapter, token: 'x');

      await expectLater(
        () => client.get('/api/debts/999'),
        throwsA(isA<ApiException>()),
      );
      expect(adapter.calls, 1);
    });

    test('warmUp sondea el health con el timeout largo y no lanza si falla',
        () async {
      final adapter =
          CapturingAdapter(throwType: DioExceptionType.connectionError);
      final client = _clientWith(adapter, token: null);

      await expectLater(client.warmUp(), completes);

      expect(adapter.calls, 1);
      expect(adapter.lastRequest.path, '/actuator/health');
      expect(adapter.lastRequest.receiveTimeout, AppConfig.coldStartTimeout);
    });
  });
}
