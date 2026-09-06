import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/models/category.dart';

void main() {
  test('fromJson mapea id, name y type', () {
    final category = Category.fromJson(<String, dynamic>{
      'id': 12,
      'name': 'Comida',
      'type': 'EXPENSE',
    });

    expect(category.id, 12);
    expect(category.name, 'Comida');
    expect(category.kind, CategoryKind.expense);
  });

  test('toJson solo manda name y type (el id va en la URL)', () {
    const category =
        Category(id: 3, name: 'Salario', kind: CategoryKind.income);

    expect(category.toJson(), <String, dynamic>{
      'name': 'Salario',
      'type': 'INCOME',
    });
  });

  test('un type desconocido cae a expense sin romper', () {
    final category = Category.fromJson(<String, dynamic>{
      'id': 1,
      'name': 'Rara',
      'type': 'OTRO',
    });

    expect(category.kind, CategoryKind.expense);
  });
}
