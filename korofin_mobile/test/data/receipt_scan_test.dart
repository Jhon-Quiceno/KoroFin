import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/ai_repository.dart';
import 'package:korofin_mobile/models/ai.dart';

import '../support/capturing_adapter.dart';

AiRepository _repo(CapturingAdapter a) => AiRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  test('scanReceipt manda el data URI y parsea un recibo válido', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'isReceipt': true,
      'description': 'Panadería San José',
      'amount': 68500,
      'movementType': 'EXPENSE',
      'categoryId': 2,
      'categoryName': 'Comida',
    });

    final r = await _repo(a).scanReceipt('data:image/jpeg;base64,AAAA');

    expect(a.lastRequest.path, '/api/receipts/scan');
    expect((a.lastRequest.data as Map)['imageDataUri'],
        'data:image/jpeg;base64,AAAA');
    expect(r.isReceipt, isTrue);
    expect(r.amount, 68500);
    expect(r.isIncome, isFalse);
    expect(r.categoryName, 'Comida');
  });

  test('scanReceipt mapea isReceipt=false sin romper', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'isReceipt': false,
      'description': null,
      'amount': null,
      'movementType': null,
      'categoryId': null,
      'categoryName': null,
    });

    final ReceiptExtraction r =
        await _repo(a).scanReceipt('data:image/jpeg;base64,ZZ');

    expect(r.isReceipt, isFalse);
    expect(r.amount, isNull);
  });

  test('movementType INCOME marca isIncome', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'isReceipt': true,
      'description': 'Reembolso',
      'amount': 12000,
      'movementType': 'INCOME',
      'categoryId': null,
      'categoryName': null,
    });

    final r = await _repo(a).scanReceipt('data:image/jpeg;base64,QQ');

    expect(r.isIncome, isTrue);
  });
}
