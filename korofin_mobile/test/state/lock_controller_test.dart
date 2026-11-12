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

  /// Simula una transición de auth (bootstrap, login, logout).
  void emit(AuthState next) => state = next;
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

/// Reloj controlable: el período de gracia del bloqueo depende de cuánto
/// tiempo estuvo la app en background.
DateTime _now = DateTime(2026, 1, 1, 12);

/// Simula que la app pasó a background (pantalla apagada, otra app) durante
/// [away] y volvió. Por defecto, bastante más que el período de gracia.
void _backgroundAndBack(
  LockController notifier, {
  Duration away = const Duration(minutes: 30),
}) {
  notifier.didChangeAppLifecycleState(AppLifecycleState.inactive);
  notifier.didChangeAppLifecycleState(AppLifecycleState.hidden);
  notifier.didChangeAppLifecycleState(AppLifecycleState.paused);
  _now = _now.add(away);
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
      lockClockProvider.overrideWithValue(() => _now),
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

  test(
      'cerrar sesión con la app bloqueada y volver a entrar con contraseña no pide huella',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();
    _backgroundAndBack(notifier);
    expect(container.read(lockControllerProvider).locked, isTrue);

    final auth =
        container.read(authControllerProvider.notifier) as _FixedAuthController;
    auth.emit(const AuthState.unauthenticated());
    await Future<void>.delayed(Duration.zero);
    auth.emit(AuthState.authenticated(_user()));
    await Future<void>.delayed(Duration.zero);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('al abrir la app con sesión guardada y bloqueo habilitado, pide huella',
      () async {
    final lockStore = InMemoryLockStore();
    await lockStore.setEnabled(true);
    final container = _container(
      authState: const AuthState.unknown(),
      lockStore: lockStore,
    );
    container.read(lockControllerProvider);
    await Future<void>.delayed(Duration.zero);

    final auth =
        container.read(authControllerProvider.notifier) as _FixedAuthController;
    auth.emit(AuthState.authenticated(_user()));
    await Future<void>.delayed(Duration.zero);

    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test('con bloqueo deshabilitado, abrir la app con sesión guardada no pide huella',
      () async {
    final container = _container(authState: const AuthState.unknown());
    container.read(lockControllerProvider);
    await Future<void>.delayed(Duration.zero);

    final auth =
        container.read(authControllerProvider.notifier) as _FixedAuthController;
    auth.emit(AuthState.authenticated(_user()));
    await Future<void>.delayed(Duration.zero);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('iniciar sesión con contraseña y bloqueo habilitado no pide huella',
      () async {
    final lockStore = InMemoryLockStore();
    await lockStore.setEnabled(true);
    final container = _container(
      authState: const AuthState.unauthenticated(),
      lockStore: lockStore,
    );
    container.read(lockControllerProvider);
    await Future<void>.delayed(Duration.zero);

    final auth =
        container.read(authControllerProvider.notifier) as _FixedAuthController;
    auth.emit(AuthState.authenticated(_user()));
    await Future<void>.delayed(Duration.zero);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('volver del background dentro del período de gracia no pide huella',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();

    _backgroundAndBack(
      notifier,
      away: LockController.backgroundGracePeriod - const Duration(seconds: 1),
    );

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('volver del background justo al cumplirse el período de gracia pide huella',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();

    _backgroundAndBack(notifier, away: LockController.backgroundGracePeriod);

    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test(
      'varias salidas cortas seguidas no se acumulan: cada una cuenta desde que '
      'la app salió', () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();
    const Duration short = Duration(seconds: 50);

    _backgroundAndBack(notifier, away: short);
    _backgroundAndBack(notifier, away: short);
    _backgroundAndBack(notifier, away: short);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('si el reloj del teléfono se atrasó mientras la app estaba fuera, pide huella',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enable();

    _backgroundAndBack(notifier, away: const Duration(hours: -1));

    expect(container.read(lockControllerProvider).locked, isTrue);
  });
}
