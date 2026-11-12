import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/lock_repository.dart';
import '../auth/auth_controller.dart';
import '../auth/auth_state.dart';
import 'lock_state.dart';

final lockRepositoryProvider = Provider<LockRepository>(
  (ref) => LockRepository(
    ref.read(lockStoreProvider),
    ref.read(biometricAuthenticatorProvider),
  ),
);

/// Reloj del bloqueo. Es un provider para poder controlar el tiempo en los
/// tests del período de gracia.
final lockClockProvider = Provider<DateTime Function()>((ref) => DateTime.now);

final lockControllerProvider =
    NotifierProvider<LockController, AppLockState>(LockController.new);

/// Orquesta el bloqueo opt-in de la app: carga la preferencia guardada,
/// bloquea automáticamente al volver del background (si está habilitado y hay
/// sesión activa) y expone el desbloqueo por biometría.
///
/// Se suscribe como [WidgetsBindingObserver] en lugar de en un [State] porque
/// el bloqueo debe activarse sin importar qué pantalla esté montada.
class LockController extends Notifier<AppLockState> with WidgetsBindingObserver {
  @override
  AppLockState build() {
    WidgetsBinding.instance.addObserver(this);
    ref.onDispose(() => WidgetsBinding.instance.removeObserver(this));
    // Si la sesión ya estaba restaurada cuando se construye este controller,
    // cuenta como apertura de la app: el listener solo ve los cambios futuros.
    _restoredSessionPendingLock =
        ref.read(authControllerProvider).status == AuthStatus.authenticated;
    ref.listen<AuthStatus>(
      authControllerProvider.select((auth) => auth.status),
      _onAuthStatusChanged,
    );
    unawaited(_loadEnabled());
    return const AppLockState.initial();
  }

  LockRepository get _repo => ref.read(lockRepositoryProvider);

  /// `true` cuando la sesión se restauró al abrir la app (bootstrap) y todavía
  /// no se aplicó el bloqueo, porque la preferencia `enabled` puede cargar
  /// después de que termina el bootstrap.
  bool _restoredSessionPendingLock = false;

  Future<void> _loadEnabled() async {
    final bool enabled = await _repo.isEnabled();
    state = state.copyWith(enabled: enabled);
    _applyRestoredSessionLock();
  }

  /// - Sesión restaurada al abrir la app (`unknown` -> `authenticated`): pide
  ///   huella, igual que al volver del background.
  /// - Login con contraseña (`unauthenticated` -> `authenticated`): no pide
  ///   huella; la contraseña ya autenticó al usuario.
  /// - Sin sesión: limpia el bloqueo. Si no, cerrar sesión desde la pantalla
  ///   de bloqueo dejaba `locked` en `true` y el siguiente login con
  ///   contraseña caía de nuevo en la huella.
  void _onAuthStatusChanged(AuthStatus? previous, AuthStatus next) {
    if (next != AuthStatus.authenticated) {
      _restoredSessionPendingLock = false;
      if (state.locked) state = state.copyWith(locked: false);
      return;
    }
    if (previous == AuthStatus.unknown) {
      _restoredSessionPendingLock = true;
      _applyRestoredSessionLock();
    }
  }

  void _applyRestoredSessionLock() {
    if (!_restoredSessionPendingLock || !state.enabled) return;
    _restoredSessionPendingLock = false;
    state = state.copyWith(locked: true);
  }

  /// Tiempo que la app puede pasar en background sin volver a pedir huella.
  /// Evita pedirla por salidas breves (contestar un mensaje, copiar un código,
  /// abrir Nequi) sin dejar la app abierta si el teléfono queda desatendido.
  static const Duration backgroundGracePeriod = Duration(minutes: 2);

  /// Momento en que la app pasó de verdad a background (pantalla apagada, otra
  /// app) desde el último `resumed`; `null` si no salió. Un `resumed` que
  /// viene solo de `inactive` (persiana de notificaciones, diálogo del
  /// sistema) no bloquea.
  DateTime? _backgroundedAt;

  /// Mientras el prompt nativo de huella está abierto, el sistema pausa y
  /// reanuda la actividad. Esas transiciones las causa el propio desbloqueo y
  /// no deben volver a bloquear: si no, la app queda en un bucle pidiendo
  /// huella sin fin.
  bool _authenticating = false;

  // El parámetro se renombra a propósito: `state` ya es la propiedad de
  // [Notifier] que guarda el [AppLockState], y usarla acá pisaría ese getter.
  @override
  // ignore: avoid_renaming_method_parameters
  void didChangeAppLifecycleState(AppLifecycleState appLifecycleState) {
    switch (appLifecycleState) {
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
        // `hidden` y `paused` llegan seguidos: cuenta el primero.
        if (!_authenticating) _backgroundedAt ??= ref.read(lockClockProvider)();
      case AppLifecycleState.resumed:
        final DateTime? backgroundedAt = _backgroundedAt;
        _backgroundedAt = null;
        if (backgroundedAt == null) return;
        final Duration away =
            ref.read(lockClockProvider)().difference(backgroundedAt);
        // Un tiempo negativo significa que el reloj del teléfono se atrasó: no
        // se puede confiar en la medición, así que se pide huella.
        if (!away.isNegative && away < backgroundGracePeriod) return;
        final bool hasActiveSession =
            ref.read(authControllerProvider).status == AuthStatus.authenticated;
        if (state.enabled && hasActiveSession) {
          state = state.copyWith(locked: true);
        }
      case AppLifecycleState.inactive:
      case AppLifecycleState.detached:
        break;
    }
  }

  Future<bool> isBiometricAvailable() => _repo.isBiometricAvailable();

  /// Activa el bloqueo biométrico. No bloquea la sesión actual: recién aplica
  /// en el próximo regreso desde background.
  Future<void> enable() async {
    _restoredSessionPendingLock = false;
    await _repo.enable();
    state = state.copyWith(enabled: true, locked: false);
  }

  Future<void> disable() async {
    await _repo.disable();
    state = state.copyWith(enabled: false, locked: false);
  }

  Future<bool> unlockWithBiometrics() async {
    _authenticating = true;
    try {
      final bool ok = await _repo.authenticateWithBiometrics();
      if (ok) state = state.copyWith(locked: false);
      return ok;
    } finally {
      _authenticating = false;
    }
  }
}
