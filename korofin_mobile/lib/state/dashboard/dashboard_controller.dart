import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/analysis_repository.dart';
import '../../models/analysis.dart';

final analysisRepositoryProvider = Provider<AnalysisRepository>(
  (ref) => AnalysisRepository(ref.read(apiClientProvider)),
);

/// Todo lo que necesita el dashboard en una sola carga.
class DashboardData {
  const DashboardData({
    required this.summary,
    required this.recommendations,
    this.prediction,
  });

  final AnalysisSummary summary;
  final List<Recommendation> recommendations;

  /// `null` si la predicción falló (no debe tumbar el dashboard entero).
  final MonthEndPrediction? prediction;
}

final dashboardProvider =
    AsyncNotifierProvider<DashboardController, DashboardData>(
  DashboardController.new,
);

class DashboardController extends AsyncNotifier<DashboardData> {
  AnalysisRepository get _repo => ref.read(analysisRepositoryProvider);

  @override
  Future<DashboardData> build() => _load();

  Future<void> refresh() async {
    state = const AsyncValue<DashboardData>.loading();
    state = await AsyncValue.guard(_load);
  }

  Future<DashboardData> _load() async {
    // El resumen es lo único imprescindible; recomendaciones y predicción se
    // piden en paralelo y degradan a vacío/null si fallan.
    final AnalysisSummary summary = await _repo.summary();
    final List<Recommendation> recommendations =
        await _repo.recommendations().catchError(
              (_) => const <Recommendation>[],
            );
    final MonthEndPrediction? prediction =
        await _repo.prediction().then<MonthEndPrediction?>((p) => p).catchError(
              (_) => null,
            );
    return DashboardData(
      summary: summary,
      recommendations: recommendations,
      prediction: prediction,
    );
  }
}
