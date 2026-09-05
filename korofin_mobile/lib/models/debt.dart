/// Una deuda del usuario: `GET /api/debts` (`DebtResponse`).
///
/// `totalAmount` es fijo desde la creación; `remainingAmount` solo cambia vía
/// abonos (lo bajan) o cargos (lo suben).
class Debt {
  const Debt({
    required this.id,
    required this.name,
    required this.totalAmount,
    required this.remainingAmount,
    this.interestRate,
    this.dueDate,
  });

  final int id;
  final String name;
  final double totalAmount;
  final double remainingAmount;
  final double? interestRate;
  final DateTime? dueDate;

  double get paidAmount => (totalAmount - remainingAmount).clamp(0, totalAmount);

  double get progress =>
      totalAmount == 0 ? 0 : (paidAmount / totalAmount).clamp(0, 1).toDouble();

  bool get isSettled => remainingAmount <= 0;

  factory Debt.fromJson(Map<String, dynamic> json) => Debt(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String,
        totalAmount: (json['totalAmount'] as num).toDouble(),
        remainingAmount: (json['remainingAmount'] as num).toDouble(),
        interestRate: (json['interestRate'] as num?)?.toDouble(),
        dueDate: json['dueDate'] == null
            ? null
            : DateTime.parse(json['dueDate'] as String),
      );
}

/// Un abono contra una deuda: `DebtPaymentResponse`. Registro inmutable.
class DebtPayment {
  const DebtPayment({
    required this.id,
    required this.amount,
    required this.paymentDate,
    this.note,
  });

  final int id;
  final double amount;
  final DateTime paymentDate;
  final String? note;

  factory DebtPayment.fromJson(Map<String, dynamic> json) => DebtPayment(
        id: (json['id'] as num).toInt(),
        amount: (json['amount'] as num).toDouble(),
        paymentDate: DateTime.parse(json['paymentDate'] as String),
        note: json['note'] as String?,
      );
}

/// Un cargo contra una deuda: `DebtChargeResponse`. Registro inmutable.
class DebtCharge {
  const DebtCharge({
    required this.id,
    required this.amount,
    required this.chargeDate,
    this.description,
  });

  final int id;
  final double amount;
  final DateTime chargeDate;
  final String? description;

  factory DebtCharge.fromJson(Map<String, dynamic> json) => DebtCharge(
        id: (json['id'] as num).toInt(),
        amount: (json['amount'] as num).toDouble(),
        chargeDate: DateTime.parse(json['chargeDate'] as String),
        description: json['description'] as String?,
      );
}
