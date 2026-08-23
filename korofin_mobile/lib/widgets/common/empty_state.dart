import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Generic empty state: icon, title, description and an optional set of
/// suggestion chips (used by the Asistente IA chat).
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    this.child,
  });

  final IconData icon;
  final String title;
  final String description;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(color: koro.surfaceElevated, shape: BoxShape.circle),
              child: Icon(icon, size: 30, color: koro.mutedForeground),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text(title, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.sm),
            Text(
              description,
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            if (child != null) ...[const SizedBox(height: AppSpacing.xl), child!],
          ],
        ),
      ),
    );
  }
}
