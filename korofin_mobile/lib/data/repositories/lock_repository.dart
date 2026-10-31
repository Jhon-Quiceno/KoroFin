import '../../core/auth/biometric_authenticator.dart';
import '../../core/storage/lock_store.dart';

/// Reglas de negocio del bloqueo de la app: habilitar/deshabilitar y delegar
/// la autenticación al [BiometricAuthenticator].
///
/// El bloqueo es solo biometría, sin PIN: el sistema operativo ya aplica su
/// propio límite de intentos y su propio lockout ante fuerza bruta, así que
/// no hace falta duplicar esa lógica acá.
class LockRepository {
  LockRepository(this._store, this._biometrics);

  final LockStore _store;
  final BiometricAuthenticator _biometrics;

  Future<bool> isEnabled() => _store.isEnabled();

  Future<bool> isBiometricAvailable() => _biometrics.isAvailable();

  /// Activa el bloqueo. No bloquea la sesión actual: recién aplica en el
  /// próximo regreso desde background.
  Future<void> enable() => _store.setEnabled(true);

  Future<void> disable() => _store.setEnabled(false);

  /// Dispara el prompt biométrico nativo. `false` ante cualquier fallo,
  /// cancelación o error de plataforma (nunca lanza).
  Future<bool> authenticateWithBiometrics({
    String reason = 'Autentícate para desbloquear KoroFin',
  }) =>
      _biometrics.authenticate(reason: reason);
}
