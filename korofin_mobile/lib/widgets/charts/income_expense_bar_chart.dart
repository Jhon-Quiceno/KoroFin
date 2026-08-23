import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_theme.dart';

/// Monthly income-vs-expense bar chart shown on the Dashboard.
class IncomeExpenseBarChart extends StatelessWidget {
  const IncomeExpenseBarChart({super.key, required this.data});

  /// Each entry is (label, income, expense).
  final List<(String, double, double)> data;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final double maxY = data
        .map((e) => e.$2 > e.$3 ? e.$2 : e.$3)
        .fold<double>(0, (a, b) => a > b ? a : b) * 1.2;

    return SizedBox(
      height: 180,
      child: BarChart(
        BarChartData(
          maxY: maxY == 0 ? 1 : maxY,
          alignment: BarChartAlignment.spaceAround,
          gridData: const FlGridData(show: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 26,
                getTitlesWidget: (value, meta) {
                  final int index = value.toInt();
                  if (index < 0 || index >= data.length) return const SizedBox.shrink();
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(data[index].$1, style: TextStyle(fontSize: 11, color: koro.mutedForeground)),
                  );
                },
              ),
            ),
          ),
          barTouchData: BarTouchData(enabled: true),
          barGroups: [
            for (int i = 0; i < data.length; i++)
              BarChartGroupData(
                x: i,
                barRods: [
                  BarChartRodData(toY: data[i].$2, color: AppColors.success, width: 8, borderRadius: BorderRadius.circular(4)),
                  BarChartRodData(toY: data[i].$3, color: AppColors.accent, width: 8, borderRadius: BorderRadius.circular(4)),
                ],
                barsSpace: 4,
              ),
          ],
        ),
      ),
    );
  }
}
