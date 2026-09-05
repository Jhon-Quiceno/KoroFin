import '../../core/network/api_client.dart';
import '../../models/debt.dart';
import '../../models/movement.dart';
import '../../models/page_response.dart';

/// Acceso a `/api/debts` y a sus sub-recursos de abonos y cargos.
class DebtRepository {
  DebtRepository(this._client);

  final ApiClient _client;

  Future<PageResponse<Debt>> list({int page = 0, int size = 20}) async {
    final response = await _client.get(
      '/api/debts',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<Debt>.fromJson(
      response.data as Map<String, dynamic>,
      Debt.fromJson,
    );
  }

  Future<Debt> get(int id) async {
    final response = await _client.get('/api/debts/$id');
    return Debt.fromJson(response.data as Map<String, dynamic>);
  }

  Future<Debt> create({
    required String name,
    required double totalAmount,
    double? interestRate,
    DateTime? dueDate,
  }) async {
    final response = await _client.post('/api/debts', body: <String, dynamic>{
      'name': name,
      'totalAmount': totalAmount,
      'interestRate': ?interestRate,
      'dueDate': ?(dueDate == null ? null : MovementDraft.isoDate(dueDate)),
    });
    return Debt.fromJson(response.data as Map<String, dynamic>);
  }

  Future<Debt> update(
    int id, {
    required String name,
    double? interestRate,
    DateTime? dueDate,
  }) async {
    final response =
        await _client.put('/api/debts/$id', body: <String, dynamic>{
      'name': name,
      'interestRate': ?interestRate,
      'dueDate': ?(dueDate == null ? null : MovementDraft.isoDate(dueDate)),
    });
    return Debt.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> delete(int id) async {
    await _client.delete('/api/debts/$id');
  }

  Future<PageResponse<DebtPayment>> payments(int debtId,
      {int page = 0, int size = 50}) async {
    final response = await _client.get(
      '/api/debts/$debtId/payments',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<DebtPayment>.fromJson(
      response.data as Map<String, dynamic>,
      DebtPayment.fromJson,
    );
  }

  Future<PageResponse<DebtCharge>> charges(int debtId,
      {int page = 0, int size = 50}) async {
    final response = await _client.get(
      '/api/debts/$debtId/charges',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<DebtCharge>.fromJson(
      response.data as Map<String, dynamic>,
      DebtCharge.fromJson,
    );
  }

  /// Registra un abono. Devuelve el abono creado (el saldo nuevo se relee con
  /// [get]).
  Future<DebtPayment> addPayment(int debtId,
      {required double amount, String? note}) async {
    final response = await _client.post(
      '/api/debts/$debtId/payments',
      body: <String, dynamic>{'amount': amount, 'note': ?note},
    );
    return DebtPayment.fromJson(response.data as Map<String, dynamic>);
  }

  /// Registra un cargo. El backend devuelve la **deuda** ya actualizada.
  Future<Debt> addCharge(int debtId,
      {required double amount, String? description}) async {
    final response = await _client.post(
      '/api/debts/$debtId/charges',
      body: <String, dynamic>{'amount': amount, 'description': ?description},
    );
    return Debt.fromJson(response.data as Map<String, dynamic>);
  }
}
