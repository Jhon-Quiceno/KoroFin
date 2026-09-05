import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/core/providers.dart';
import 'package:korofin_mobile/core/storage/session_store.dart';
import 'package:korofin_mobile/data/repositories/auth_repository.dart';
import 'package:korofin_mobile/models/auth_session.dart';
import 'package:korofin_mobile/models/user.dart';
import 'package:korofin_mobile/state/auth/auth_controller.dart';
import 'package:korofin_mobile/state/auth/auth_state.dart';

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

ProviderContainer _container({
  required SessionStore store,
  required AuthRepository repo,
}) {
  final container = ProviderContainer(
    overrides: <Override>[
      sessionStoreProvider.overrideWithValue(store),
      authRepositoryProvider.overrideWithValue(repo),
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
}
