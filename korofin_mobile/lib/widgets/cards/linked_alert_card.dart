import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';

/// Actionable card used for the Dashboard's "alertas de vencimientos",
/// "Deudas activas" and AI-insight cards — each one links to another
/// screen/tab per the KoroFin information-architecture spec.
class LinkedAlertCard extends StatelessWidget {
  const LinkedAlertCard({
    super.key,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.description,
    required this.actionLabel,
    required this.onTap,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String description;
  final String actionLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(color: iconColor.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(AppRadii.md)),
                child: Icon(icon, color: iconColor, size: 20),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 2),
                    Text(description, style: Theme.of(context).textTheme.bodyMedium),
                    const SizedBox(height: AppSpacing.sm),
                    Row(
                      children: [
                        Text(
                          actionLabel,
                          style: TextStyle(color: iconColor, fontSize: 13, fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(width: 2),
                        Icon(Icons.arrow_forward_rounded, size: 14, color: iconColor),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
