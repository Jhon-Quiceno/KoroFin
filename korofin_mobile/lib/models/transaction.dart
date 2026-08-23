import 'category.dart';

enum TransactionType { income, expense }

/// A single income or expense movement, as shown in the "Movimientos"
/// (Ingresos/Gastos) list and the Dashboard's recent-transactions card.
class AppTransaction {
  const AppTransaction({
    required this.id,
    required this.title,
    required this.category,
    required this.amount,
    required this.date,
    required this.type,
    this.categorizedByAi = false,
    this.fromTelegram = false,
  });

  final String id;
  final String title;
  final AppCategory category;
  final double amount;
  final DateTime date;
  final TransactionType type;
  final bool categorizedByAi;
  final bool fromTelegram;
}
