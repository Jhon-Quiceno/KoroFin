import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../data/mock_data.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/cards/kpi_card.dart';
import '../../widgets/cards/section_card.dart';
import '../../widgets/charts/trend_line_chart.dart';
import '../../widgets/list_items/transaction_tile.dart';

enum _Period { month, quarter, year }

/// Screen 12 — Reportes/Análisis: period selector, KPI cards, 6-month
/// trend chart and the period's movement table.
class ReportsScreen extends StatefulWidget {
  const ReportsScreen({super.key});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  _Period _period = _Period.month;

  @override
  Widget build(BuildContext context) {
    final income = MockData.totalIncome;
    final expense = MockData.totalExpense;
    final savings = income - expense;

    return Scaffold(
      appBar: AppBar(title: const Text('Reportes')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          SegmentedButton<_Period>(
            segments: const [
              ButtonSegment(value: _Period.month, label: Text('Mes')),
              ButtonSegment(value: _Period.quarter, label: Text('Trimestre')),
              ButtonSegment(value: _Period.year, label: Text('Año')),
            ],
            selected: {_period},
            onSelectionChanged: (s) => setState(() => _period = s.first),
          ),
          const SizedBox(height: AppSpacing.lg),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: AppSpacing.md,
            crossAxisSpacing: AppSpacing.md,
            childAspectRatio: 1.5,
            children: [
              KpiCard(label: 'Ingresos', value: AppFormatters.currency(income), icon: Icons.arrow_downward_rounded, color: AppColors.success),
              KpiCard(label: 'Gastos', value: AppFormatters.currency(expense), icon: Icons.arrow_upward_rounded, color: AppColors.accent),
              KpiCard(
                label: 'Ahorro',
                value: AppFormatters.currency(savings),
                icon: Icons.savings_outlined,
                color: AppColors.info,
                trend: savings >= 0 ? 'Positivo' : 'Negativo',
              ),
              const KpiCard(label: 'Tasa de ahorro', value: '14.8%', icon: Icons.trending_up_rounded, color: AppColors.warning),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Tendencia (últimos 6 meses)',
            child: TrendLineChart(
              months: const ['Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago'],
              values: const [900000, 1150000, 700000, 750000, 800000, 1337400],
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          Text('Movimientos del periodo', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.sm),
          for (final t in MockData.transactions) TransactionTile(transaction: t),
        ],
      ),
    );
  }
}
