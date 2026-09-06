import 'package:flutter/material.dart';

import '../../data/category_visuals.dart';
import '../../data/formatters.dart';
import '../../models/recurring_payment.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../common/app_badge.dart';

/// Fila de un pago recurrente: próximo cobro, badge "vence pronto", switch de
/// activo/pausado y botón para registrar el pago del período.
class SubscriptionTile extends StatelessWidget {
  const SubscriptionTile({
    super.key,
    required this.payment,
    required this.onPay,
    required this.onToggle,
    this.onTap,
  });

  final RecurringPayment payment;
  final VoidCallback onPay;
  final VoidCallback onToggle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final Color color = CategoryVisuals.colorForName(payment.name);
    final bool dimmed = !payment.isActive;
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        child: Opacity(
          opacity: dimmed ? 0.55 : 1,
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(AppRadii.md),
                  ),
                  child: Icon(CategoryVisuals.iconForName(payment.name),
                      size: 18, color: color),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(payment.name,
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 2),
                      Text(
                        '${payment.frequency.label} · '
                        'próximo ${AppFormatters.shortDate(payment.nextPaymentDate)}',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                      if (payment.isDueSoon) ...[
                        const SizedBox(height: 4),
                        const AppBadge(
                            label: 'Vence pronto',
                            color: AppColors.warning,
                            icon: Icons.schedule),
                      ],
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(AppFormatters.currency(payment.amount),
                        style: Theme.of(context).textTheme.titleMedium),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (payment.isActive)
                          TextButton(
                            onPressed: onPay,
                            style: TextButton.styleFrom(
                                padding: EdgeInsets.zero,
                                minimumSize: const Size(0, 32)),
                            child: const Text('Pagar'),
                          ),
                        Switch.adaptive(
                          value: payment.isActive,
                          onChanged: (_) => onToggle(),
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
