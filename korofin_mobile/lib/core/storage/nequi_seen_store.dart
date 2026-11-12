import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Recuerda qué notificaciones de Nequi ya se le ofrecieron al usuario, para
/// no ofrecer dos veces el mismo movimiento (la misma notificación llega por
/// el stream en vivo y vuelve a aparecer al revisar la bandeja en el próximo
/// arranque).
///
/// Es una interfaz para poder inyectar una implementación en memoria en los
/// tests (el plugin nativo no está disponible en `flutter test`).
abstract interface class NequiSeenStore {
  Future<List<String>> readKeys();
  Future<void> saveKeys(List<String> keys);
}

/// Implementación real, sobre el mismo almacenamiento que [SecureLockStore].
class SecureNequiSeenStore implements NequiSeenStore {
  SecureNequiSeenStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  final FlutterSecureStorage _storage;

  static const String _key = 'korofin.nequi_seen_keys';

  @override
  Future<List<String>> readKeys() async {
    final String? raw = await _storage.read(key: _key);
    if (raw == null || raw.isEmpty) return <String>[];
    try {
      return (jsonDecode(raw) as List<dynamic>).cast<String>();
    } on FormatException {
      return <String>[];
    }
  }

  @override
  Future<void> saveKeys(List<String> keys) =>
      _storage.write(key: _key, value: jsonEncode(keys));
}

/// Implementación en memoria para tests y previews.
class InMemoryNequiSeenStore implements NequiSeenStore {
  List<String> _keys = <String>[];

  @override
  Future<List<String>> readKeys() async => List<String>.of(_keys);

  @override
  Future<void> saveKeys(List<String> keys) async => _keys = List<String>.of(keys);
}
