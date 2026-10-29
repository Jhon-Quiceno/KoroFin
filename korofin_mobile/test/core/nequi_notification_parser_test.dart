import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/notifications/nequi_notification_parser.dart';

void main() {
  group('NequiNotificationParser.parse', () {
    group('al recibir dinero (S4.1)', () {
      test('título "¡Te enviaron plata!" detecta ingreso con el monto', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Te enviaron plata!',
          body:
              'Juan Pérez te envió \$50.000. Tu disponible ahora es \$120.000.',
        );
        expect(result, isA<NequiIncomeDetected>());
        expect((result as NequiIncomeDetected).amount, 50000);
      });

      test('título "¡Te llegó una plata!" detecta ingreso con el monto', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Te llegó una plata!',
          body:
              'María Gómez te envió \$15.500. Tu disponible ahora es \$200.000.',
        );
        expect(result, isA<NequiIncomeDetected>());
        expect((result as NequiIncomeDetected).amount, 15500);
      });

      test('mensaje de Transfiya detecta ingreso con el monto', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Te enviaron plata!',
          body:
              '¡Te enviaron \$30.000 por Transfiya! Entra a Nequi para aceptarla.',
        );
        expect(result, isA<NequiIncomeDetected>());
        expect((result as NequiIncomeDetected).amount, 30000);
      });
    });

    group('al enviar dinero (S4.2)', () {
      test('título "¡Envío exitoso!" detecta egreso con el monto', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Envío exitoso!',
          body:
              'Enviaste \$20.000 a Carlos Ramírez. Tu disponible ahora es \$80.000.',
        );
        expect(result, isA<NequiExpenseDetected>());
        final NequiExpenseDetected expense = result as NequiExpenseDetected;
        expect(expense.amount, 20000);
        expect(expense.description, 'Carlos Ramírez');
      });

      test('título "¡Plata enviada!" detecta egreso con el monto y el destinatario', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Plata enviada!',
          body:
              'Enviaste \$5.000 a 3001234567. Tu disponible ahora es \$75.000.',
        );
        expect(result, isA<NequiExpenseDetected>());
        final NequiExpenseDetected expense = result as NequiExpenseDetected;
        expect(expense.amount, 5000);
        expect(expense.description, '3001234567');
      });
    });

    group('al pagar algo (S4.3)', () {
      test('título "¡Pago exitoso!" detecta egreso con el monto y el comercio', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Pago exitoso!',
          body:
              'Pagaste \$45.000 en Éxito Envigado. Tu disponible ahora es \$35.000.',
        );
        expect(result, isA<NequiExpenseDetected>());
        final NequiExpenseDetected expense = result as NequiExpenseDetected;
        expect(expense.amount, 45000);
        expect(expense.description, 'Éxito Envigado');
      });

      test('título "¡Compra exitosa!" detecta egreso con el monto y el servicio', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Compra exitosa!',
          body:
              'Pagaste \$12.900 en Netflix. Tu disponible ahora es \$22.100.',
        );
        expect(result, isA<NequiExpenseDetected>());
        final NequiExpenseDetected expense = result as NequiExpenseDetected;
        expect(expense.amount, 12900);
        expect(expense.description, 'Netflix');
      });
    });

    group('pantalla bloqueada (S4, aspectos clave)', () {
      test('sin texto para parsear se ignora', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: 'Nequi: Tienes una nueva notificación',
          body: '',
        );
        expect(result, isA<NequiIgnored>());
      });
    });

    group('notificaciones no reconocidas', () {
      test('texto sin ninguno de los patrones de Nequi se ignora', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: 'Resumen semanal',
          body: 'Revisá tu resumen de gastos de esta semana en Nequi.',
        );
        expect(result, isA<NequiIgnored>());
      });

      test('cuerpo vacío se ignora sin importar el título', () {
        final NequiParseResult result = NequiNotificationParser.parse(
          title: '¡Pago exitoso!',
          body: '',
        );
        expect(result, isA<NequiIgnored>());
      });
    });
  });
}
