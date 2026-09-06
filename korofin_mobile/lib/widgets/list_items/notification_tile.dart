import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/notification.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Fila de notificación con ícono por tipo y realce sutil mientras no está
/// leída. Deslizar de derecha a izquierda la marca como leída.
class NotificationTile extends StatelessWidget {
  const NotificationTile(
      {super.key, required this.notification, required this.onRead});

  final AppNotification notification;
  final VoidCallback onRead;

  Color _color() {
    switch (notification.kind) {
      case NotificationKind.overspendAlert:
      case NotificationKind.paymentReminder:
      case NotificationKind.cardCycleClose:
        return AppColors.warning;
      case NotificationKind.monthEndPrediction:
      case NotificationKind.weeklySummary:
        return AppColors.info;
      case NotificationKind.inactivityReminder:
      case NotificationKind.system:
        return AppColors.categoryPalette[5];
    }
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final Color color = _color();

    return Dismissible(
      key: ValueKey<int>(notification.id),
      direction: notification.read
          ? DismissDirection.none
          : DismissDirection.endToStart,
      confirmDismiss: (_) async {
        onRead();
        return false;
      },
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
        color: koro.success.withValues(alpha: 0.14),
        child: Icon(Icons.done_rounded, color: koro.success),
      ),
      child: Container(
        color: notification.read
            ? Colors.transparent
            : koro.accent.withValues(alpha: 0.05),
        padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.lg, vertical: AppSpacing.md),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.14),
                  shape: BoxShape.circle),
              child: Icon(notification.kind.icon, size: 17, color: color),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(notification.title,
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 2),
                  Text(notification.message,
                      style: Theme.of(context).textTheme.bodyMedium),
                  const SizedBox(height: 4),
                  Text(AppFormatters.relative(notification.createdAt),
                      style: TextStyle(
                          fontSize: 11, color: koro.mutedForeground)),
                ],
              ),
            ),
            if (!notification.read)
              Container(
                width: 8,
                height: 8,
                margin: const EdgeInsets.only(top: 4),
                decoration: BoxDecoration(
                    color: koro.accent, shape: BoxShape.circle),
              ),
          ],
        ),
      ),
    );
  }
}
