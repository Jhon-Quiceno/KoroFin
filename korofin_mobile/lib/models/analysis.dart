/// Un total de gasto agrupado por categoría dentro del resumen financiero.
class CategoryTotal {
  const CategoryTotal({
    required this.categoryName,
    required this.total,
    this.categoryId,
  });

  final int? categoryId;
  final String categoryName;
  final double total;

  factory CategoryTotal.fromJson(Map<String, dynamic> json) => CategoryTotal(
        categoryId: (json['categoryId'] as num?)?.toInt(),
        categoryName: json['categoryName'] as String? ?? 'Sin categoría',
        total: (json['total'] as num?)?.toDouble() ?? 0,
      );
}

/// Un punto de la serie mensual (ingresos/gastos de un mes).
class MonthlyTotal {
  const MonthlyTotal({
    required this.year,
    required this.month,
    required this.income,
    required this.expense,
  });

  final int year;
  final int month;
  final double income;
  final double expense;

  double get savings => income - expense;

  factory MonthlyTotal.fromJson(Map<String, dynamic> json) => MonthlyTotal(
        year: (json['periodYear'] as num).toInt(),
        month: (json['periodMonth'] as num).toInt(),
        income: (json['income'] as num?)?.toDouble() ?? 0,
        expense: (json['expense'] as num?)?.toDouble() ?? 0,
      );
}

/// Resumen financiero del mes: `GET /api/analysis/summary`.
class AnalysisSummary {
  const AnalysisSummary({
    required this.year,
    required this.month,
    required this.totalIncome,
    required this.totalExpense,
    required this.totalSavings,
    required this.savingsRate,
    required this.topExpenseCategories,
    required this.monthlySeries,
  });

  final int year;
  final int month;
  final double totalIncome;
  final double totalExpense;
  final double totalSavings;

  /// Porcentaje 0-100.
  final double savingsRate;
  final List<CategoryTotal> topExpenseCategories;
  final List<MonthlyTotal> monthlySeries;

  factory AnalysisSummary.fromJson(Map<String, dynamic> json) => AnalysisSummary(
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
        monthlySeries:
            ((json['monthlySeries'] as List<dynamic>?) ?? const <dynamic>[])
                .map((e) => MonthlyTotal.fromJson(e as Map<String, dynamic>))
                .toList(growable: false),
      );
}

/// Recomendación en texto libre: `GET /api/analysis/recommendations`.
class Recommendation {
  const Recommendation({required this.title, required this.message});

  final String title;
  final String message;

  factory Recommendation.fromJson(Map<String, dynamic> json) => Recommendation(
        title: json['title'] as String? ?? '',
        message: json['message'] as String? ?? '',
      );
}

/// Proyección de gasto de fin de mes: `GET /api/analysis/prediction`.
class MonthEndPrediction {
  const MonthEndPrediction({
    required this.currentExpense,
    required this.averageDailyExpense,
    required this.projectedExpense,
    required this.daysElapsed,
    required this.daysInMonth,
  });

  final double currentExpense;
  final double averageDailyExpense;
  final double projectedExpense;
  final int daysElapsed;
  final int daysInMonth;

  factory MonthEndPrediction.fromJson(Map<String, dynamic> json) =>
      MonthEndPrediction(
        currentExpense: (json['currentExpense'] as num?)?.toDouble() ?? 0,
        averageDailyExpense:
            (json['averageDailyExpense'] as num?)?.toDouble() ?? 0,
        projectedExpense: (json['projectedExpense'] as num?)?.toDouble() ?? 0,
        daysElapsed: (json['daysElapsed'] as num?)?.toInt() ?? 0,
        daysInMonth: (json['daysInMonth'] as num?)?.toInt() ?? 30,
      );
}
