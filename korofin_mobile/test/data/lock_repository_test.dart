import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/auth/biometric_authenticator.dart';
import 'package:korofin_mobile/core/storage/lock_store.dart';
import 'package:korofin_mobile/data/repositories/lock_repository.dart';

class _FakeBiometricAuthenticator implements BiometricAuthenticator {
  bool available = true;
  bool succeeds = true;
  int authenticateCalls = 0;

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<bool> authenticate({required String reason}) async {
    authenticateCalls++;
    return succeeds;
  }
}

void main() {
  late InMemoryLockStore store;
  late _FakeBiometricAuthenticator biometrics;
  late LockRepository repo;

  setUp(() {
    store = InMemoryLockStore();
    biometrics = _FakeBiometricAuthenticator();
    repo = LockRepository(store, biometrics);
  });

  test('el bloqueo empieza deshabilitado', () async {
    expect(await repo.isEnabled(), isFalse);
  });

  test('enable habilita el bloqueo', () async {
    await repo.enable();
    expect(await repo.isEnabled(), isTrue);
  });

  test('disable apaga el bloqueo', () async {
    await repo.enable();
    await repo.disable();
    expect(await repo.isEnabled(), isFalse);
  });

  test('isBiometricAvailable delega en el autenticador', () async {
    biometrics.available = false;
    expect(await repo.isBiometricAvailable(), isFalse);

    biometrics.available = true;
    expect(await repo.isBiometricAvailable(), isTrue);
  });

  test('authenticateWithBiometrics delega en el autenticador', () async {
    biometrics.succeeds = true;
    expect(await repo.authenticateWithBiometrics(), isTrue);
    expect(biometrics.authenticateCalls, 1);

    biometrics.succeeds = false;
    expect(await repo.authenticateWithBiometrics(), isFalse);
  });
}
