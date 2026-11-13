import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../../data/repositories/auth_repository.dart';
import '../../models/auth_session.dart';
import '../../models/user.dart';
import '../../models/user_preferences.dart';
import '../../theme/theme_controller.dart';
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
      // Sin sesión el destino es el login. Despertar el backend acá sin esperar
      // es lo mejor de los dos mundos: el usuario escribe sus credenciales
      // (10-20 s) mientras el servicio arranca en paralelo, en vez de mirar un
      // splash durante los ~200 s que tarda el plan Free de Render.
      unawaited(ref.read(apiClientProvider).warmUp());
      state = const AuthState.unauthenticated();
      return;
    }
    // Con sesión guardada hay que despertar el backend ANTES del refresh: ese
    // refresh es un POST y, si expira contra un servicio dormido, no se puede
    // reintentar solo sin arriesgar rotar el token dos veces. El splash cubre
    // esta espera y va explicando que el servidor está arrancando.
    await ref.read(apiClientProvider).warmUp();
    try {
      final AuthSession session = await _repo.refresh(refreshToken);
      await _persist(session);
      state = AuthState.authenticated(session.user);
    } on ApiException catch (e) {
      // Solo un rechazo del backend (401/403) invalida la sesión. Un timeout,
      // la falta de red o un 5xx del servicio arrancando son transitorios: se
      // conserva el refresh token para que el próximo arranque lo reintente,
      // en vez de obligar a escribir correo y contraseña otra vez.
      if (e.isUnauthorized) {
        await _clear();
      } else {
        ref.read(accessTokenProvider.notifier).state = null;
      }
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

  /// Reemplaza el usuario en el estado tras editar el perfil, sin tocar tokens.
  void applyUser(User user) {
    if (state.status == AuthStatus.authenticated) {
      state = AuthState.authenticated(user);
    }
  }

  Future<void> _persist(AuthSession session) async {
    ref.read(accessTokenProvider.notifier).state = session.accessToken;
    await ref.read(sessionStoreProvider).saveRefreshToken(session.refreshToken);
    // El tema guardado del usuario manda al abrir sesión.
    ThemeController.mode.value =
        ThemePreference.fromWire(session.user.theme).mode;
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
      } on ApiException catch (e) {
        // `null` significa "sesión terminada" y dispara [onSessionExpired]:
        // solo corresponde si el backend rechazó el refresh token. Un fallo
        // transitorio se relanza para que la sesión siga abierta.
        if (e.isUnauthorized) return null;
        rethrow;
      }
    };

    client.onSessionExpired = () {
      ref.read(accessTokenProvider.notifier).state = null;
      unawaited(ref.read(sessionStoreProvider).clear());
      state = const AuthState.unauthenticated();
    };
  }
}
