/// A credit card tracked in the "Tarjetas de crédito" sub-tab of the Deudas
/// hub, including its available/used quota.
class AppCreditCard {
  const AppCreditCard({
    required this.id,
    required this.name,
    required this.bank,
    required this.totalLimit,
    required this.usedAmount,
    required this.closingDay,
    this.lastFourDigits = '0000',
  });

  final String id;
  final String name;
  final String bank;
  final double totalLimit;
  final double usedAmount;
  final int closingDay;
  final String lastFourDigits;

  double get availableAmount => totalLimit - usedAmount;

  double get usageRatio => totalLimit == 0 ? 0 : (usedAmount / totalLimit).clamp(0, 1);
}

enum CardMovementType { purchase, payment }

class CardMovement {
  const CardMovement({
    required this.description,
    required this.amount,
    required this.date,
    required this.type,
  });

  final String description;
  final double amount;
  final DateTime date;
  final CardMovementType type;
}
