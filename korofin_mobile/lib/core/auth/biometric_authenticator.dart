import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';

/// Autenticación biométrica del dispositivo (Face ID / huella).
///
/// Es una interfaz para poder inyectar un fake en los tests: el plugin nativo
/// no está disponible en `flutter test`.
abstract interface class BiometricAuthenticator {
  /// True si el dispositivo soporta biometría y tiene al menos una
  /// registrada. El PIN sigue siendo el fallback obligatorio cuando esto da
  /// `false`.
  Future<bool> isAvailable();

  /// Dispara el prompt nativo de biometría. Devuelve `false` ante cualquier
  /// cancelación, fallo o error de plataforma (nunca lanza).
  Future<bool> authenticate({required String reason});
}

/// Implementación real sobre `local_auth`.
class DeviceBiometricAuthenticator implements BiometricAuthenticator {
  DeviceBiometricAuthenticator({LocalAuthentication? localAuth})
      : _auth = localAuth ?? LocalAuthentication();

  final LocalAuthentication _auth;

  @override
  Future<bool> isAvailable() async {
    try {
      final bool canCheckBiometrics = await _auth.canCheckBiometrics;
      final bool isDeviceSupported = await _auth.isDeviceSupported();
      if (!canCheckBiometrics || !isDeviceSupported) return false;
      final List<BiometricType> available =
          await _auth.getAvailableBiometrics();
      return available.isNotEmpty;
    } on PlatformException {
      return false;
    }
  }

  @override
  Future<bool> authenticate({required String reason}) async {
    try {
      return await _auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
        ),
      );
    } on PlatformException {
      // notAvailable/notEnrolled/lockedOut, cancelación, etc.: se resuelve
      // cayendo al PIN, nunca propagando la excepción a la pantalla.
      return false;
    }
  }
}
