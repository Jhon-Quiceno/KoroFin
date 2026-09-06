import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/models/movement.dart';

void main() {
  test('fromExpenseJson mapea todos los campos, incluido el método de pago', () {
    final m = Movement.fromExpenseJson(<String, dynamic>{
      'id': 5,
      'amount': 12345.67,
      'description': 'Almuerzo',
      'date': '2026-09-03',
      'paymentMethod': 'DEBIT_CARD',
      'categoryId': 2,
      'categoryName': 'Comida',
    });

    expect(m.type, MovementType.expense);
    expect(m.amount, 12345.67);
    expect(m.date, DateTime(2026, 9, 3));
    expect(m.paymentMethod, PaymentMethod.debitCard);
    expect(m.categoryName, 'Comida');
    expect(m.title, 'Almuerzo');
  });

  test('fromIncomeJson no trae método de pago y el title cae a la categoría',
      () {
    final m = Movement.fromIncomeJson(<String, dynamic>{
      'id': 8,
      'amount': 3000000,
      'description': null,
      'date': '2026-09-01',
      'categoryId': 9,
      'categoryName': 'Salario',
    });

    expect(m.type, MovementType.income);
    expect(m.paymentMethod, isNull);
    expect(m.title, 'Salario');
  });

  test('toExpenseJson: fecha ISO, descripción omitida si vacía, método por defecto',
      () {
    final draft = MovementDraft(amount: 100, date: DateTime(2026, 1, 5));
    final json = draft.toExpenseJson();

    expect(json['date'], '2026-01-05');
    expect(json.containsKey('description'), isFalse);
    expect(json['paymentMethod'], 'CASH');
    expect(json.containsKey('categoryId'), isFalse);
  });

  test('toIncomeJson incluye categoryId y descripción cuando están', () {
    final draft = MovementDraft(
      amount: 50,
      date: DateTime(2026, 2, 10),
      description: 'Devolución',
      categoryId: 4,
    );
    final json = draft.toIncomeJson();

    expect(json['description'], 'Devolución');
    expect(json['categoryId'], 4);
    expect(json.containsKey('paymentMethod'), isFalse);
  });
}
