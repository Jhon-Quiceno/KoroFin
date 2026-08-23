import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/formatters.dart';
import '../../data/mock_data.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/cards/balance_card.dart';
import '../../widgets/cards/linked_alert_card.dart';
import '../../widgets/cards/section_card.dart';
import '../../widgets/charts/category_donut_chart.dart';
import '../../widgets/charts/income_expense_bar_chart.dart';
import '../../widgets/list_items/transaction_tile.dart';
import '../../widgets/nav/app_header.dart';

/// Screen 3 — Dashboard/Inicio: balance destacado, gráfico ingresos vs
/// gastos, donut por categoría, movimientos recientes, tarjetas contextuales
/// (deudas activas, alertas de vencimientos, insight IA) y "ver reporte".
class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final recent = MockData.transactions.take(4).toList();
    final nextDueSubscription = MockData.subscriptions.reduce(
      (a, b) => a.nextChargeDate.isBefore(b.nextChargeDate) ? a : b,
    );
    final categoryTotals = <String, double>{};
    for (final expense in MockData.expenses) {
      categoryTotals.update(expense.category.id, (v) => v + expense.amount, ifAbsent: () => expense.amount);
    }
    final donutEntries = [
      for (final entry in categoryTotals.entries) (MockData.categoryById(entry.key), entry.value),
    ]..sort((a, b) => b.$2.compareTo(a.$2));

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppHeader(
            title: 'Buenos días, Valentina',
            subtitle: 'Así va tu mes en KoroFin',
            onNotificationsTap: () => context.push('/notifications'),
            onProfileTap: () => context.push('/settings'),
            onSettingsTap: () => context.push('/settings'),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.xxxl),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                BalanceCard(
                  balance: MockData.totalBalance,
                  income: MockData.totalIncome,
                  expense: MockData.totalExpense,
                ),
                const SizedBox(height: AppSpacing.lg),
                LinkedAlertCard(
                  icon: Icons.auto_awesome,
                  iconColor: koro.accent,
                  title: 'Predicción de fin de mes (IA)',
                  description: 'A este ritmo cerrarás el mes con un ahorro estimado de ${AppFormatters.currency(620000)}.',
                  actionLabel: 'Hablar con el asistente',
                  onTap: () => context.go('/assistant'),
                ),
                const SizedBox(height: AppSpacing.md),
                LinkedAlertCard(
                  icon: Icons.schedule,
                  iconColor: AppColors.warning,
                  title: 'Vencimientos próximos',
                  description: '${nextDueSubscription.name} se cobra el ${AppFormatters.shortDate(nextDueSubscription.nextChargeDate)}.',
                  actionLabel: 'Ver servicios y suscripciones',
                  onTap: () => context.go('/debts?tab=2'),
                ),
                const SizedBox(height: AppSpacing.md),
                LinkedAlertCard(
                  icon: Icons.account_balance_wallet_outlined,
                  iconColor: AppColors.info,
                  title: 'Deudas activas',
                  description: 'Tenés ${MockData.debts.length} deudas en seguimiento por ${AppFormatters.currency(MockData.debts.fold<double>(0, (s, d) => s + d.remainingAmount))}.',
                  actionLabel: 'Ver hub de deudas',
                  onTap: () => context.go('/debts?tab=0'),
                ),
                const SizedBox(height: AppSpacing.lg),
                SectionCard(
                  title: 'Ingresos vs. gastos',
                  child: IncomeExpenseBarChart(
                    data: const [
                      ('Mar', 3800000, 2900000),
                      ('Abr', 4000000, 3100000),
                      ('May', 4100000, 3400000),
                      ('Jun', 3950000, 3200000),
                      ('Jul', 4150000, 3350000),
                      ('Ago', 5150000, 3812600),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.lg),
                SectionCard(
                  title: 'Gastos por categoría',
                  child: CategoryDonutChart(entries: donutEntries),
                ),
                const SizedBox(height: AppSpacing.lg),
                SectionCard(
                  title: 'Movimientos recientes',
                  trailing: TextButton(
                    onPressed: () => context.push('/reports'),
                    child: const Text('Ver reporte completo'),
                  ),
                  child: Column(
                    children: [for (final t in recent) TransactionTile(transaction: t)],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
