import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/core/providers.dart';
import 'package:korofin_mobile/core/storage/session_store.dart';
import 'package:korofin_mobile/data/repositories/auth_repository.dart';
import 'package:korofin_mobile/models/auth_session.dart';
import 'package:korofin_mobile/models/user.dart';
import 'package:korofin_mobile/state/auth/auth_controller.dart';
import 'package:korofin_mobile/state/auth/auth_state.dart';

import '../support/capturing_adapter.dart';

User _user() => const User(
      id: 1,
      name: 'Vale',
      email: 'vale@korofin.local',
      theme: 'SYSTEM',
      currency: 'COP',
      language: 'ES',
    );

AuthSession _session({String access = 'A1', String refresh = 'R2'}) =>
    AuthSession(
      accessToken: access,
      refreshToken: refresh,
      expiresIn: 900,
      user: _user(),
    );

class _FakeAuthRepository implements AuthRepository {
  _FakeAuthRepository({this.failRefresh = false});

  bool failRefresh;
  int refreshCalls = 0;
  int logoutCalls = 0;
  String? lastRefreshTokenSeen;

  @override
  Future<AuthSession> login({
    required String email,
    required String password,
  }) async =>
      _session(access: 'LOGIN', refresh: 'LOGIN_R');

  @override
  Future<AuthSession> register({
    required String name,
    required String email,
    required String password,
  }) async =>
      _session(access: 'REG', refresh: 'REG_R');

  @override
  Future<AuthSession> refresh(String refreshToken) async {
    refreshCalls++;
    lastRefreshTokenSeen = refreshToken;
    if (failRefresh) {
      throw const ApiException(message: 'refresh token inválido', statusCode: 401);
    }
    return _session(access: 'REFRESHED', refresh: 'ROTATED_R');
  }

  @override
  Future<void> logout(String refreshToken) async {
    logoutCalls++;
  }
}

/// Adaptador que acepta la request y nunca responde. Sirve para distinguir un
/// `await` de un `unawaited`: lo primero cuelga para siempre, lo segundo no.
class HangingAdapter extends CapturingAdapter {
  final Completer<ResponseBody> _never = Completer<ResponseBody>();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    requests.add(options);
    return _never.future;
  }
}

