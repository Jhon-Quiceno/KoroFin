import '../../core/network/api_client.dart';
import '../../models/movement.dart';
import '../../models/report.dart';

/// Acceso a `/api/reports/*` (cifras del mes y tabla de movimientos).
class ReportRepository {
  ReportRepository(this._client);

  final ApiClient _client;

  Future<MonthlyReport> monthly({int? year, int? month}) async {
    final response = await _client.get(
      '/api/reports/monthly',
      query: <String, dynamic>{'year': ?year, 'month': ?month},
    );
    return MonthlyReport.fromJson(response.data as Map<String, dynamic>);
  }

  /// `from` y `to` son obligatorios en este endpoint.
  Future<List<ReportMovement>> movements({
    required DateTime from,
    required DateTime to,
  }) async {
    final response = await _client.get(
      '/api/reports/movements',
      query: <String, dynamic>{
        'from': MovementDraft.isoDate(from),
        'to': MovementDraft.isoDate(to),
      },
    );
    return (response.data as List<dynamic>)
        .map((e) => ReportMovement.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }
}
