import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Small KPI summary tile used in Reportes (total ingresos/gastos/ahorro).
class KpiCard extends StatelessWidget {
  const KpiCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
    this.trend,
  });

  final String label;
  final String value;
  final IconData icon;
  final Color color;
  final String? trend;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: koro.surface,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: koro.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(color: color.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(AppRadii.sm)),
            child: Icon(icon, size: 16, color: color),
          ),
          const SizedBox(height: AppSpacing.md),
          Text(label, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 2),
          Text(value, style: Theme.of(context).textTheme.titleLarge, maxLines: 1, overflow: TextOverflow.ellipsis),
          if (trend != null) ...[
            const SizedBox(height: 2),
            Text(trend!, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color)),
          ],
        ],
      ),
    );
  }
}
