import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_theme.dart';

/// 6-month trend line chart used in Reportes/Análisis.
class TrendLineChart extends StatelessWidget {
  const TrendLineChart({super.key, required this.months, required this.values});

  final List<String> months;
  final List<double> values;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return SizedBox(
      height: 180,
      child: LineChart(
        LineChartData(
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
                  if (index < 0 || index >= months.length) return const SizedBox.shrink();
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(months[index], style: TextStyle(fontSize: 11, color: koro.mutedForeground)),
                  );
                },
              ),
            ),
          ),
          lineTouchData: LineTouchData(
            enabled: true,
            touchTooltipData: LineTouchTooltipData(
              // Por defecto fl_chart muestra `spot.y.toString()` (ej.
              // "4000000.0"); lo reemplazamos por el formato de moneda COP.
              getTooltipItems: (touchedSpots) => touchedSpots
                  .map(
                    (spot) => LineTooltipItem(
                      AppFormatters.currency(spot.y),
                      TextStyle(
                        color: spot.bar.gradient?.colors.first ??
                            spot.bar.color ??
                            Colors.blueGrey,
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
          lineBarsData: [
            LineChartBarData(
              spots: [for (int i = 0; i < values.length; i++) FlSpot(i.toDouble(), values[i])],
              isCurved: true,
              color: AppColors.accent,
              barWidth: 3,
              dotData: const FlDotData(show: false),
              belowBarData: BarAreaData(show: true, color: AppColors.accent.withValues(alpha: 0.12)),
            ),
          ],
        ),
      ),
    );
  }
}
