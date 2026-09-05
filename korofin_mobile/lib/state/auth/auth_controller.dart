import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../../data/repositories/auth_repository.dart';
import '../../models/auth_session.dart';
import 'auth_state.dart';

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.read(apiClientProvider)),
);

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);

/// Orquesta la sesión: bootstrap al abrir la app, login/registro/logout, y el
/// cableado del refresh transparente del [ApiClient].
class AuthController extends Notifier<AuthState> {
  final Completer<void> _ready = Completer<void>();

  /// Se completa cuando el bootstrap de sesión terminó (haya o no sesión). Útil
  /// en tests para esperar el arranque; en la app basta con observar el estado.
  Future<void> get ready => _ready.future;

  @override
  AuthState build() {
    _wireApiClient();
    // Fire-and-forget: `build` no puede ser async; el estado se actualiza
    // cuando el bootstrap termina.
    unawaited(_bootstrap().whenComplete(() {
      if (!_ready.isCompleted) _ready.complete();
    }));
    return const AuthState.unknown();
  }

  AuthRepository get _repo => ref.read(authRepositoryProvider);

  Future<void> _bootstrap() async {
    final String? refreshToken =
        await ref.read(sessionStoreProvider).readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      state = const AuthState.unauthenticated();
      return;
    }
    try {
      final AuthSession session = await _repo.refresh(refreshToken);
      await _persist(session);
      state = AuthState.authenticated(session.user);
    } on ApiException {
      await _clear();
      state = const AuthState.unauthenticated();
    }
  }

  Future<void> login({
    required String email,
    required String password,
  }) async {
    final AuthSession session = await _repo.login(
      email: email.trim(),
      password: password,
    );
    await _persist(session);
    state = AuthState.authenticated(session.user);
  }

  Future<void> register({
    required String name,
    required String email,
    required String password,
  }) async {
    final AuthSession session = await _repo.register(
      name: name.trim(),
      email: email.trim(),
      password: password,
    );
    await _persist(session);
    state = AuthState.authenticated(session.user);
  }

  Future<void> logout() async {
    final String? refreshToken =
        await ref.read(sessionStoreProvider).readRefreshToken();
    if (refreshToken != null && refreshToken.isNotEmpty) {
      try {
        await _repo.logout(refreshToken);
      } on ApiException {
        // El logout local no debe fallar por un error de red del backend.
      }
    }
    await _clear();
    state = const AuthState.unauthenticated();
  }

  Future<void> _persist(AuthSession session) async {
    ref.read(accessTokenProvider.notifier).state = session.accessToken;
    await ref.read(sessionStoreProvider).saveRefreshToken(session.refreshToken);
  }

  Future<void> _clear() async {
    ref.read(accessTokenProvider.notifier).state = null;
    await ref.read(sessionStoreProvider).clear();
  }

  /// Le da al [ApiClient] cómo renovar la sesión ante un 401 y qué hacer si el
  /// refresh falla de forma definitiva.
  void _wireApiClient() {
    final ApiClient client = ref.read(apiClientProvider);

    client.onRefresh = () async {
      final String? refreshToken =
          await ref.read(sessionStoreProvider).readRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) return null;
      try {
        final AuthSession session = await _repo.refresh(refreshToken);
        await _persist(session);
        return session.accessToken;
      } on ApiException {
        return null;
      }
    };

    client.onSessionExpired = () {
      ref.read(accessTokenProvider.notifier).state = null;
      unawaited(ref.read(sessionStoreProvider).clear());
      state = const AuthState.unauthenticated();
    };
  }
}
