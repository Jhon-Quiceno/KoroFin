import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

/// Compara el hash SHA-256 (hex) del certificado TLS de una conexión contra
/// el hash pineado esperado (ver [AppConfig.pinnedCertSha256]).
///
/// Hashea el certificado completo en DER, no solo la clave pública/SPKI: para
/// pinnear únicamente la SubjectPublicKeyInfo habría que parsear ASN.1 a
/// mano (`dart:io` no expone esos bytes por separado) o sumar una dependencia
/// nueva solo para eso, y no valía la pena para este alcance. La contrapartida
/// es que rotar el certificado —incluso conservando la misma clave pública—
/// invalida el pin y hay que recompilar con un `PINNED_CERT_SHA256` nuevo.
///
/// Cómo calcular el hash real de un certificado de producción:
///   openssl x509 -in cert.pem -outform der | openssl dgst -sha256
bool certificateMatchesPinnedHash(
  X509Certificate certificate,
  String expectedSha256Hex,
) =>
    certificateDerMatchesPinnedHash(certificate.der, expectedSha256Hex);

/// Misma comparación que [certificateMatchesPinnedHash], pero a partir de los
/// bytes DER crudos. Se separa así porque `X509Certificate` no tiene un
/// constructor público utilizable en tests (solo lo produce un handshake TLS
/// real), así que esta es la forma de testear la lógica de comparación de
/// forma aislada.
bool certificateDerMatchesPinnedHash(
  List<int> certificateDer,
  String expectedSha256Hex,
) =>
    _constantTimeEquals(
      utf8.encode(sha256.convert(certificateDer).toString()),
      utf8.encode(expectedSha256Hex.trim().toLowerCase()),
    );

/// Comparación en tiempo constante (mismo motivo que `MessageDigest.isEqual`
/// en el backend para el hash del refresh token): un hash pineado es, en los
/// hechos, un secreto que solo vive en el binario compilado, y `==` sobre
/// [String] corta en el primer byte distinto.
bool _constantTimeEquals(List<int> a, List<int> b) {
  if (a.length != b.length) return false;
  int diff = 0;
  for (int i = 0; i < a.length; i++) {
    diff |= a[i] ^ b[i];
  }
  return diff == 0;
}
