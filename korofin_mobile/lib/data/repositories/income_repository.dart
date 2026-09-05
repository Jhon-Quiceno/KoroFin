import '../../core/network/api_client.dart';
import '../../models/movement.dart';
import '../../models/page_response.dart';

/// Acceso a `/api/incomes` (ingresos del usuario actual, paginados).
class IncomeRepository {
  IncomeRepository(this._client);

  final ApiClient _client;

  Future<PageResponse<Movement>> list({
    int page = 0,
    int size = 20,
    int? month,
    int? year,
  }) async {
    final response = await _client.get(
      '/api/incomes',
      query: <String, dynamic>{
        'page': page,
        'size': size,
        'month': ?month,
        'year': ?year,
      },
    );
    return PageResponse<Movement>.fromJson(
      response.data as Map<String, dynamic>,
      Movement.fromIncomeJson,
    );
  }

  Future<Movement> create(MovementDraft draft) async {
    final response =
        await _client.post('/api/incomes', body: draft.toIncomeJson());
    return Movement.fromIncomeJson(response.data as Map<String, dynamic>);
  }

  Future<Movement> update(int id, MovementDraft draft) async {
    final response =
        await _client.put('/api/incomes/$id', body: draft.toIncomeJson());
    return Movement.fromIncomeJson(response.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _client.delete('/api/incomes/$id');
  }
}
