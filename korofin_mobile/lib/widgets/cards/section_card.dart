import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';

/// Standard bordered card wrapper used across dashboard/report sections,
/// with an optional header row (title + trailing action).
class SectionCard extends StatelessWidget {
  const SectionCard({
    super.key,
    required this.child,
    this.title,
    this.trailing,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  final Widget child;
  final String? title;
  final Widget? trailing;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (title != null) ...[
              Row(
                children: [
                  Expanded(child: Text(title!, style: Theme.of(context).textTheme.titleMedium)),
                  ?trailing,
                ],
              ),
              const SizedBox(height: AppSpacing.md),
            ],
            child,
          ],
        ),
      ),
    );
  }
}
