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
  _FakeBiometricAuthenticator({this.available = true, this.succeeds = true});

  bool available;
  bool succeeds;

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<bool> authenticate({required String reason}) async => succeeds;
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

  test('enableWithPin habilita el bloqueo sin bloquear la sesión actual',
      () async {
    final container = _container(authState: const AuthState.unauthenticated());
    final notifier = container.read(lockControllerProvider.notifier);

    await notifier.enableWithPin('1234');

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
    await notifier.enableWithPin('1234');

    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test('con bloqueo deshabilitado, volver del background no bloquea',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    // El bloqueo nunca se habilitó.

    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('sin sesión activa, volver del background no bloquea aunque esté habilitado',
      () async {
    final container = _container(authState: const AuthState.unauthenticated());
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enableWithPin('1234');

    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('unlockWithPin correcto desbloquea', () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);
    expect(container.read(lockControllerProvider).locked, isTrue);

    final bool ok = await notifier.unlockWithPin('1234');

    expect(ok, isTrue);
    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('unlockWithPin incorrecto no desbloquea', () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    final bool ok = await notifier.unlockWithPin('0000');

    expect(ok, isFalse);
    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test('sin biometría disponible, isBiometricAvailable da false (fallback a PIN)',
      () async {
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
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

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
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    final bool ok = await notifier.unlockWithBiometrics();

    expect(ok, isFalse);
    expect(container.read(lockControllerProvider).locked, isTrue);
  });

  test('currentPinLockout refleja el lockout del repositorio tras 5 fallos',
      () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    expect(await notifier.currentPinLockout(), isNull);
    for (int i = 0; i < 5; i++) {
      await notifier.unlockWithPin('0000');
    }

    expect(await notifier.currentPinLockout(), isNotNull);
  });

  test('la biometría sigue funcionando aunque el PIN esté en lockout',
      () async {
    final container = _container(
      authState: AuthState.authenticated(_user()),
      biometrics: _FakeBiometricAuthenticator(),
    );
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);
    for (int i = 0; i < 5; i++) {
      await notifier.unlockWithPin('0000');
    }
    expect(await notifier.currentPinLockout(), isNotNull);

    final bool ok = await notifier.unlockWithBiometrics();

    expect(ok, isTrue);
    expect(container.read(lockControllerProvider).locked, isFalse);
  });

  test('disable apaga el bloqueo y limpia el estado bloqueado', () async {
    final container =
        _container(authState: AuthState.authenticated(_user()));
    final notifier = container.read(lockControllerProvider.notifier);
    await notifier.enableWithPin('1234');
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);

    await notifier.disable();

    final state = container.read(lockControllerProvider);
    expect(state.enabled, isFalse);
    expect(state.locked, isFalse);
  });
}
