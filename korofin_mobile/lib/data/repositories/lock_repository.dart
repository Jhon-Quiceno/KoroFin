import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart' show visibleForTesting;

import '../../core/auth/biometric_authenticator.dart';
import '../../core/storage/lock_store.dart';

/// Regex del PIN válido: exactamente 4 dígitos numéricos.
final RegExp _pinFormat = RegExp(r'^\d{4}$');

/// Reglas de negocio del bloqueo de la app: habilitar/deshabilitar, hashear y
/// verificar el PIN, aplicar el lockout temporal ante fuerza bruta, y delegar
/// la biometría al [BiometricAuthenticator].
///
/// El PIN nunca se guarda ni se compara en claro: se hashea con
/// PBKDF2-HMAC-SHA256 (ver [_hashPin]) más una sal aleatoria de 16 bytes
/// generada al activarse el bloqueo.
///
/// Un PIN de 4 dígitos son solo 10.000 combinaciones, así que sin límite de
/// intentos alguien con el teléfono en la mano lo fuerza bruta en minutos. Por
/// eso, a partir de [_attemptsBeforeLockout] intentos fallidos consecutivos se
/// aplica un lockout temporal escalonado (ver [_lockoutTiers]) durante el cual
/// ni un PIN correcto desbloquea. El contador y el instante de fin del
/// lockout se persisten en [LockStore]: matar la app no resetea el castigo.
///
/// Ese lockout es una defensa solo a nivel de app/UI: alguien que extraiga el
/// `flutter_secure_storage` de un dispositivo rooteado/jailbreakeado se lo
/// salta por completo y ataca el hash guardado offline. Contra ESE escenario,
/// lo único que importa es que el hash sea costoso de invertir por
/// combinación probada — de ahí PBKDF2 con muchas iteraciones en vez de un
/// SHA-256 directo (que un atacante offline calcula millones de veces por
/// segundo).
///
/// Nota de migración: los PIN guardados en dispositivos con el esquema previo
/// (SHA-256 simple) dejan de validar con este cambio. No hay forma (ni falta
/// hace) de migrarlos automáticamente porque el PIN vive solo en el
/// dispositivo: el usuario simplemente vuelve a configurarlo con el flujo de
/// "activar bloqueo" ya existente en la UI.
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

  /// Iteraciones de PBKDF2. OWASP (2023) recomienda 210.000 para
  /// PBKDF2-HMAC-SHA256, pero eso corre en el hilo principal cada vez que se
  /// verifica el PIN (login/desbloqueo), sin `compute()` de por medio:
  /// benchmarks locales (`dart compile exe`, CPU de escritorio) dieron ~600-700
  /// ms para 210.000 iteraciones. En un dispositivo de gama baja eso puede
  /// traducirse en 1-2+ segundos de UI congelada al tipear el último dígito
  /// del PIN, lo cual es inaceptable para una pantalla de desbloqueo.
  ///
  /// Se optó por 50.000 iteraciones (~150-250 ms en el mismo benchmark) como
  /// balance: sigue multiplicando por 50.000x el costo de cada intento
  /// respecto de un SHA-256 directo (probar las 10.000 combinaciones de un
  /// PIN de 4 dígitos pasa de ser instantáneo a requerir 500 millones de
  /// operaciones HMAC-SHA256), mientras mantiene la verificación por debajo
  /// de ~300 ms también en equipos modestos. No es memory-hard (a diferencia
  /// de Argon2/scrypt), así que no es una defensa perfecta contra un
  /// atacante con GPU dedicada, pero es una mejora sustancial y proporcional
  /// al riesgo real de un PIN de 4 dígitos guardado localmente.
  static const int _pbkdf2Iterations = 50000;

  static const int _pbkdf2KeyLengthBytes = 32; // igual al output de SHA-256

  String _hashPin(String pin, String salt) => base64.encode(
        _pbkdf2HmacSha256(
          password: pin,
          salt: salt,
          iterations: _pbkdf2Iterations,
          keyLengthBytes: _pbkdf2KeyLengthBytes,
        ),
      );
}

/// PBKDF2 (RFC 8018) con HMAC-SHA256 como PRF.
///
/// El paquete `crypto` (única dependencia de criptografía del proyecto) no
/// expone una clase `Pbkdf2` en la versión resuelta (3.0.7): solo trae los
/// hashes y `Hmac` sueltos. Sumar `cryptography`/`pointycastle` solo para
/// esto no se justificaba, así que PBKDF2 se arma acá encima de [Hmac],
/// que sí expone el paquete.
///
/// [keyLengthBytes] es 32 (igual al output de SHA-256), así que alcanza con
/// un único bloque de PBKDF2 (no hace falta concatenar T_1, T_2, ...).
List<int> _pbkdf2HmacSha256({
  required String password,
  required String salt,
  required int iterations,
  required int keyLengthBytes,
}) {
  assert(iterations > 0, 'PBKDF2 necesita al menos 1 iteración.');
  assert(
    keyLengthBytes <= 32,
    'Esta implementación solo cubre un bloque de PBKDF2 (hasta 32 bytes).',
  );

  final Hmac hmac = Hmac(sha256, utf8.encode(password));

  // Bloque índice 1 en big-endian de 4 bytes, como pide el RFC 8018.
  final Uint8List blockIndex = (ByteData(4)..setUint32(0, 1)).buffer.asUint8List();
  final List<int> saltAndBlockIndex = <int>[...utf8.encode(salt), ...blockIndex];

  List<int> u = hmac.convert(saltAndBlockIndex).bytes;
  final List<int> t = List<int>.from(u);

  for (int i = 1; i < iterations; i++) {
    u = hmac.convert(u).bytes;
    for (int j = 0; j < t.length; j++) {
      t[j] ^= u[j];
    }
  }

  return t.sublist(0, keyLengthBytes);
}

/// Mismo cálculo que usa internamente [LockRepository._hashPin]. Se expone
/// solo para poder testear las propiedades de PBKDF2 (determinismo dado
/// `pin` + `salt`, sensibilidad a cambios en cualquiera de los dos) de forma
/// aislada, sin pasar por el flujo completo de habilitar/verificar PIN.
@visibleForTesting
String hashPinForTest(String pin, String salt) => base64.encode(
      _pbkdf2HmacSha256(
        password: pin,
        salt: salt,
        iterations: LockRepository._pbkdf2Iterations,
        keyLengthBytes: LockRepository._pbkdf2KeyLengthBytes,
      ),
    );
