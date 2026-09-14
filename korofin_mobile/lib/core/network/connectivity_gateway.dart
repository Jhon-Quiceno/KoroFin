import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';

/// Abstrae la detección de conectividad de red para poder inyectar un fake en
/// los tests — el plugin real no está disponible en `flutter test`.
abstract interface class ConnectivityGateway {
  /// `true` si el dispositivo tiene alguna interfaz de red activa ahora
  /// mismo. No garantiza que el backend sea alcanzable (podría estar caído
  /// igual), solo que vale la pena intentar la sincronización.
  Future<bool> hasConnection();

  /// Emite el nuevo estado (`true`/`false`) cada vez que cambia.
  Stream<bool> get onConnectivityChanged;
}

/// Implementación real sobre `connectivity_plus`.
class DeviceConnectivityGateway implements ConnectivityGateway {
  DeviceConnectivityGateway({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity();

  final Connectivity _connectivity;

  @override
  Future<bool> hasConnection() async =>
      _hasAny(await _connectivity.checkConnectivity());

  @override
  Stream<bool> get onConnectivityChanged =>
      _connectivity.onConnectivityChanged.map(_hasAny);

  bool _hasAny(List<ConnectivityResult> results) =>
      results.any((result) => result != ConnectivityResult.none);
}

/// Implementación controlable a mano para tests y previews.
class FakeConnectivityGateway implements ConnectivityGateway {
  FakeConnectivityGateway({bool connected = true}) : _connected = connected;

  bool _connected;
  final StreamController<bool> _controller =
      StreamController<bool>.broadcast();

  bool get connected => _connected;

  @override
  Future<bool> hasConnection() async => _connected;

  @override
  Stream<bool> get onConnectivityChanged => _controller.stream;

  /// Simula un cambio de conectividad, como dispararía el plugin real.
  void setConnected(bool value) {
    _connected = value;
    _controller.add(value);
  }

  void dispose() => _controller.close();
}
