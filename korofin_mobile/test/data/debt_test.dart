import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/debt_repository.dart';
import 'package:korofin_mobile/models/debt.dart';

import '../support/capturing_adapter.dart';

Map<String, dynamic> _debtJson({double remaining = 6000, double total = 10000}) =>
    <String, dynamic>{
      'id': 4,
      'name': 'Crédito',
      'totalAmount': total,
      'remainingAmount': remaining,
      'interestRate': 1.8,
      'dueDate': '2027-01-31',
      'createdAt': '2026-09-01T00:00:00Z',
      'updatedAt': '2026-09-01T00:00:00Z',
    };

DebtRepository _repo(CapturingAdapter a) => DebtRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  group('modelo Debt', () {
    test('fromJson y campos derivados', () {
      final d = Debt.fromJson(_debtJson(remaining: 4000, total: 10000));
      expect(d.paidAmount, 6000);
      expect(d.progress, closeTo(0.6, 0.001));
      expect(d.interestRate, 1.8);
      expect(d.dueDate, DateTime(2027, 1, 31));
      expect(d.isSettled, isFalse);
    });

    test('deuda sin fecha ni interés', () {
      final d = Debt.fromJson(<String, dynamic>{
        'id': 1,
        'name': 'X',
        'totalAmount': 100,
        'remainingAmount': 0,
        'interestRate': null,
        'dueDate': null,
      });
      expect(d.dueDate, isNull);
      expect(d.interestRate, isNull);
      expect(d.isSettled, isTrue);
      expect(d.progress, 1);
    });
  });

  group('DebtRepository', () {
    test('create manda name/totalAmount y fecha ISO', () async {
      final a = CapturingAdapter(status: 201, body: _debtJson());
      final debt = await _repo(a).create(
        name: 'Crédito',
        totalAmount: 10000,
        interestRate: 1.8,
        dueDate: DateTime(2027, 1, 31),
      );
      expect(a.lastRequest.path, '/api/debts');
      final body = a.lastRequest.data as Map<String, dynamic>;
      expect(body['name'], 'Crédito');
      expect(body['totalAmount'], 10000);
      expect(body['dueDate'], '2027-01-31');
      expect(debt.id, 4);
    });

    test('update no manda totalAmount', () async {
      final a = CapturingAdapter(body: _debtJson());
      await _repo(a).update(4, name: 'Nuevo');
      expect(a.lastRequest.method, 'PUT');
      expect((a.lastRequest.data as Map).containsKey('totalAmount'), isFalse);
    });

    test('addPayment devuelve el abono creado', () async {
      final a = CapturingAdapter(status: 201, body: <String, dynamic>{
        'id': 10,
        'debtId': 4,
        'amount': 500,
        'paymentDate': '2026-09-05',
        'note': null,
        'createdAt': '2026-09-05T00:00:00Z',
        'expenseId': 7,
      });
      final p = await _repo(a).addPayment(4, amount: 500);
      expect(a.lastRequest.path, '/api/debts/4/payments');
      expect(p, isA<DebtPayment>());
      expect(p.amount, 500);
    });

    test('addCharge devuelve la DEUDA actualizada', () async {
      final a = CapturingAdapter(status: 201, body: _debtJson(remaining: 8000));
      final d = await _repo(a).addCharge(4, amount: 2000);
      expect(a.lastRequest.path, '/api/debts/4/charges');
      expect(d, isA<Debt>());
      expect(d.remainingAmount, 8000);
    });
  });
}
