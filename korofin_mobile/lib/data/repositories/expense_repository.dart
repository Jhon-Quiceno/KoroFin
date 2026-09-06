import '../../core/network/api_client.dart';
import '../../models/movement.dart';
import '../../models/page_response.dart';

/// Acceso a `/api/expenses` (gastos del usuario actual, paginados).
class ExpenseRepository {
  ExpenseRepository(this._client);

  final ApiClient _client;

  Future<PageResponse<Movement>> list({
    int page = 0,
    int size = 20,
    int? categoryId,
    DateTime? from,
    DateTime? to,
    PaymentMethod? paymentMethod,
  }) async {
    final response = await _client.get(
      '/api/expenses',
      query: <String, dynamic>{
        'page': page,
        'size': size,
        'categoryId': ?categoryId,
        if (from != null) 'from': MovementDraft.isoDate(from),
        if (to != null) 'to': MovementDraft.isoDate(to),
        if (paymentMethod != null) 'paymentMethod': paymentMethod.wire,
      },
    );
    return PageResponse<Movement>.fromJson(
      response.data as Map<String, dynamic>,
      Movement.fromExpenseJson,
    );
  }

  Future<Movement> create(MovementDraft draft) async {
    final response =
        await _client.post('/api/expenses', body: draft.toExpenseJson());
    return Movement.fromExpenseJson(response.data as Map<String, dynamic>);
  }

  Future<Movement> update(int id, MovementDraft draft) async {
    final response =
        await _client.put('/api/expenses/$id', body: draft.toExpenseJson());
    return Movement.fromExpenseJson(response.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _client.delete('/api/expenses/$id');
  }
}
