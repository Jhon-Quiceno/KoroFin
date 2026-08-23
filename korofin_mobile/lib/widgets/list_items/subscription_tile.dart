import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/subscription.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../common/app_badge.dart';

/// Recurring-payment row with next-charge date and a "vence pronto" badge
/// when the charge is due in less than 5 days.
class SubscriptionTile extends StatelessWidget {
  const SubscriptionTile({super.key, required this.subscription, this.onTap});

  final Subscription subscription;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(color: subscription.color.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(AppRadii.md)),
                child: Icon(subscription.icon, size: 18, color: subscription.color),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(subscription.name, style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 2),
                    Text('Próximo cobro: ${AppFormatters.shortDate(subscription.nextChargeDate)}', style: Theme.of(context).textTheme.bodyMedium),
                    if (subscription.isDueSoon) ...[
                      const SizedBox(height: 4),
                      const AppBadge(label: 'Vence pronto', color: AppColors.warning, icon: Icons.schedule),
                    ],
                  ],
                ),
              ),
              Text(AppFormatters.currency(subscription.amount), style: Theme.of(context).textTheme.titleMedium),
            ],
          ),
        ),
      ),
    );
  }
}
