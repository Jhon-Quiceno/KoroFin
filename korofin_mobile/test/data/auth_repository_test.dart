import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/data/repositories/auth_repository.dart';

import '../support/capturing_adapter.dart';

Map<String, dynamic> _authResponse({String access = 'a', String refresh = 'r'}) {
  return <String, dynamic>{
    'accessToken': access,
    'tokenType': 'Bearer',
    'expiresIn': 900,
    'refreshToken': refresh,
    'user': <String, dynamic>{
      'id': 7,
      'name': 'Vale',
      'email': 'vale@korofin.local',
      'theme': 'DARK',
      'currency': 'COP',
      'language': 'ES',
    },
  };
}

AuthRepository _repoWith(CapturingAdapter adapter) {
  final Dio dio = Dio()..httpClientAdapter = adapter;
  return AuthRepository(ApiClient(readAccessToken: () => null, dio: dio));
}

void main() {
  test('login manda email/password/rememberMe y parsea la sesión', () async {
    final adapter = CapturingAdapter(body: _authResponse(access: 'tok'));
    final repo = _repoWith(adapter);

    final session =
        await repo.login(email: 'vale@korofin.local', password: 'secret12');

    expect(adapter.lastRequest.path, '/api/users/login');
    final body = adapter.lastRequest.data as Map<String, dynamic>;
    expect(body['email'], 'vale@korofin.local');
    expect(body['password'], 'secret12');
    expect(body['rememberMe'], true);
    expect(session.accessToken, 'tok');
    expect(session.user.id, 7);
  });

  test('register manda name/email/password', () async {
    final adapter = CapturingAdapter(body: _authResponse());
    final repo = _repoWith(adapter);

    await repo.register(
        name: 'Vale', email: 'vale@korofin.local', password: 'secret12');

    expect(adapter.lastRequest.path, '/api/users/register');
    final body = adapter.lastRequest.data as Map<String, dynamic>;
    expect(body['name'], 'Vale');
    expect(body.containsKey('rememberMe'), isFalse);
  });

  test('refresh manda el refreshToken en el body', () async {
    final adapter =
        CapturingAdapter(body: _authResponse(access: 'nuevo', refresh: 'r2'));
    final repo = _repoWith(adapter);

    final session = await repo.refresh('r1');

    expect(adapter.lastRequest.path, '/api/users/refresh');
    expect((adapter.lastRequest.data as Map)['refreshToken'], 'r1');
    expect(session.accessToken, 'nuevo');
    expect(session.refreshToken, 'r2');
  });

  test('un 401 en login se propaga como ApiException', () async {
    final adapter = CapturingAdapter(
      status: 401,
      body: <String, dynamic>{'message': 'Credenciales inválidas'},
    );
    final repo = _repoWith(adapter);

    expect(
      () => repo.login(email: 'x@x.com', password: 'mala'),
      throwsA(isA<ApiException>()
          .having((e) => e.message, 'message', 'Credenciales inválidas')),
    );
  });
}
