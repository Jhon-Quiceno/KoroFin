import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/certificate_pinning.dart';

void main() {
  group('certificateDerMatchesPinnedHash', () {
    test('coincide cuando el hash esperado es el SHA-256 del DER', () {
      final List<int> der = <int>[1, 2, 3, 4, 5];
      final String expected = sha256.convert(der).toString();

      expect(certificateDerMatchesPinnedHash(der, expected), isTrue);
    });

    test('no coincide con un certificado distinto', () {
      final List<int> der = <int>[1, 2, 3, 4, 5];
      final String expected = sha256.convert(<int>[9, 9, 9]).toString();

      expect(certificateDerMatchesPinnedHash(der, expected), isFalse);
    });

    test('no distingue mayúsculas/minúsculas ni espacios en el hash esperado',
        () {
      final List<int> der = <int>[10, 20, 30];
      final String expected = sha256.convert(der).toString();

      expect(
        certificateDerMatchesPinnedHash(der, ' ${expected.toUpperCase()} '),
        isTrue,
      );
    });
  });
}
