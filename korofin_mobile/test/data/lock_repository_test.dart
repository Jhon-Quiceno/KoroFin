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

/// Reloj controlable a mano para poder probar el lockout sin esperar tiempo
/// real (los tramos van de 30s a 15min).
class _FakeClock {
  _FakeClock(this._current);

  DateTime _current;

  DateTime now() => _current;

  void advance(Duration duration) => _current = _current.add(duration);

  void jumpTo(DateTime instant) => _current = instant;
}

void main() {
  late InMemoryLockStore store;
  late _FakeBiometricAuthenticator biometrics;
  late _FakeClock clock;
  late LockRepository repo;

  setUp(() {
    store = InMemoryLockStore();
    biometrics = _FakeBiometricAuthenticator();
    clock = _FakeClock(DateTime(2026, 1, 1, 12));
    repo = LockRepository(store, biometrics, now: clock.now);
  });

  test('el bloqueo empieza deshabilitado', () async {
    expect(await repo.isEnabled(), isFalse);
  });

  test('enableWithPin guarda un hash salteado, nunca el PIN en claro',
      () async {
    await repo.enableWithPin('1234');

    expect(await repo.isEnabled(), isTrue);
    final String? storedHash = await store.readPinHash();
    final String? salt = await store.readSalt();
    expect(storedHash, isNotNull);
    expect(salt, isNotNull);
    expect(storedHash, isNot('1234'));
    expect(storedHash, isNot(contains('1234')));
  });

  test('el mismo PIN genera hashes distintos por la sal aleatoria', () async {
    await repo.enableWithPin('1234');
    final String? firstHash = await store.readPinHash();

    await repo.enableWithPin('1234');
    final String? secondHash = await store.readPinHash();

    expect(firstHash, isNot(secondHash));
  });

  test('enableWithPin rechaza PINs que no son 4 dígitos numéricos', () async {
    await expectLater(repo.enableWithPin('123'), throwsArgumentError);
    await expectLater(repo.enableWithPin('12a4'), throwsArgumentError);
    await expectLater(repo.enableWithPin('12345'), throwsArgumentError);
  });

  test('verifyPin acepta el PIN correcto', () async {
    await repo.enableWithPin('4321');
    expect(await repo.verifyPin('4321'), isTrue);
  });

  test('verifyPin rechaza un PIN incorrecto', () async {
    await repo.enableWithPin('4321');
    expect(await repo.verifyPin('0000'), isFalse);
  });

  test('verifyPin da false si nunca se configuró un PIN', () async {
    expect(await repo.verifyPin('1234'), isFalse);
  });

  test('disable apaga el bloqueo y borra el PIN guardado', () async {
    await repo.enableWithPin('1234');

    await repo.disable();

    expect(await repo.isEnabled(), isFalse);
    expect(await store.readPinHash(), isNull);
    expect(await store.readSalt(), isNull);
    expect(await repo.verifyPin('1234'), isFalse);
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

  group('lockout de PIN', () {
    setUp(() async {
      await repo.enableWithPin('4321');
    });

    test('el contador de intentos fallidos persiste entre instancias del repo',
        () async {
      await repo.verifyPin('0000');
      await repo.verifyPin('0000');

      // Una segunda instancia sobre el mismo store retoma el contador: matar
      // la app no debe resetear los intentos.
      final otherRepo = LockRepository(store, biometrics, now: clock.now);
      expect(await store.readFailedAttempts(), 2);

      // Con 3 fallos más desde la nueva instancia se llega al umbral de 5.
      await otherRepo.verifyPin('0000');
      await otherRepo.verifyPin('0000');
      await otherRepo.verifyPin('0000');

      expect(await otherRepo.currentLockout(), isNotNull);
    });

    test('el lockout se activa al llegar al umbral de intentos fallidos',
        () async {
      for (int i = 0; i < 5; i++) {
        await repo.verifyPin('0000');
      }

      final Duration? lockout = await repo.currentLockout();
      expect(lockout, isNotNull);
      expect(lockout!.inSeconds, 30); // primer tramo documentado
    });

    test('durante el lockout, un PIN correcto no desbloquea', () async {
      for (int i = 0; i < 5; i++) {
        await repo.verifyPin('0000');
      }

      expect(await repo.verifyPin('4321'), isFalse);
    });

    test('vencido el lockout, se puede reintentar', () async {
      for (int i = 0; i < 5; i++) {
        await repo.verifyPin('0000');
      }
      expect(await repo.currentLockout(), isNotNull);

      clock.advance(const Duration(seconds: 31));

      expect(await repo.currentLockout(), isNull);
      expect(await repo.verifyPin('4321'), isTrue);
    });

    test('un desbloqueo exitoso por PIN resetea el contador', () async {
      await repo.verifyPin('0000');
      await repo.verifyPin('0000');

      expect(await repo.verifyPin('4321'), isTrue);

      expect(await store.readFailedAttempts(), 0);
      expect(await repo.currentLockout(), isNull);
    });

    test('un desbloqueo exitoso por biometría también resetea el contador',
        () async {
      await repo.verifyPin('0000');
      await repo.verifyPin('0000');

      biometrics.succeeds = true;
      expect(await repo.authenticateWithBiometrics(), isTrue);

      expect(await store.readFailedAttempts(), 0);
    });

    test('la biometría sigue funcionando durante un lockout de PIN', () async {
      for (int i = 0; i < 5; i++) {
        await repo.verifyPin('0000');
      }
      expect(await repo.currentLockout(), isNotNull);

      biometrics.succeeds = true;
      expect(await repo.authenticateWithBiometrics(), isTrue);
      expect(biometrics.authenticateCalls, 1);
    });

    test('el castigo escala en tramos con más intentos fallidos', () async {
      for (int i = 0; i < 5; i++) {
        await repo.verifyPin('0000');
      }
      expect((await repo.currentLockout())!.inSeconds, 30);

      clock.advance(const Duration(seconds: 31)); // vence el primer tramo
      await repo.verifyPin('0000'); // sexto intento fallido: siguiente tramo

      expect((await repo.currentLockout())!.inMinutes, 1);
    });

    test(
        'un reloj retrocedido de forma implausible no deja el lockout vigente para siempre',
        () async {
      for (int i = 0; i < 5; i++) {
        await repo.verifyPin('0000');
      }
      expect(await repo.currentLockout(), isNotNull);

      // El usuario retrasa el reloj del dispositivo 1000 días.
      clock.advance(const Duration(days: -1000));

      final Duration? corrected = await repo.currentLockout();
      expect(corrected, isNotNull);
      expect(corrected!.inMinutes, 15); // tope del último tramo, no ~1000 días
    });
  });
}
