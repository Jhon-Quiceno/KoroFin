import '../../core/network/api_client.dart';
import '../../models/movement.dart';
import '../../models/page_response.dart';
import '../../models/recurring_payment.dart';

/// Acceso a `/api/recurring` (pagos recurrentes del usuario actual).
class RecurringPaymentRepository {
  RecurringPaymentRepository(this._client);

  final ApiClient _client;

  Future<PageResponse<RecurringPayment>> list(
      {int page = 0, int size = 100}) async {
    final response = await _client.get(
      '/api/recurring',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<RecurringPayment>.fromJson(
      response.data as Map<String, dynamic>,
      RecurringPayment.fromJson,
    );
  }

  Future<RecurringPayment> create({
    required String name,
    required double amount,
    required RecurringFrequency frequency,
    required DateTime firstPaymentDate,
  }) async {
    final response = await _client.post('/api/recurring', body: <String, dynamic>{
      'name': name,
      'amount': amount,
      'frequency': frequency.wire,
      'firstPaymentDate': MovementDraft.isoDate(firstPaymentDate),
    });
    return RecurringPayment.fromJson(response.data as Map<String, dynamic>);
  }

  Future<RecurringPayment> update(
    int id, {
    required String name,
    required double amount,
    required RecurringFrequency frequency,
  }) async {
    final response =
        await _client.put('/api/recurring/$id', body: <String, dynamic>{
      'name': name,
      'amount': amount,
      'frequency': frequency.wire,
    });
    return RecurringPayment.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _client.delete('/api/recurring/$id');
  }

  Future<RecurringPayment> toggle(int id) async {
    final response = await _client.patch('/api/recurring/$id/toggle');
    return RecurringPayment.fromJson(response.data as Map<String, dynamic>);
  }

  /// Registra el pago del período. Devuelve el recurrente con su
  /// `nextPaymentDate` avanzada (el `expenseId` del gasto creado se descarta).
  Future<RecurringPayment> pay(int id) async {
    final response = await _client.patch('/api/recurring/$id/pay');
    final Map<String, dynamic> body = response.data as Map<String, dynamic>;
    return RecurringPayment.fromJson(
      body['recurringPayment'] as Map<String, dynamic>,
    );
  }
}
