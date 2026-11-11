import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/auth/biometric_authenticator.dart';
import 'package:korofin_mobile/core/providers.dart';
import 'package:korofin_mobile/core/storage/lock_store.dart';
import 'package:korofin_mobile/models/user.dart';
import 'package:korofin_mobile/state/auth/auth_controller.dart';
import 'package:korofin_mobile/state/auth/auth_state.dart';
import 'package:korofin_mobile/state/lock/lock_controller.dart';

User _user() => const User(
      id: 1,
      name: 'Vale',
      email: 'vale@korofin.local',
      theme: 'SYSTEM',
      currency: 'COP',
      language: 'ES',
    );

/// Deja el estado de auth fijo sin correr el bootstrap real: los tests de
/// bloqueo no necesitan red, solo un [AuthStatus] conocido.
class _FixedAuthController extends AuthController {
  _FixedAuthController(this._fixed);
  final AuthState _fixed;

  @override
  AuthState build() => _fixed;
}

class _FakeBiometricAuthenticator implements BiometricAuthenticator {
  _FakeBiometricAuthenticator({
    this.available = true,
    this.succeeds = true,
    this.whileAuthenticating,
  });

  bool available;
  bool succeeds;

  /// Simula lo que hace el prompt nativo de huella mientras está abierto
  /// (por ejemplo, pausar y reanudar la actividad).
  void Function()? whileAuthenticating;

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<bool> authenticate({required String reason}) async {
    whileAuthenticating?.call();
    return succeeds;
  }
}

/// Simula que la app pasó a background (pantalla apagada, otra app) y volvió.
void _backgroundAndBack(LockController notifier) {
  notifier.didChangeAppLifecycleState(AppLifecycleState.inactive);
  notifier.didChangeAppLifecycleState(AppLifecycleState.hidden);
  notifier.didChangeAppLifecycleState(AppLifecycleState.paused);
  notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);
}

ProviderContainer _container({
  required AuthState authState,
  InMemoryLockStore? lockStore,
  _FakeBiometricAuthenticator? biometrics,
}) {
  final container = ProviderContainer(
    overrides: <Override>[
      authControllerProvider.overrideWith(() => _FixedAuthController(authState)),
      lockStoreProvider.overrideWithValue(lockStore ?? InMemoryLockStore()),
      biometricAuthenticatorProvider
          .overrideWithValue(biometrics ?? _FakeBiometricAuthenticator()),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('arranca sin bloqueo activo mientras carga la preferencia', () async {
    final container = _container(authState: const AuthState.unauthenticated());
    expect(container.read(lockControllerProvider).enabled, isFalse);
    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('enable habilita el bloqueo sin bloquear la sesión actual', () async {
    final container = _container(authState: const AuthState.unauthenticated());
    final notifier = container.read(lockControllerProvider.notifier);

    await notifier.enable();

    final state = container.read(lockControllerProvider);
    expect(state.enabled, isTrue);
    expect(state.locked, isFalse);
  });

  test(
      'al volver del background con bloqueo habilitado y sesión activa, queda bloqueada',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();

    _backgroundAndBack(notifier);

    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test('con bloqueo deshabilitado, volver del background no bloquea',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    // El bloqueo nunca se habilitó.

    _backgroundAndBack(notifier);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('sin sesión activa, volver del background no bloquea aunque esté habilitado',
      () async {
    final container = _container(authState: const AuthState.unauthenticated());
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();

    _backgroundAndBack(notifier);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('sin biometría disponible, isBiometricAvailable da false', () async {
    final container = _container(
      authState: AuthState.authenticated(_user()),
      biometrics: _FakeBiometricAuthenticator(available: false),
    );
    final notifier = container.read(lockControllerProvider.notifier);

    expect(await notifier.isBiometricAvailable(), isFalse);
  });

  test('unlockWithBiometrics exitoso desbloquea', () async {
    final container = _container(
      authState: AuthState.authenticated(_user()),
      biometrics: _FakeBiometricAuthenticator(),
    );
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();
    _backgroundAndBack(notifier);

    final bool ok = await notifier.unlockWithBiometrics();

    expect(ok, isTrue);
    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('unlockWithBiometrics fallido mantiene bloqueada la app', () async {
    final container = _container(
      authState: AuthState.authenticated(_user()),
      biometrics: _FakeBiometricAuthenticator(succeeds: false),
    );
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();
    _backgroundAndBack(notifier);

    final bool ok = await notifier.unlockWithBiometrics();

    expect(ok, isFalse);
    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test('disable apaga el bloqueo y limpia el estado bloqueado', () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();
    _backgroundAndBack(notifier);

    await notifier.disable();

    final state = container.read(lockControllerProvider);
    expect(state.enabled, isFalse);
    expect(state.locked, isFalse);
  });

  test('volver de inactive sin pasar por background no bloquea', () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();

    notifier.didChangeAppLifecycleState(AppLifecycleState.inactive);
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test(
      'la pausa que causa el propio prompt de huella no vuelve a bloquear tras desbloquear',
      () async {
    late final LockController notifier;
    final biometrics = _FakeBiometricAuthenticator(
      whileAuthenticating: () => _backgroundAndBack(notifier),
    );
    final container = _container(
      authState: AuthState.authenticated(_user()),
      biometrics: biometrics,
    );
    notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();
    _backgroundAndBack(notifier);
    expect(container.read(lockControllerProvider).locked, isTrue);

    final bool ok = await notifier.unlockWithBiometrics();
    // El resumed del prompt puede llegar después de que termina la autenticación.
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    expect(ok, isTrue);
    expect(container.read(lockControllerProvider).locked, isFalse);
  });
}
