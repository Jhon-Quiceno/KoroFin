import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Un segmento del donut: etiqueta, monto y color.
typedef DonutEntry = ({String label, double value, Color color});

/// Donut de gastos por categoría con una leyenda accesible al lado. Usado en
/// el Dashboard.
class CategoryDonutChart extends StatelessWidget {
  const CategoryDonutChart({super.key, required this.entries});

  final List<DonutEntry> entries;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final double total = entries.fold(0, (sum, e) => sum + e.value);

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
                    value: entry.value,
                    color: entry.color,
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
                      Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                              color: entry.color, shape: BoxShape.circle)),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: Text(entry.label,
                            style: Theme.of(context).textTheme.bodyMedium,
                            overflow: TextOverflow.ellipsis),
                      ),
                      Text(
                        total == 0
                            ? '0%'
                            : '${(entry.value / total * 100).round()}%',
                        style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: koro.foreground),
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
