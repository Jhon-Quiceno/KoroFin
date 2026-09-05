import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/recurring_payment_repository.dart';
import 'package:korofin_mobile/models/recurring_payment.dart';

import '../support/capturing_adapter.dart';

Map<String, dynamic> _json({bool active = true, String next = '2026-10-01'}) =>
    <String, dynamic>{
      'id': 3,
      'name': 'Netflix',
      'amount': 44900,
      'frequency': 'MONTHLY',
      'nextPaymentDate': next,
      'isActive': active,
      'createdAt': '2026-09-01T00:00:00Z',
      'updatedAt': '2026-09-01T00:00:00Z',
    };

RecurringPaymentRepository _repo(CapturingAdapter a) =>
    RecurringPaymentRepository(ApiClient(
        readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  test('fromJson mapea frecuencia y estado', () {
    final r = RecurringPayment.fromJson(_json(active: false));
    expect(r.frequency, RecurringFrequency.monthly);
    expect(r.isActive, isFalse);
    expect(r.isDueSoon, isFalse); // pausado nunca vence pronto
  });

  test('create manda firstPaymentDate en ISO', () async {
    final a = CapturingAdapter(status: 201, body: _json());
    await _repo(a).create(
      name: 'Netflix',
      amount: 44900,
      frequency: RecurringFrequency.monthly,
      firstPaymentDate: DateTime(2026, 10, 1),
    );
    expect(a.lastRequest.path, '/api/recurring');
    expect((a.lastRequest.data as Map)['firstPaymentDate'], '2026-10-01');
  });

  test('update no manda firstPaymentDate', () async {
    final a = CapturingAdapter(body: _json());
    await _repo(a).update(3,
        name: 'Netflix', amount: 50000, frequency: RecurringFrequency.weekly);
    expect(a.lastRequest.method, 'PUT');
    expect((a.lastRequest.data as Map).containsKey('firstPaymentDate'), isFalse);
  });

  test('toggle hace PATCH y devuelve el recurrente', () async {
    final a = CapturingAdapter(body: _json(active: false));
    final r = await _repo(a).toggle(3);
    expect(a.lastRequest.method, 'PATCH');
    expect(a.lastRequest.path, '/api/recurring/3/toggle');
    expect(r.isActive, isFalse);
  });

  test('pay desanida recurringPayment de la respuesta', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'recurringPayment': _json(next: '2026-11-01'),
      'expenseId': 42,
    });
    final r = await _repo(a).pay(3);
    expect(a.lastRequest.path, '/api/recurring/3/pay');
    expect(r.nextPaymentDate, DateTime(2026, 11, 1));
  });
}
