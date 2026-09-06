import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/expense_repository.dart';
import 'package:korofin_mobile/data/repositories/income_repository.dart';
import 'package:korofin_mobile/models/movement.dart';

import '../support/capturing_adapter.dart';

Map<String, dynamic> _page(List<Map<String, dynamic>> content,
        {bool last = true, int number = 0}) =>
    <String, dynamic>{
      'content': content,
      'number': number,
      'totalElements': content.length,
      'totalPages': last ? number + 1 : number + 2,
      'last': last,
    };

Map<String, dynamic> _expenseRow(int id) => <String, dynamic>{
      'id': id,
      'amount': 1000,
      'description': 'Gasto $id',
      'date': '2026-09-0${id % 9 + 1}',
      'paymentMethod': 'CASH',
      'categoryId': null,
      'categoryName': null,
    };

(ExpenseRepository, CapturingAdapter) _expenseRepo() {
  final adapter = CapturingAdapter();
  final dio = Dio()..httpClientAdapter = adapter;
  return (ExpenseRepository(ApiClient(readAccessToken: () => 't', dio: dio)),
      adapter);
}

(IncomeRepository, CapturingAdapter) _incomeRepo() {
  final adapter = CapturingAdapter();
  final dio = Dio()..httpClientAdapter = adapter;
  return (IncomeRepository(ApiClient(readAccessToken: () => 't', dio: dio)),
      adapter);
}

void main() {
  group('ExpenseRepository', () {
    test('list manda page/size y filtros, y parsea el Page', () async {
      final (repo, adapter) = _expenseRepo();
      adapter.body = _page(<Map<String, dynamic>>[_expenseRow(1), _expenseRow(2)],
          last: false);

      final page = await repo.list(
        page: 1,
        size: 10,
        categoryId: 7,
        from: DateTime(2026, 9, 1),
        paymentMethod: PaymentMethod.creditCard,
      );

      expect(adapter.lastRequest.path, '/api/expenses');
      final q = adapter.lastRequest.queryParameters;
      expect(q['page'], 1);
      expect(q['size'], 10);
      expect(q['categoryId'], 7);
      expect(q['from'], '2026-09-01');
      expect(q['paymentMethod'], 'CREDIT_CARD');
      expect(page.content, hasLength(2));
      expect(page.hasMore, isTrue);
    });

    test('create hace POST y devuelve el Movement', () async {
      final (repo, adapter) = _expenseRepo();
      adapter.body = _expenseRow(3);

      final created = await repo.create(
          MovementDraft(amount: 500, date: DateTime(2026, 9, 2)));

      expect(adapter.lastRequest.method, 'POST');
      expect(created.id, 3);
      expect(created.type, MovementType.expense);
    });

    test('delete hace DELETE a /api/expenses/{id}', () async {
      final (repo, adapter) = _expenseRepo();
      adapter.status = 204;

      await repo.delete(3);

      expect(adapter.lastRequest.method, 'DELETE');
      expect(adapter.lastRequest.path, '/api/expenses/3');
    });
  });

  group('IncomeRepository', () {
    test('list manda month/year cuando se pasan', () async {
      final (repo, adapter) = _incomeRepo();
      adapter.body = _page(<Map<String, dynamic>>[]);

      await repo.list(month: 9, year: 2026);

      final q = adapter.lastRequest.queryParameters;
      expect(q['month'], 9);
      expect(q['year'], 2026);
    });

    test('create hace POST a /api/incomes sin paymentMethod', () async {
      final (repo, adapter) = _incomeRepo();
      adapter.body = <String, dynamic>{
        'id': 1,
        'amount': 100,
        'description': null,
        'date': '2026-09-01',
        'categoryId': null,
        'categoryName': null,
      };

      await repo.create(MovementDraft(amount: 100, date: DateTime(2026, 9, 1)));

      expect(adapter.lastRequest.path, '/api/incomes');
      expect((adapter.lastRequest.data as Map).containsKey('paymentMethod'),
          isFalse);
    });
  });
}
