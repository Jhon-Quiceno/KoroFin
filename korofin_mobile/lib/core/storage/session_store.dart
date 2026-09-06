import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persistencia del refresh token.
///
/// El access token **no** se guarda: vive solo en memoria durante la sesión y
/// se re-obtiene con un refresh al abrir la app. Solo el refresh token
/// sobrevive a un cierre en frío, y por eso va en almacenamiento seguro.
///
/// Es una interfaz para poder inyectar una implementación en memoria en los
/// tests (el plugin nativo no está disponible en `flutter test`).
abstract interface class SessionStore {
  Future<String?> readRefreshToken();
  Future<void> saveRefreshToken(String token);
  Future<void> clear();
}

/// Implementación real: Keychain en iOS, Keystore/EncryptedSharedPreferences en
/// Android.
class SecureSessionStore implements SessionStore {
  SecureSessionStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  final FlutterSecureStorage _storage;

  static const String _refreshTokenKey = 'korofin.refresh_token';

  @override
  Future<String?> readRefreshToken() =>
      _storage.read(key: _refreshTokenKey);

  @override
  Future<void> saveRefreshToken(String token) =>
      _storage.write(key: _refreshTokenKey, value: token);

  @override
  Future<void> clear() => _storage.delete(key: _refreshTokenKey);
}

/// Implementación en memoria para tests y previews.
class InMemorySessionStore implements SessionStore {
  String? _refreshToken;

  @override
  Future<String?> readRefreshToken() async => _refreshToken;

  @override
  Future<void> saveRefreshToken(String token) async => _refreshToken = token;

  @override
  Future<void> clear() async => _refreshToken = null;
}
