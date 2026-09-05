import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/card_repository.dart';
import 'package:korofin_mobile/models/credit_card.dart';

import '../support/capturing_adapter.dart';

Map<String, dynamic> _cardJson() => <String, dynamic>{
      'id': 2,
      'name': 'Visa Oro',
      'bank': 'Bancolombia',
      'franchise': 'VISA',
      'creditLimit': 5000000,
      'monthlyRate': 2.1,
      'cutoffDay': 15,
      'paymentDueDay': 3,
      'currentBalance': 1200000,
      'availableCredit': 3800000,
      'lastCutoffDate': null,
      'createdAt': '2026-09-01T00:00:00Z',
      'updatedAt': '2026-09-01T00:00:00Z',
    };

CardRepository _repo(CapturingAdapter a) => CardRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  test('CreditCard.fromJson y usageRatio', () {
    final c = CreditCard.fromJson(_cardJson());
    expect(c.franchise, CardFranchise.visa);
    expect(c.usageRatio, closeTo(0.24, 0.001));
    expect(c.availableCredit, 3800000);
  });

  test('CardMovement.fromJson mapea el tipo y detecta cuotas', () {
    final m = CardMovement.fromJson(<String, dynamic>{
      'id': 9,
      'cardId': 2,
      'type': 'INSTALLMENT_PURCHASE',
      'amount': 900000,
      'date': '2026-09-03',
      'description': 'TV',
      'cardBalanceAfter': 2100000,
      'expenseId': null,
      'installmentPlanId': 5,
      'createdAt': '2026-09-03T00:00:00Z',
    });
    expect(m.kind, CardMovementKind.installmentPurchase);
    expect(m.hasInstallments, isTrue);
    expect(m.kind.reducesBalance, isFalse);
  });

  test('create manda la franquicia y el cupo', () async {
    final a = CapturingAdapter(status: 201, body: _cardJson());
    await _repo(a).create(
      name: 'Visa Oro',
      bank: 'Bancolombia',
      franchise: CardFranchise.visa,
      creditLimit: 5000000,
      monthlyRate: 2.1,
      cutoffDay: 15,
      paymentDueDay: 3,
    );
    final body = a.lastRequest.data as Map<String, dynamic>;
    expect(body['franchise'], 'VISA');
    expect(body['creditLimit'], 5000000);
  });

  test('registerPurchase manda installmentCount cuando hay cuotas', () async {
    final a = CapturingAdapter(status: 201, body: <String, dynamic>{
      'id': 1,
      'cardId': 2,
      'type': 'INSTALLMENT_PURCHASE',
      'amount': 600000,
      'date': '2026-09-05',
      'description': null,
      'cardBalanceAfter': 600000,
      'expenseId': null,
      'installmentPlanId': 1,
      'createdAt': '2026-09-05T00:00:00Z',
    });
    await _repo(a).registerPurchase(2, amount: 600000, installmentCount: 6);
    expect(a.lastRequest.path, '/api/cards/2/purchases');
    expect((a.lastRequest.data as Map)['installmentCount'], 6);
  });

  test('update no manda franchise ni creditLimit', () async {
    final a = CapturingAdapter(body: _cardJson());
    await _repo(a).update(2,
        name: 'Nueva', monthlyRate: 2.0, cutoffDay: 10, paymentDueDay: 1);
    final body = a.lastRequest.data as Map<String, dynamic>;
    expect(body.containsKey('franchise'), isFalse);
    expect(body.containsKey('creditLimit'), isFalse);
  });
}
