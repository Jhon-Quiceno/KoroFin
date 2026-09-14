import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../../core/auth/biometric_authenticator.dart';
import '../../core/storage/lock_store.dart';

/// Regex del PIN válido: exactamente 4 dígitos numéricos.
final RegExp _pinFormat = RegExp(r'^\d{4}$');

/// Reglas de negocio del bloqueo de la app: habilitar/deshabilitar, hashear y
/// verificar el PIN, aplicar el lockout temporal ante fuerza bruta, y delegar
/// la biometría al [BiometricAuthenticator].
///
/// El PIN nunca se guarda ni se compara en claro: se hashea con SHA-256 más
/// una sal aleatoria de 16 bytes generada al activarse el bloqueo.
///
/// Un PIN de 4 dígitos son solo 10.000 combinaciones, así que sin límite de
/// intentos alguien con el teléfono en la mano lo fuerza bruta en minutos. Por
/// eso, a partir de [_attemptsBeforeLockout] intentos fallidos consecutivos se
/// aplica un lockout temporal escalonado (ver [_lockoutTiers]) durante el cual
/// ni un PIN correcto desbloquea. El contador y el instante de fin del
/// lockout se persisten en [LockStore]: matar la app no resetea el castigo.
///
/// La biometría es un canal aparte y nunca se ve afectada por este lockout:
/// el sistema operativo ya aplica su propio límite de intentos biométricos.
class LockRepository {
  LockRepository(this._store, this._biometrics, {DateTime Function()? now})
      : _now = now ?? DateTime.now;

  final LockStore _store;
  final BiometricAuthenticator _biometrics;
  final DateTime Function() _now;

  static const int _saltLengthBytes = 16;

  /// Intentos fallidos que se toleran como errores de tipeo normales antes de
  /// empezar a castigar.
  static const int _attemptsBeforeLockout = 5;

  /// Duración de cada tramo de castigo, en el orden en que se van aplicando a
  /// medida que se acumulan más intentos fallidos por encima de
  /// [_attemptsBeforeLockout]. Escalona 30s → 1m → 2m → 5m → 15m: lo bastante
  /// disuasivo para desalentar probar las 10.000 combinaciones posibles de un
  /// PIN de 4 dígitos, sin dejar afuera de forma permanente a un usuario
  /// legítimo que se equivoca ocasionalmente. El último tramo se repite como
  /// tope una vez agotada la lista.
  static const List<Duration> _lockoutTiers = [
    Duration(seconds: 30),
    Duration(minutes: 1),
    Duration(minutes: 2),
    Duration(minutes: 5),
    Duration(minutes: 15),
  ];

  static Duration get _maxLockoutDuration => _lockoutTiers.last;

  Future<bool> isEnabled() => _store.isEnabled();

  Future<bool> isBiometricAvailable() => _biometrics.isAvailable();

  /// Activa el bloqueo definiendo el PIN de 4 dígitos. Lanza [ArgumentError]
  /// si el PIN no tiene el formato esperado. Arranca sin intentos fallidos ni
  /// lockout previos.
  Future<void> enableWithPin(String pin) async {
    if (!_pinFormat.hasMatch(pin)) {
      throw ArgumentError('El PIN debe tener exactamente 4 dígitos.');
    }
    final String salt = _generateSalt();
    final String hash = _hashPin(pin, salt);
    await _store.savePin(hash, salt);
    await _store.setEnabled(true);
    await _resetFailedAttempts();
  }

  /// Desactiva el bloqueo, borra el PIN guardado y limpia cualquier lockout
  /// pendiente.
  Future<void> disable() async {
    await _store.setEnabled(false);
    await _store.clearPin();
    await _resetFailedAttempts();
  }

  /// Lockout de PIN vigente en este momento, o `null` si se puede reintentar.
  ///
  /// Corrige por su cuenta un caso de reloj del dispositivo retrocedido de
  /// forma implausible (más allá del tramo máximo de castigo): en vez de
  /// dejar el lockout vigente "para siempre", lo recorta a
  /// [_maxLockoutDuration] contado desde ahora. Esto no evita que alguien
  /// *adelante* el reloj para saltarse el castigo — hacerlo bien requeriría
  /// una fuente de tiempo confiable (reloj monótono del SO o del backend) que
  /// no está disponible acá —, pero sí evita que un reloj atrasado deje a un
  /// usuario legítimo bloqueado de forma indefinida.
  Future<Duration?> currentLockout() async {
    final DateTime? lockoutUntil = await _store.readLockoutUntil();
    if (lockoutUntil == null) return null;

    final DateTime now = _now();
    Duration remaining = lockoutUntil.difference(now);

    if (remaining > _maxLockoutDuration) {
      final DateTime corrected = now.add(_maxLockoutDuration);
      await _store.saveLockoutUntil(corrected);
      remaining = _maxLockoutDuration;
    }

    if (remaining <= Duration.zero) {
      await _store.saveLockoutUntil(null);
      return null;
    }
    return remaining;
  }

  /// Compara el PIN ingresado contra el hash guardado. `false` si no hay PIN
  /// configurado o si hay un lockout vigente (sin contar esto como un nuevo
  /// intento fallido).
  Future<bool> verifyPin(String pin) async {
    if (await currentLockout() != null) return false;

    final String? storedHash = await _store.readPinHash();
    final String? salt = await _store.readSalt();
    final bool valid =
        storedHash != null && salt != null && _hashPin(pin, salt) == storedHash;

    if (valid) {
      await _resetFailedAttempts();
      return true;
    }

    await _registerFailedAttempt();
    return false;
  }

  /// Dispara el prompt biométrico nativo. `false` ante cualquier fallo,
  /// cancelación o dispositivo sin biometría — el llamador debe caer al PIN.
  /// Un desbloqueo exitoso también resetea el contador de intentos del PIN.
  Future<bool> authenticateWithBiometrics({
    String reason = 'Autentícate para desbloquear KoroFin',
  }) async {
    final bool ok = await _biometrics.authenticate(reason: reason);
    if (ok) await _resetFailedAttempts();
    return ok;
  }

  Future<void> _resetFailedAttempts() async {
    await _store.saveFailedAttempts(0);
    await _store.saveLockoutUntil(null);
  }

  Future<void> _registerFailedAttempt() async {
    final int attempts = (await _store.readFailedAttempts()) + 1;
    await _store.saveFailedAttempts(attempts);

    final Duration? lockout = _lockoutDurationFor(attempts);
    if (lockout != null) {
      await _store.saveLockoutUntil(_now().add(lockout));
    }
  }

  Duration? _lockoutDurationFor(int failedAttempts) {
    if (failedAttempts < _attemptsBeforeLockout) return null;
    final int tierIndex = failedAttempts - _attemptsBeforeLockout;
    final int clampedIndex = tierIndex.clamp(0, _lockoutTiers.length - 1);
    return _lockoutTiers[clampedIndex];
  }

  String _generateSalt() {
    final Random random = Random.secure();
    final List<int> bytes =
        List<int>.generate(_saltLengthBytes, (_) => random.nextInt(256));
    return base64Url.encode(bytes);
  }

  String _hashPin(String pin, String salt) =>
      sha256.convert(utf8.encode('$salt:$pin')).toString();
}
