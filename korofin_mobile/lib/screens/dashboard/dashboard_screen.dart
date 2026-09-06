import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_exception.dart';
import '../../data/category_visuals.dart';
import '../../data/formatters.dart';
import '../../models/ai.dart';
import '../../models/analysis.dart';
import '../../state/ai/ai_controller.dart';
import '../../state/auth/auth_controller.dart';
import '../../state/dashboard/dashboard_controller.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/cards/balance_card.dart';
import '../../widgets/cards/linked_alert_card.dart';
import '../../widgets/cards/section_card.dart';
import '../../widgets/charts/category_donut_chart.dart';
import '../../widgets/charts/income_expense_bar_chart.dart';
import '../../widgets/nav/app_header.dart';

/// Pantalla 3 — Dashboard/Inicio: cifras del mes desde `/api/analysis/summary`,
/// gráficos, y tarjetas contextuales de predicción y recomendaciones.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<DashboardData> dashboard = ref.watch(dashboardProvider);
    final String name = ref.watch(
      authControllerProvider.select((s) => s.user?.name.split(' ').first ?? ''),
    );

    final AsyncValue<AiInsight?> insight = ref.watch(latestInsightProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppHeader(
          title: name.isEmpty ? 'Hola' : 'Hola, $name',
          subtitle: 'Así va tu mes en KoroFin',
          onNotificationsTap: () => context.push('/notifications'),
          onProfileTap: () => context.push('/settings'),
          onSettingsTap: () => context.push('/settings'),
        ),
        Expanded(
          child: dashboard.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => _ErrorView(
              message: error is ApiException
                  ? error.message
                  : 'No se pudo cargar el resumen.',
              onRetry: () => ref.read(dashboardProvider.notifier).refresh(),
            ),
            data: (data) => RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(latestInsightProvider);
                await ref.read(dashboardProvider.notifier).refresh();
              },
              child: _DashboardBody(
                data: data,
                insight: insight.valueOrNull,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _DashboardBody extends StatelessWidget {
  const _DashboardBody({required this.data, this.insight});

  final DashboardData data;
  final AiInsight? insight;

  String _monthShort(int month) =>
      toBeginningOfSentenceCase(
          DateFormat('MMM', 'es_CO').format(DateTime(2020, month))) ??
      '';

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final AnalysisSummary s = data.summary;

    final List<(String, double, double)> barData = <(String, double, double)>[
      for (final MonthlyTotal m in s.monthlySeries)
        (_monthShort(m.month), m.income, m.expense),
    ];

    final List<DonutEntry> donut = <DonutEntry>[
      for (final CategoryTotal c in s.topExpenseCategories)
        (
          label: c.categoryName,
          value: c.total,
          color: CategoryVisuals.colorForName(c.categoryName),
        ),
    ];

    return SingleChildScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.xxxl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          BalanceCard(
            balance: s.totalSavings,
            income: s.totalIncome,
            expense: s.totalExpense,
          ),
          const SizedBox(height: AppSpacing.lg),
          if (insight != null) ...[
            LinkedAlertCard(
              icon: Icons.psychology_outlined,
              iconColor: koro.accent,
              title: 'Insight de la IA',
              description: insight!.content,
              actionLabel: 'Abrir el asistente',
              onTap: () => context.go('/assistant'),
            ),
            const SizedBox(height: AppSpacing.md),
          ],
          if (data.prediction != null)
            LinkedAlertCard(
              icon: Icons.auto_awesome,
              iconColor: koro.accent,
              title: 'Proyección de fin de mes',
              description:
                  'A este ritmo cerrarías el mes gastando ${AppFormatters.currency(data.prediction!.projectedExpense)} '
                  '(${data.prediction!.daysElapsed}/${data.prediction!.daysInMonth} días).',
              actionLabel: 'Hablar con el asistente',
              onTap: () => context.go('/assistant'),
            ),
          for (final r in data.recommendations) ...[
            const SizedBox(height: AppSpacing.md),
            LinkedAlertCard(
              icon: Icons.lightbulb_outline,
              iconColor: AppColors.info,
              title: r.title,
              description: r.message,
              actionLabel: 'Ver movimientos',
              onTap: () => context.go('/movements'),
            ),
          ],
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Ingresos vs. gastos',
            child: barData.isEmpty
                ? const _Empty('Sin datos del período')
                : IncomeExpenseBarChart(data: barData),
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Gastos por categoría',
            child: donut.isEmpty
                ? const _Empty('Todavía no registraste gastos este mes')
                : CategoryDonutChart(entries: donut),
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Tasa de ahorro',
            trailing: TextButton(
              onPressed: () => context.push('/reports'),
              child: const Text('Ver reporte'),
            ),
            child: Text(
              '${s.savingsRate.toStringAsFixed(1)}%',
              style: Theme.of(context).textTheme.displayLarge,
            ),
          ),
        ],
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
        child: Text(text, style: Theme.of(context).textTheme.bodyMedium),
      );
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
