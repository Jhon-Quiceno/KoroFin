import 'analysis.dart';
import 'movement.dart';

/// Cifras del mes: `GET /api/reports/monthly`. Misma forma que el resumen pero
/// sin la serie mensual.
class MonthlyReport {
  const MonthlyReport({
    required this.year,
    required this.month,
    required this.totalIncome,
    required this.totalExpense,
    required this.totalSavings,
    required this.savingsRate,
    required this.topExpenseCategories,
  });

  final int year;
  final int month;
  final double totalIncome;
  final double totalExpense;
  final double totalSavings;
  final double savingsRate;
  final List<CategoryTotal> topExpenseCategories;

  factory MonthlyReport.fromJson(Map<String, dynamic> json) => MonthlyReport(
        year: (json['periodYear'] as num).toInt(),
        month: (json['periodMonth'] as num).toInt(),
        totalIncome: (json['totalIncome'] as num?)?.toDouble() ?? 0,
        totalExpense: (json['totalExpense'] as num?)?.toDouble() ?? 0,
        totalSavings: (json['totalSavings'] as num?)?.toDouble() ?? 0,
        savingsRate: (json['savingsRate'] as num?)?.toDouble() ?? 0,
        topExpenseCategories: ((json['topExpenseCategories'] as List<dynamic>?) ??
                const <dynamic>[])
            .map((e) => CategoryTotal.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}

/// Una fila de la tabla de movimientos del período: `GET /api/reports/movements`.
class ReportMovement {
  const ReportMovement({
    required this.id,
    required this.type,
    required this.date,
    required this.amount,
    required this.categoryName,
    this.description,
  });

  final int id;
  final MovementType type;
  final DateTime date;
  final double amount;
  final String? description;
  final String categoryName;

  bool get isIncome => type == MovementType.income;

  factory ReportMovement.fromJson(Map<String, dynamic> json) => ReportMovement(
        id: (json['id'] as num).toInt(),
        type: (json['type'] as String?) == 'INCOME'
            ? MovementType.income
            : MovementType.expense,
        date: DateTime.parse(json['date'] as String),
        amount: (json['amount'] as num).toDouble(),
        description: json['description'] as String?,
        categoryName: json['categoryName'] as String? ?? 'Sin categoría',
      );
}
