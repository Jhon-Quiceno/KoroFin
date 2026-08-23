import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/notification_item.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Notification row with a type-specific icon and a subtle highlight while
/// unread. Swipe-to-dismiss marks it as read.
class NotificationTile extends StatelessWidget {
  const NotificationTile({super.key, required this.notification, required this.onRead});

  final NotificationItem notification;
  final VoidCallback onRead;

  static final Map<NotificationType, (IconData, Color)> _style = {
    NotificationType.dueDate: (Icons.schedule, AppColors.warning),
    NotificationType.ai: (Icons.auto_awesome, AppColors.info),
    NotificationType.system: (Icons.notifications_outlined, AppColors.categoryPalette[5]),
  };

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final (icon, color) = _style[notification.type]!;

    return Dismissible(
      key: ValueKey(notification.id),
      direction: DismissDirection.endToStart,
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
        color: notification.isRead ? Colors.transparent : koro.accent.withValues(alpha: 0.05),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(color: color.withValues(alpha: 0.14), shape: BoxShape.circle),
              child: Icon(icon, size: 17, color: color),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(notification.title, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 2),
                  Text(notification.description, style: Theme.of(context).textTheme.bodyMedium),
                  const SizedBox(height: 4),
                  Text(AppFormatters.relative(notification.date), style: TextStyle(fontSize: 11, color: koro.mutedForeground)),
                ],
              ),
            ),
            if (!notification.isRead)
              Container(width: 8, height: 8, margin: const EdgeInsets.only(top: 4), decoration: BoxDecoration(color: koro.accent, shape: BoxShape.circle)),
          ],
        ),
      ),
    );
  }
}
