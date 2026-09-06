import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/models/analysis.dart';
import 'package:korofin_mobile/models/movement.dart';
import 'package:korofin_mobile/models/report.dart';

void main() {
  test('AnalysisSummary.fromJson parsea totales, categorías y serie', () {
    final s = AnalysisSummary.fromJson(<String, dynamic>{
      'periodYear': 2026,
      'periodMonth': 9,
      'totalIncome': 5000000,
      'totalExpense': 3200000,
      'totalSavings': 1800000,
      'savingsRate': 36.0,
      'topExpenseCategories': <dynamic>[
        <String, dynamic>{'categoryId': 1, 'categoryName': 'Comida', 'total': 900000},
        <String, dynamic>{'categoryId': null, 'categoryName': 'Sin categoría', 'total': 100000},
      ],
      'monthlySeries': <dynamic>[
        <String, dynamic>{
          'periodYear': 2026,
          'periodMonth': 8,
          'income': 4000000,
          'expense': 3000000,
        },
      ],
    });

    expect(s.month, 9);
    expect(s.savingsRate, 36.0);
    expect(s.topExpenseCategories, hasLength(2));
    expect(s.topExpenseCategories[1].categoryId, isNull);
    expect(s.monthlySeries.single.savings, 1000000);
  });

  test('MonthEndPrediction.fromJson', () {
    final p = MonthEndPrediction.fromJson(<String, dynamic>{
      'periodYear': 2026,
      'periodMonth': 9,
      'currentExpense': 1200000,
      'averageDailyExpense': 120000,
      'projectedExpense': 3600000,
      'daysElapsed': 10,
      'daysInMonth': 30,
    });

    expect(p.projectedExpense, 3600000);
    expect(p.daysElapsed, 10);
  });

  test('MonthlyReport.fromJson y ReportMovement.fromJson mapean el tipo', () {
    final r = MonthlyReport.fromJson(<String, dynamic>{
      'periodYear': 2026,
      'periodMonth': 9,
      'totalIncome': 1000,
      'totalExpense': 400,
      'totalSavings': 600,
      'savingsRate': 60,
      'topExpenseCategories': <dynamic>[],
    });
    expect(r.totalSavings, 600);

    final m = ReportMovement.fromJson(<String, dynamic>{
      'id': 3,
      'type': 'INCOME',
      'date': '2026-09-02',
      'amount': 250000,
      'description': null,
      'categoryName': 'Salario',
    });
    expect(m.type, MovementType.income);
    expect(m.isIncome, isTrue);
    expect(m.categoryName, 'Salario');
  });
}