ProviderContainer _container({
  required SessionStore store,
  required AuthRepository repo,
  CapturingAdapter? adapter,
}) {
  // Siempre se inyecta un adaptador falso: el bootstrap sondea `/actuator/health`
  // para despertar el backend, y sin esto cada test saldría a la red de verdad.
  final CapturingAdapter httpAdapter =
      adapter ?? CapturingAdapter(body: <String, dynamic>{'status': 'UP'});
  final container = ProviderContainer(
    overrides: <Override>[
      sessionStoreProvider.overrideWithValue(store),
      authRepositoryProvider.overrideWithValue(repo),
      apiClientProvider.overrideWithValue(
        ApiClient(
          readAccessToken: () => null,
          dio: Dio()..httpClientAdapter = httpAdapter,
        ),
      ),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

void main() {
  test('sin refresh token guardado, el bootstrap deja la sesión no autenticada',
      () async {
    final store = InMemorySessionStore();
    final container = _container(store: store, repo: _FakeAuthRepository());

    expect(container.read(authControllerProvider).status, AuthStatus.unknown);
    await container.read(authControllerProvider.notifier).ready;

    expect(
        container.read(authControllerProvider).status, AuthStatus.unauthenticated);
  });

  test('con refresh token válido, el bootstrap autentica y guarda la sesión',
      () async {
    final store = InMemorySessionStore()..saveRefreshToken('R1');
    final repo = _FakeAuthRepository();
    final container = _container(store: store, repo: repo);

    await container.read(authControllerProvider.notifier).ready;

    final state = container.read(authControllerProvider);
    expect(state.status, AuthStatus.authenticated);
    expect(state.user?.email, 'vale@korofin.local');
    expect(container.read(accessTokenProvider), 'REFRESHED');
    expect(await store.readRefreshToken(), 'ROTATED_R'); // token rotado guardado
    expect(repo.lastRefreshTokenSeen, 'R1');
  });

  test('con refresh token inválido, el bootstrap limpia y va a no autenticado',
      () async {
    final store = InMemorySessionStore()..saveRefreshToken('viejo');
    final container = _container(
      store: store,
      repo: _FakeAuthRepository(failRefresh: true),
    );

    await container.read(authControllerProvider.notifier).ready;

    expect(
        container.read(authControllerProvider).status, AuthStatus.unauthenticated);
    expect(container.read(accessTokenProvider), isNull);
    expect(await store.readRefreshToken(), isNull);
  });

  test('login autentica y persiste el access + refresh token', () async {
    final store = InMemorySessionStore();
    final container = _container(store: store, repo: _FakeAuthRepository());
    await container.read(authControllerProvider.notifier).ready;

    await container
        .read(authControllerProvider.notifier)
        .login(email: 'vale@korofin.local', password: 'secret12');

    expect(
        container.read(authControllerProvider).status, AuthStatus.authenticated);
    expect(container.read(accessTokenProvider), 'LOGIN');
    expect(await store.readRefreshToken(), 'LOGIN_R');
  });

  test('logout llama al backend, limpia el store y deja no autenticado',
      () async {
    final store = InMemorySessionStore()..saveRefreshToken('R1');
    final repo = _FakeAuthRepository();
    final container = _container(store: store, repo: repo);
    await container.read(authControllerProvider.notifier).ready;

    await container.read(authControllerProvider.notifier).logout();

    expect(repo.logoutCalls, 1);
    expect(
        container.read(authControllerProvider).status, AuthStatus.unauthenticated);
    expect(container.read(accessTokenProvider), isNull);
    expect(await store.readRefreshToken(), isNull);
  });

  group('despertar el backend en el bootstrap', () {
    test('con sesión guardada, sondea el health ANTES de pedir el refresh',
        () async {
      // El refresh es un POST: si sale con el backend dormido, expira y no se
      // puede reintentar solo. Despertarlo primero es lo que evita que el
      // arranque de la app muera contra un servicio que todavía está booteando.
      final adapter = CapturingAdapter(body: <String, dynamic>{'status': 'UP'});
      final store = InMemorySessionStore()..saveRefreshToken('R1');
      final repo = _FakeAuthRepository();
      final container =
          _container(store: store, repo: repo, adapter: adapter);

      await container.read(authControllerProvider.notifier).ready;

      expect(adapter.calls, 1);
      expect(adapter.lastRequest.path, '/actuator/health');
      expect(repo.refreshCalls, 1);
      expect(container.read(authControllerProvider).status,
          AuthStatus.authenticated);
    });

    test('sin sesión guardada no bloquea el arranque esperando el health',
        () async {
      // Acá el destino es el login: hacer esperar al usuario los ~200 s del
      // arranque frente a un splash sería peor que dejarlo escribir sus
      // credenciales mientras el backend despierta en paralelo.
      //
      // El sondeo se cuelga a propósito y nunca responde: si el bootstrap lo
      // esperara (`await` en vez de `unawaited`), `ready` jamás completaría y
      // este test moriría por timeout en vez de pasar.
      final adapter = HangingAdapter();
      final store = InMemorySessionStore();
      final container = _container(
        store: store,
        repo: _FakeAuthRepository(),
        adapter: adapter,
      );

      await expectLater(
        container
            .read(authControllerProvider.notifier)
            .ready
            .timeout(const Duration(seconds: 2)),
        completes,
      );

      expect(container.read(authControllerProvider).status,
          AuthStatus.unauthenticated);
      // No se afirma nada sobre `adapter.calls`: el bootstrap completa antes de
      // que el fetch del sondeo alcance a correr, que es exactamente la prueba
      // de que no lo espera. Que el sondeo sí se dispare lo cubre el test del
      // camino con sesión guardada.
    });

    test('un health que falla no aborta el refresh de sesión', () async {
      // El sondeo es un intento de despertar, no una precondición: si falla,
      // el bootstrap tiene que seguir e intentar el refresh igual. Con el
      // refresh en verde, el único resultado que prueba eso es `authenticated`.
      final adapter =
          CapturingAdapter(throwType: DioExceptionType.connectionError);
      final store = InMemorySessionStore()..saveRefreshToken('R1');
      final repo = _FakeAuthRepository();
      final container = _container(store: store, repo: repo, adapter: adapter);

      await container.read(authControllerProvider.notifier).ready;

      expect(repo.refreshCalls, 1);
      expect(container.read(authControllerProvider).status,
          AuthStatus.authenticated);
    });
  });
}
