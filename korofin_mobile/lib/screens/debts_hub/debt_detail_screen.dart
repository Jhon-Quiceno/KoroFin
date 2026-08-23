import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/debt.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../../widgets/cards/section_card.dart';

/// Debt detail with payment history, reached by tapping a [Debt] row.
class DebtDetailScreen extends StatelessWidget {
  const DebtDetailScreen({super.key, required this.debt});

  final Debt debt;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      appBar: AppBar(title: Text(debt.name)),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          Text(debt.lender, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: AppSpacing.lg),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppRadii.pill),
            child: LinearProgressIndicator(value: debt.progress, minHeight: 10, backgroundColor: koro.surfaceElevated),
          ),
          const SizedBox(height: AppSpacing.sm),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Pagado: ${AppFormatters.currency(debt.paidAmount)}', style: Theme.of(context).textTheme.bodyMedium),
              Text('Total: ${AppFormatters.currency(debt.totalAmount)}', style: Theme.of(context).textTheme.bodyMedium),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Resumen',
            child: Column(
              children: [
                _InfoRow(label: 'Restante', value: AppFormatters.currency(debt.remainingAmount)),
                _InfoRow(label: 'Fecha límite', value: AppFormatters.longDate(debt.dueDate)),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          Text('Historial de pagos', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.sm),
          for (final payment in debt.payments)
            Card(
              child: ListTile(
                leading: CircleAvatar(backgroundColor: koro.success.withValues(alpha: 0.14), child: Icon(Icons.check, color: koro.success, size: 18)),
                title: Text(AppFormatters.currency(payment.amount)),
                subtitle: Text(AppFormatters.longDate(payment.date)),
              ),
            ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodyMedium),
          Text(value, style: AppTextStyles.bodyMediumMedium(context.koroColors.foreground)),
        ],
      ),
    );
  }
}
