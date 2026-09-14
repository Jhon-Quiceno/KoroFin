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

final lockControllerProvider =
    NotifierProvider<LockController, AppLockState>(LockController.new);

/// Orquesta el bloqueo opt-in de la app: carga la preferencia guardada,
/// bloquea automáticamente al volver del background (si está habilitado y hay
/// sesión activa) y expone el desbloqueo por PIN o biometría.
///
/// Se suscribe como [WidgetsBindingObserver] en lugar de en un [State] porque
/// el bloqueo debe activarse sin importar qué pantalla esté montada.
class LockController extends Notifier<AppLockState> with WidgetsBindingObserver {
  @override
  AppLockState build() {
    WidgetsBinding.instance.addObserver(this);
    ref.onDispose(() => WidgetsBinding.instance.removeObserver(this));
    unawaited(_loadEnabled());
    return const AppLockState.initial();
  }

  LockRepository get _repo => ref.read(lockRepositoryProvider);

  Future<void> _loadEnabled() async {
    final bool enabled = await _repo.isEnabled();
    state = state.copyWith(enabled: enabled);
  }

  // El parámetro se renombra a propósito: `state` ya es la propiedad de
  // [Notifier] que guarda el [AppLockState], y usarla acá pisaría ese getter.
  @override
  // ignore: avoid_renaming_method_parameters
  void didChangeAppLifecycleState(AppLifecycleState appLifecycleState) {
    if (appLifecycleState != AppLifecycleState.resumed) return;
    final bool hasActiveSession =
        ref.read(authControllerProvider).status == AuthStatus.authenticated;
    if (state.enabled && hasActiveSession) {
      state = state.copyWith(locked: true);
    }
  }

  Future<bool> isBiometricAvailable() => _repo.isBiometricAvailable();

  /// Tiempo restante del lockout de PIN vigente, o `null` si se puede
  /// reintentar. No afecta a la biometría, que es un canal aparte.
  Future<Duration?> currentPinLockout() => _repo.currentLockout();

  /// Activa el bloqueo definiendo el PIN de 4 dígitos. No bloquea la sesión
  /// actual: recién aplica en el próximo regreso desde background.
  Future<void> enableWithPin(String pin) async {
    await _repo.enableWithPin(pin);
    state = state.copyWith(enabled: true, locked: false);
  }

  Future<void> disable() async {
    await _repo.disable();
    state = state.copyWith(enabled: false, locked: false);
  }

  Future<bool> unlockWithPin(String pin) async {
    final bool valid = await _repo.verifyPin(pin);
    if (valid) state = state.copyWith(locked: false);
    return valid;
  }

  Future<bool> unlockWithBiometrics() async {
    final bool ok = await _repo.authenticateWithBiometrics();
    if (ok) state = state.copyWith(locked: false);
    return ok;
  }
}
