import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/formatters.dart';

/// `intl` separa el símbolo del número con un espacio duro; se normaliza para
/// comparar contra texto escrito a mano.
String _plain(String formatted) => formatted.replaceAll(' ', ' ');

void main() {
  group('AppFormatters.parseAmount', () {
    test('coma decimal con puntos de miles: 172.875,24', () {
      expect(AppFormatters.parseAmount('172.875,24'), 172875.24);
    });

    test('coma decimal sin separador de miles: 172875,24', () {
      expect(AppFormatters.parseAmount('172875,24'), 172875.24);
    });

    test('un solo decimal: 1500,5', () {
      expect(AppFormatters.parseAmount('1500,5'), 1500.5);
    });

    test('punto seguido de tres dígitos es separador de miles: 50.000', () {
      expect(AppFormatters.parseAmount('50.000'), 50000);
    });

    test('varios puntos son separadores de miles: 1.250.000', () {
      expect(AppFormatters.parseAmount('1.250.000'), 1250000);
    });

    test('punto seguido de uno o dos dígitos es decimal (teclado numérico): 172875.24',
        () {
      expect(AppFormatters.parseAmount('172875.24'), 172875.24);
      expect(AppFormatters.parseAmount('10.5'), 10.5);
    });

    test('entero sin separadores sigue funcionando: 20000', () {
      expect(AppFormatters.parseAmount('20000'), 20000);
    });

    test('ignora el signo pesos y los espacios', () {
      expect(AppFormatters.parseAmount(r' $ 172.875,24 '), 172875.24);
    });

    test('más de dos decimales no es un monto válido', () {
      expect(AppFormatters.parseAmount('10,255'), isNull);
    });

    test('texto vacío o no numérico no es un monto válido', () {
      expect(AppFormatters.parseAmount(''), isNull);
      expect(AppFormatters.parseAmount('abc'), isNull);
      expect(AppFormatters.parseAmount('1,2,3'), isNull);
    });
  });

  group('AppFormatters.currency', () {
    test('un monto con centavos muestra dos decimales', () {
      expect(_plain(AppFormatters.currency(172875.24)), r'172.875,24 $');
    });

    test('un monto entero se muestra sin decimales, como antes', () {
      expect(_plain(AppFormatters.currency(50000)), r'50.000 $');
    });

    test('un monto con un solo decimal completa los centavos', () {
      expect(_plain(AppFormatters.currency(1500.5)), r'1.500,50 $');
    });
  });

  group('AppFormatters.amountInput', () {
    test('un monto con centavos se edita con coma decimal', () {
      expect(AppFormatters.amountInput(172875.24), '172875,24');
    });

    test('un monto entero se edita sin decimales, como antes', () {
      expect(AppFormatters.amountInput(50000), '50000');
    });

    test('lo que produce se vuelve a leer con el mismo valor', () {
      expect(
        AppFormatters.parseAmount(AppFormatters.amountInput(172875.24)),
        172875.24,
      );
    });
  });
}
