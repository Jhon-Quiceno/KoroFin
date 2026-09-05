import '../../core/network/api_client.dart';
import '../../models/analysis.dart';

/// Acceso a `/api/analysis/*` (resumen financiero del usuario actual).
class AnalysisRepository {
  AnalysisRepository(this._client);

  final ApiClient _client;

  /// Sin `year`/`month` el backend usa el mes en curso.
  Future<AnalysisSummary> summary({int? year, int? month}) async {
    final response = await _client.get(
      '/api/analysis/summary',
      query: <String, dynamic>{'year': ?year, 'month': ?month},
    );
    return AnalysisSummary.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<Recommendation>> recommendations() async {
    final response = await _client.get('/api/analysis/recommendations');
    return (response.data as List<dynamic>)
        .map((e) => Recommendation.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<MonthEndPrediction> prediction() async {
    final response = await _client.get('/api/analysis/prediction');
    return MonthEndPrediction.fromJson(response.data as Map<String, dynamic>);
  }
}
