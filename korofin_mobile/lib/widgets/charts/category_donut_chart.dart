import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../models/category.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Donut chart of expenses by category with an accessible legend list,
/// used on Dashboard and Reportes.
class CategoryDonutChart extends StatelessWidget {
  const CategoryDonutChart({super.key, required this.entries});

  /// Each entry is (category, totalAmount).
  final List<(AppCategory, double)> entries;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final double total = entries.fold(0, (sum, e) => sum + e.$2);

    return Row(
      children: [
        SizedBox(
          width: 120,
          height: 120,
          child: PieChart(
            PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 34,
              sections: [
                for (final entry in entries)
                  PieChartSectionData(
                    value: entry.$2,
                    color: entry.$1.color,
                    radius: 20,
                    showTitle: false,
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(width: AppSpacing.lg),
        Expanded(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final entry in entries)
                Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                  child: Row(
                    children: [
                      Container(width: 8, height: 8, decoration: BoxDecoration(color: entry.$1.color, shape: BoxShape.circle)),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: Text(entry.$1.name, style: Theme.of(context).textTheme.bodyMedium, overflow: TextOverflow.ellipsis),
                      ),
                      Text(
                        total == 0 ? '0%' : '${(entry.$2 / total * 100).round()}%',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: koro.foreground),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
