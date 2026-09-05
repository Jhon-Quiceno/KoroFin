import '../../core/network/api_client.dart';
import '../../models/credit_card.dart';
import '../../models/movement.dart';
import '../../models/page_response.dart';

/// Acceso a `/api/cards` y a sus movimientos y planes de cuotas.
class CardRepository {
  CardRepository(this._client);

  final ApiClient _client;

  Future<PageResponse<CreditCard>> list({int page = 0, int size = 50}) async {
    final response = await _client.get(
      '/api/cards',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<CreditCard>.fromJson(
      response.data as Map<String, dynamic>,
      CreditCard.fromJson,
    );
  }

  Future<CreditCard> get(int id) async {
    final response = await _client.get('/api/cards/$id');
    return CreditCard.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CreditCard> create({
    required String name,
    String? bank,
    required CardFranchise franchise,
    required double creditLimit,
    required double monthlyRate,
    required int cutoffDay,
    required int paymentDueDay,
  }) async {
    final response = await _client.post('/api/cards', body: <String, dynamic>{
      'name': name,
      'bank': ?bank,
      'franchise': franchise.wire,
      'creditLimit': creditLimit,
      'monthlyRate': monthlyRate,
      'cutoffDay': cutoffDay,
      'paymentDueDay': paymentDueDay,
    });
    return CreditCard.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CreditCard> update(
    int id, {
    required String name,
    String? bank,
    required double monthlyRate,
    required int cutoffDay,
    required int paymentDueDay,
  }) async {
    final response =
        await _client.put('/api/cards/$id', body: <String, dynamic>{
      'name': name,
      'bank': ?bank,
      'monthlyRate': monthlyRate,
      'cutoffDay': cutoffDay,
      'paymentDueDay': paymentDueDay,
    });
    return CreditCard.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _client.delete('/api/cards/$id');
  }

  Future<PageResponse<CardMovement>> movements(int cardId,
      {int page = 0, int size = 50}) async {
    final response = await _client.get(
      '/api/cards/$cardId/movements',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<CardMovement>.fromJson(
      response.data as Map<String, dynamic>,
      CardMovement.fromJson,
    );
  }

  Future<List<Installment>> installments(int cardId, int movementId) async {
    final response =
        await _client.get('/api/cards/$cardId/movements/$movementId/installments');
    return (response.data as List<dynamic>)
        .map((e) => Installment.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<CardMovement> registerPurchase(
    int cardId, {
    required double amount,
    DateTime? date,
    String? description,
    int? installmentCount,
  }) async {
    final response = await _client.post(
      '/api/cards/$cardId/purchases',
      body: <String, dynamic>{
        'amount': amount,
        'date': ?(date == null ? null : MovementDraft.isoDate(date)),
        'description': ?description,
        'installmentCount': ?installmentCount,
      },
    );
    return CardMovement.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CardMovement> registerPayment(
    int cardId, {
    required double amount,
    DateTime? date,
    String? description,
  }) async {
    final response = await _client.post(
      '/api/cards/$cardId/payments',
      body: <String, dynamic>{
        'amount': amount,
        'date': ?(date == null ? null : MovementDraft.isoDate(date)),
        'description': ?description,
      },
    );
    return CardMovement.fromJson(response.data as Map<String, dynamic>);
  }
}
