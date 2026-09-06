import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/data/repositories/analysis_repository.dart';
import 'package:korofin_mobile/models/analysis.dart';
import 'package:korofin_mobile/state/dashboard/dashboard_controller.dart';

AnalysisSummary _summary() => const AnalysisSummary(
      year: 2026,
      month: 9,
      totalIncome: 1000,
      totalExpense: 400,
      totalSavings: 600,
      savingsRate: 60,
      topExpenseCategories: <CategoryTotal>[],
      monthlySeries: <MonthlyTotal>[],
    );

class _FakeAnalysisRepository implements AnalysisRepository {
  _FakeAnalysisRepository({this.failPrediction = false, this.failRecs = false});

  final bool failPrediction;
  final bool failRecs;

  @override
  Future<AnalysisSummary> summary({int? year, int? month}) async => _summary();

  @override
  Future<List<Recommendation>> recommendations() async {
    if (failRecs) throw const ApiException(message: 'x');
    return const [Recommendation(title: 'T', message: 'M')];
  }

  @override
  Future<MonthEndPrediction> prediction() async {
    if (failPrediction) throw const ApiException(message: 'x');
    return const MonthEndPrediction(
      currentExpense: 100,
      averageDailyExpense: 10,
      projectedExpense: 300,
      daysElapsed: 10,
      daysInMonth: 30,
    );
  }
}

ProviderContainer _container(AnalysisRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    analysisRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build combina summary + recomendaciones + predicción', () async {
    final c = _container(_FakeAnalysisRepository());

    final data = await c.read(dashboardProvider.future);

    expect(data.summary.savingsRate, 60);
    expect(data.recommendations, hasLength(1));
    expect(data.prediction?.projectedExpense, 300);
  });

  test('si la predicción falla, el dashboard igual carga con prediction null',
      () async {
    final c = _container(_FakeAnalysisRepository(failPrediction: true));

    final data = await c.read(dashboardProvider.future);

    expect(data.prediction, isNull);
    expect(data.summary.month, 9);
  });

  test('si las recomendaciones fallan, quedan vacías', () async {
    final c = _container(_FakeAnalysisRepository(failRecs: true));

    final data = await c.read(dashboardProvider.future);

    expect(data.recommendations, isEmpty);
  });
}
