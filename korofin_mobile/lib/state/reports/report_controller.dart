import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/report_repository.dart';
import '../../models/analysis.dart';
import '../../models/report.dart';
import '../dashboard/dashboard_controller.dart';

final reportRepositoryProvider = Provider<ReportRepository>(
  (ref) => ReportRepository(ref.read(apiClientProvider)),
);

/// Mes seleccionado en la pantalla de Reportes (siempre el día 1). Por defecto
/// el mes en curso.
final selectedReportMonthProvider = StateProvider<DateTime>((ref) {
  final DateTime now = DateTime.now();
  return DateTime(now.year, now.month);
});

class ReportData {
  const ReportData({required this.monthly, required this.movements});

  final MonthlyReport monthly;
  final List<ReportMovement> movements;
}

/// Cifras + tabla de movimientos del [selectedReportMonthProvider].
final reportProvider = FutureProvider<ReportData>((ref) async {
  final DateTime month = ref.watch(selectedReportMonthProvider);
  final ReportRepository repo = ref.read(reportRepositoryProvider);

  final DateTime from = DateTime(month.year, month.month);
  final DateTime to = DateTime(month.year, month.month + 1, 0); // último día

  final MonthlyReport monthly =
      await repo.monthly(year: month.year, month: month.month);
  final List<ReportMovement> movements =
      await repo.movements(from: from, to: to);

  return ReportData(monthly: monthly, movements: movements);
});

/// Serie de los últimos 6 meses (para el gráfico de tendencia) del mes
/// seleccionado, tomada del resumen de análisis.
final reportTrendProvider = FutureProvider<List<MonthlyTotal>>((ref) async {
  final DateTime month = ref.watch(selectedReportMonthProvider);
  final AnalysisSummary summary = await ref
      .read(analysisRepositoryProvider)
      .summary(year: month.year, month: month.month);
  return summary.monthlySeries;
});
