import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persistencia del bloqueo de la app (opt-in, solo biometría).
///
/// Guarda únicamente si el bloqueo está habilitado: no hay PIN ni lockout que
/// persistir, el sistema operativo ya aplica su propio límite de intentos
/// biométricos.
///
/// Es una interfaz para poder inyectar una implementación en memoria en los
/// tests (el plugin nativo no está disponible en `flutter test`).
abstract interface class LockStore {
  Future<bool> isEnabled();
  Future<void> setEnabled(bool enabled);
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

  @override
  Future<bool> isEnabled() async =>
      (await _storage.read(key: _enabledKey)) == 'true';

  @override
  Future<void> setEnabled(bool enabled) =>
      _storage.write(key: _enabledKey, value: enabled ? 'true' : 'false');
}

/// Implementación en memoria para tests y previews.
class InMemoryLockStore implements LockStore {
  bool _enabled = false;

  @override
  Future<bool> isEnabled() async => _enabled;

  @override
  Future<void> setEnabled(bool enabled) async => _enabled = enabled;
}
