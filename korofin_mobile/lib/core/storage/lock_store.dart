import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persistencia del bloqueo de la app (opt-in).
///
/// Guarda si el bloqueo está habilitado, el hash + salt del PIN de 4 dígitos,
/// y el contador de intentos fallidos + el instante en que vence el lockout
/// temporal. **Nunca** se guarda el PIN en claro: [LockRepository] es quien
/// calcula el hash antes de llamar a [savePin].
///
/// El contador de intentos y el lockout se persisten (no solo en memoria)
/// para que reiniciar la app no sea una forma de resetear el castigo.
///
/// Es una interfaz para poder inyectar una implementación en memoria en los
/// tests (el plugin nativo no está disponible en `flutter test`).
abstract interface class LockStore {
  Future<bool> isEnabled();
  Future<void> setEnabled(bool enabled);
  Future<void> savePin(String pinHash, String salt);
  Future<String?> readPinHash();
  Future<String?> readSalt();
  Future<void> clearPin();

  /// Cantidad de intentos de PIN fallidos consecutivos desde el último
  /// desbloqueo exitoso (o desde que se activó el bloqueo).
  Future<int> readFailedAttempts();
  Future<void> saveFailedAttempts(int count);

  /// Instante en el que termina el lockout temporal actual, o `null` si no
  /// hay ninguno vigente. Se guarda como instante absoluto (no como una
  /// cuenta regresiva en memoria) para que sobreviva a que maten el proceso.
  Future<DateTime?> readLockoutUntil();
  Future<void> saveLockoutUntil(DateTime? until);
}

/// Implementación real: Keychain en iOS, Keystore/EncryptedSharedPreferences en
/// Android, igual que [SecureSessionStore].
class SecureLockStore implements LockStore {
  SecureLockStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  final FlutterSecureStorage _storage;

  static const String _enabledKey = 'korofin.lock_enabled';
  static const String _pinHashKey = 'korofin.lock_pin_hash';
  static const String _saltKey = 'korofin.lock_pin_salt';
  static const String _failedAttemptsKey = 'korofin.lock_failed_attempts';
  static const String _lockoutUntilKey = 'korofin.lock_lockout_until';

  @override
  Future<bool> isEnabled() async =>
      (await _storage.read(key: _enabledKey)) == 'true';

  @override
  Future<void> setEnabled(bool enabled) =>
      _storage.write(key: _enabledKey, value: enabled ? 'true' : 'false');

  @override
  Future<void> savePin(String pinHash, String salt) async {
    await _storage.write(key: _pinHashKey, value: pinHash);
    await _storage.write(key: _saltKey, value: salt);
  }

  @override
  Future<String?> readPinHash() => _storage.read(key: _pinHashKey);

  @override
  Future<String?> readSalt() => _storage.read(key: _saltKey);

  @override
  Future<void> clearPin() async {
    await _storage.delete(key: _pinHashKey);
    await _storage.delete(key: _saltKey);
  }

  @override
  Future<int> readFailedAttempts() async {
    final String? raw = await _storage.read(key: _failedAttemptsKey);
    return int.tryParse(raw ?? '') ?? 0;
  }

  @override
  Future<void> saveFailedAttempts(int count) =>
      _storage.write(key: _failedAttemptsKey, value: count.toString());

  @override
  Future<DateTime?> readLockoutUntil() async {
    final String? raw = await _storage.read(key: _lockoutUntilKey);
    final int? millis = raw == null ? null : int.tryParse(raw);
    return millis == null ? null : DateTime.fromMillisecondsSinceEpoch(millis);
  }

  @override
  Future<void> saveLockoutUntil(DateTime? until) async {
    if (until == null) {
      await _storage.delete(key: _lockoutUntilKey);
    } else {
      await _storage.write(
        key: _lockoutUntilKey,
        value: until.millisecondsSinceEpoch.toString(),
      );
    }
  }
}

/// Implementación en memoria para tests y previews.
class InMemoryLockStore implements LockStore {
  bool _enabled = false;
  String? _pinHash;
  String? _salt;
  int _failedAttempts = 0;
  DateTime? _lockoutUntil;

  @override
  Future<bool> isEnabled() async => _enabled;

  @override
  Future<void> setEnabled(bool enabled) async => _enabled = enabled;

  @override
  Future<void> savePin(String pinHash, String salt) async {
    _pinHash = pinHash;
    _salt = salt;
  }

  @override
  Future<String?> readPinHash() async => _pinHash;

  @override
  Future<String?> readSalt() async => _salt;

  @override
  Future<void> clearPin() async {
    _pinHash = null;
    _salt = null;
  }

  @override
  Future<int> readFailedAttempts() async => _failedAttempts;

  @override
  Future<void> saveFailedAttempts(int count) async => _failedAttempts = count;

  @override
  Future<DateTime?> readLockoutUntil() async => _lockoutUntil;

  @override
  Future<void> saveLockoutUntil(DateTime? until) async => _lockoutUntil = until;
}
