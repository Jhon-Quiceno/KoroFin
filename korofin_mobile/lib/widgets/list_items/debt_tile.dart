import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/debt.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Fila de una deuda con barra de progreso pagado/restante, tocable para abrir
/// el detalle.
class DebtTile extends StatelessWidget {
  const DebtTile({super.key, required this.debt, required this.onTap});

  final Debt debt;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(debt.name,
                            style: Theme.of(context).textTheme.titleMedium),
                        Text(
                          debt.dueDate != null
                              ? 'Vence ${AppFormatters.shortDate(debt.dueDate!)}'
                              : (debt.interestRate != null
                                  ? 'Interés ${debt.interestRate!.toStringAsFixed(1)}%'
                                  : 'Sin fecha de vencimiento'),
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right_rounded,
                      color: koro.mutedForeground),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              ClipRRect(
                borderRadius: BorderRadius.circular(AppRadii.pill),
                child: LinearProgressIndicator(
                  value: debt.progress,
                  minHeight: 8,
                  backgroundColor: koro.surfaceElevated,
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Pagado ${AppFormatters.currency(debt.paidAmount)}',
                      style: Theme.of(context).textTheme.bodyMedium),
                  Text('Resta ${AppFormatters.currency(debt.remainingAmount)}',
                      style: Theme.of(context).textTheme.bodyMedium),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
