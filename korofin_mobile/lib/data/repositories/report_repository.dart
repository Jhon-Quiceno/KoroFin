import 'package:dio/dio.dart';

import '../../core/network/api_client.dart';
import '../../core/network/dio_error_mapper.dart';
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

  /// Descarga el CSV de `/api/reports/export` para el rango [from]-[to].
  /// Usa `_client.dio` directamente (acceso documentado para lo que no
  /// encaja en los verbos del cliente, como esta descarga de texto plano) y
  /// traduce cualquier falla a [ApiException] igual que el resto del repo.
  Future<String> exportCsv({
    required DateTime from,
    required DateTime to,
  }) async {
    try {
      final response = await _client.dio.get<String>(
        '/api/reports/export',
        queryParameters: <String, dynamic>{
          'from': MovementDraft.isoDate(from),
          'to': MovementDraft.isoDate(to),
          'format': 'csv',
        },
        options: Options(responseType: ResponseType.plain),
      );
      return response.data ?? '';
    } on DioException catch (error) {
      throw mapDioException(error);
    }
  }
}
