import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/transaction.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../common/app_badge.dart';

/// Row for a single income/expense movement — icon + category color,
/// title, date, and amount (green for income, foreground for expense).
class TransactionTile extends StatelessWidget {
  const TransactionTile({super.key, required this.transaction, this.onTap});

  final AppTransaction transaction;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final bool isIncome = transaction.type == TransactionType.income;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.md),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: transaction.category.color.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(AppRadii.md),
              ),
              child: Icon(transaction.category.icon, size: 18, color: transaction.category.color),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(transaction.title, style: Theme.of(context).textTheme.titleMedium, maxLines: 1, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 2),
                  Row(
                    children: [
                      Text(transaction.category.name, style: Theme.of(context).textTheme.bodyMedium),
                      Text(' · ${AppFormatters.relative(transaction.date)}', style: Theme.of(context).textTheme.bodyMedium),
                    ],
                  ),
                  if (transaction.categorizedByAi || transaction.fromTelegram) ...[
                    const SizedBox(height: 4),
                    Wrap(
                      spacing: 6,
                      children: [
                        if (transaction.categorizedByAi)
                          const AppBadge(label: 'Categorizado por IA', color: AppColors.info, icon: Icons.auto_awesome),
                        if (transaction.fromTelegram)
                          const AppBadge(label: 'Vía Telegram', color: AppColors.info, icon: Icons.send_outlined),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            Text(
              '${isIncome ? '+' : '-'}${AppFormatters.currency(transaction.amount)}',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: isIncome ? koro.success : koro.foreground,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
