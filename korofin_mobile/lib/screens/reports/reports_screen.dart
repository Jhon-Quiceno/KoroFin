import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_exception.dart';
import '../../data/formatters.dart';
import '../../models/analysis.dart';
import '../../models/report.dart';
import '../../state/reports/report_controller.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/cards/kpi_card.dart';
import '../../widgets/cards/section_card.dart';
import '../../widgets/charts/trend_line_chart.dart';

/// Pantalla 12 — Reportes: selector de mes, KPIs, tendencia de ahorro de 6
/// meses y la tabla de movimientos del período.
class ReportsScreen extends ConsumerWidget {
  const ReportsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final DateTime month = ref.watch(selectedReportMonthProvider);
    final AsyncValue<ReportData> report = ref.watch(reportProvider);
    final AsyncValue<List<MonthlyTotal>> trend = ref.watch(reportTrendProvider);

    void shift(int months) {
      ref.read(selectedReportMonthProvider.notifier).state =
          DateTime(month.year, month.month + months);
    }

    final bool isCurrentOrFuture = !month.isBefore(
      DateTime(DateTime.now().year, DateTime.now().month),
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Reportes')),
      body: report.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorView(
          message: error is ApiException
              ? error.message
              : 'No se pudo cargar el reporte.',
          onRetry: () => ref.invalidate(reportProvider),
        ),
        data: (data) => ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                IconButton(
                  onPressed: () => shift(-1),
                  icon: const Icon(Icons.chevron_left),
                ),
                Text(
                  toBeginningOfSentenceCase(
                        DateFormat('MMMM y', 'es_CO').format(month),
                      ) ??
                      '',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                IconButton(
                  onPressed: isCurrentOrFuture ? null : () => shift(1),
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: AppSpacing.md,
              crossAxisSpacing: AppSpacing.md,
              childAspectRatio: 1.5,
              children: [
                KpiCard(
                  label: 'Ingresos',
                  value: AppFormatters.currency(data.monthly.totalIncome),
                  icon: Icons.arrow_downward_rounded,
                  color: AppColors.success,
                ),
                KpiCard(
                  label: 'Gastos',
                  value: AppFormatters.currency(data.monthly.totalExpense),
                  icon: Icons.arrow_upward_rounded,
                  color: AppColors.accent,
                ),
                KpiCard(
                  label: 'Ahorro',
                  value: AppFormatters.currency(data.monthly.totalSavings),
                  icon: Icons.savings_outlined,
                  color: AppColors.info,
                  trend: data.monthly.totalSavings >= 0 ? 'Positivo' : 'Negativo',
                ),
                KpiCard(
                  label: 'Tasa de ahorro',
                  value: '${data.monthly.savingsRate.toStringAsFixed(1)}%',
                  icon: Icons.trending_up_rounded,
                  color: AppColors.warning,
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Ahorro (últimos 6 meses)',
              child: trend.maybeWhen(
                data: (series) => TrendLineChart(
                  months: [
                    for (final m in series)
                      toBeginningOfSentenceCase(
                            DateFormat('MMM', 'es_CO')
                                .format(DateTime(2020, m.month)),
                          ) ??
                          '',
                  ],
                  values: [for (final m in series) m.savings],
                ),
                orElse: () => const SizedBox(
                  height: 120,
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            if (data.monthly.topExpenseCategories.isNotEmpty) ...[
              SectionCard(
                title: 'Gasto por categoría',
                child: Column(
                  children: [
                    for (final CategoryTotal c
                        in data.monthly.topExpenseCategories)
                      Padding(
                        padding: const EdgeInsets.symmetric(
                            vertical: AppSpacing.xs),
                        child: Row(
                          children: [
                            Expanded(child: Text(c.categoryName)),
                            Text(AppFormatters.currency(c.total),
                                style: const TextStyle(
                                    fontWeight: FontWeight.w600)),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
            ],
            Text('Movimientos del período',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            if (data.movements.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: AppSpacing.lg),
                child: Text('Sin movimientos en este mes.'),
              )
            else
              for (final ReportMovement m in data.movements)
                _ReportMovementRow(movement: m),
          ],
        ),
      ),
    );
  }
}

class _ReportMovementRow extends StatelessWidget {
  const _ReportMovementRow({required this.movement});

  final ReportMovement movement;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(movement.description?.isNotEmpty == true
                    ? movement.description!
                    : movement.categoryName),
                Text(
                  '${movement.categoryName} · '
                  '${AppFormatters.shortDate(movement.date)}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
          Text(
            '${movement.isIncome ? '+' : '-'}'
            '${AppFormatters.currency(movement.amount)}',
            style: TextStyle(
              fontWeight: FontWeight.w700,
              color: movement.isIncome
                  ? AppColors.success
                  : Theme.of(context).colorScheme.onSurface,
            ),
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: AppSpacing.lg),
              FilledButton.tonal(
                  onPressed: onRetry, child: const Text('Reintentar')),
            ],
          ),
        ),
      );
}
