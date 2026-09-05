import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/analysis_repository.dart';
import 'package:korofin_mobile/data/repositories/report_repository.dart';

import '../support/capturing_adapter.dart';

Map<String, dynamic> _summary() => <String, dynamic>{
      'periodYear': 2026,
      'periodMonth': 9,
      'totalIncome': 1000,
      'totalExpense': 400,
      'totalSavings': 600,
      'savingsRate': 60,
      'topExpenseCategories': <dynamic>[],
      'monthlySeries': <dynamic>[],
    };

(AnalysisRepository, CapturingAdapter) _analysis() {
  final adapter = CapturingAdapter();
  return (
    AnalysisRepository(ApiClient(
        readAccessToken: () => 't', dio: Dio()..httpClientAdapter = adapter)),
    adapter
  );
}

(ReportRepository, CapturingAdapter) _report() {
  final adapter = CapturingAdapter();
  return (
    ReportRepository(ApiClient(
        readAccessToken: () => 't', dio: Dio()..httpClientAdapter = adapter)),
    adapter
  );
}

void main() {
  test('analysis.summary sin período no manda query', () async {
    final (repo, adapter) = _analysis();
    adapter.body = _summary();

    await repo.summary();

    expect(adapter.lastRequest.path, '/api/analysis/summary');
    expect(adapter.lastRequest.queryParameters, isEmpty);
  });

  test('analysis.summary con período manda year/month', () async {
    final (repo, adapter) = _analysis();
    adapter.body = _summary();

    await repo.summary(year: 2026, month: 7);

    expect(adapter.lastRequest.queryParameters,
        <String, dynamic>{'year': 2026, 'month': 7});
  });

  test('analysis.recommendations parsea la lista', () async {
    final (repo, adapter) = _analysis();
    adapter.body = <dynamic>[
      <String, dynamic>{'title': 'Ahorrás poco', 'message': 'Subí la tasa'},
    ];

    final recs = await repo.recommendations();

    expect(adapter.lastRequest.path, '/api/analysis/recommendations');
    expect(recs.single.title, 'Ahorrás poco');
  });

  test('report.movements exige from/to y los manda en ISO', () async {
    final (repo, adapter) = _report();
    adapter.body = <dynamic>[];

    await repo.movements(from: DateTime(2026, 9, 1), to: DateTime(2026, 9, 30));

    expect(adapter.lastRequest.path, '/api/reports/movements');
    expect(adapter.lastRequest.queryParameters, <String, dynamic>{
      'from': '2026-09-01',
      'to': '2026-09-30',
    });
  });

  test('report.monthly con período manda year/month', () async {
    final (repo, adapter) = _report();
    adapter.body = <String, dynamic>{
      'periodYear': 2026,
      'periodMonth': 9,
      'totalIncome': 0,
      'totalExpense': 0,
      'totalSavings': 0,
      'savingsRate': 0,
      'topExpenseCategories': <dynamic>[],
    };

    await repo.monthly(year: 2026, month: 9);

    expect(adapter.lastRequest.queryParameters,
        <String, dynamic>{'year': 2026, 'month': 9});
  });
}
