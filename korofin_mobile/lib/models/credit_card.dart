/// Franquicia de una tarjeta. En el backend es el enum `CardFranchise`.
enum CardFranchise {
  visa('VISA', 'Visa'),
  mastercard('MASTERCARD', 'Mastercard'),
  amex('AMEX', 'American Express'),
  diners('DINERS', 'Diners Club');

  const CardFranchise(this.wire, this.label);
  final String wire;
  final String label;

  static CardFranchise fromWire(String? v) => CardFranchise.values
      .firstWhere((f) => f.wire == v, orElse: () => CardFranchise.visa);
}

/// Tarjeta de crédito del usuario: `GET /api/cards` (`CreditCardResponse`).
class CreditCard {
  const CreditCard({
    required this.id,
    required this.name,
    required this.franchise,
    required this.creditLimit,
    required this.monthlyRate,
    required this.cutoffDay,
    required this.paymentDueDay,
    required this.currentBalance,
    required this.availableCredit,
    this.bank,
    this.lastCutoffDate,
  });

  final int id;
  final String name;
  final String? bank;
  final CardFranchise franchise;
  final double creditLimit;
  final double monthlyRate;
  final int cutoffDay;
  final int paymentDueDay;
  final double currentBalance;
  final double availableCredit;
  final DateTime? lastCutoffDate;

  double get usageRatio =>
      creditLimit == 0 ? 0 : (currentBalance / creditLimit).clamp(0, 1).toDouble();

  factory CreditCard.fromJson(Map<String, dynamic> json) => CreditCard(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String,
        bank: json['bank'] as String?,
        franchise: CardFranchise.fromWire(json['franchise'] as String?),
        creditLimit: (json['creditLimit'] as num).toDouble(),
        monthlyRate: (json['monthlyRate'] as num?)?.toDouble() ?? 0,
        cutoffDay: (json['cutoffDay'] as num?)?.toInt() ?? 1,
        paymentDueDay: (json['paymentDueDay'] as num?)?.toInt() ?? 1,
        currentBalance: (json['currentBalance'] as num?)?.toDouble() ?? 0,
        availableCredit: (json['availableCredit'] as num?)?.toDouble() ??
            (json['creditLimit'] as num).toDouble(),
        lastCutoffDate: json['lastCutoffDate'] == null
            ? null
            : DateTime.parse(json['lastCutoffDate'] as String),
      );
}

/// Tipo de movimiento de tarjeta. En el backend es `CardMovementType`.
enum CardMovementKind {
  purchase('PURCHASE', 'Compra'),
  installmentPurchase('INSTALLMENT_PURCHASE', 'Compra a cuotas'),
  payment('PAYMENT', 'Pago'),
  interest('INTEREST', 'Interés'),
  fee('FEE', 'Cargo');

  const CardMovementKind(this.wire, this.label);
  final String wire;
  final String label;

  static CardMovementKind fromWire(String? v) => CardMovementKind.values
      .firstWhere((k) => k.wire == v, orElse: () => CardMovementKind.purchase);

  bool get reducesBalance => this == CardMovementKind.payment;
}

/// Movimiento del ledger (inmutable) de una tarjeta: `CardMovementResponse`.
class CardMovement {
  const CardMovement({
    required this.id,
    required this.kind,
    required this.amount,
    required this.date,
    required this.cardBalanceAfter,
    this.description,
    this.installmentPlanId,
  });

  final int id;
  final CardMovementKind kind;
  final double amount;
  final DateTime date;
  final double cardBalanceAfter;
  final String? description;
  final int? installmentPlanId;

  bool get hasInstallments => installmentPlanId != null;

  factory CardMovement.fromJson(Map<String, dynamic> json) => CardMovement(
        id: (json['id'] as num).toInt(),
        kind: CardMovementKind.fromWire(json['type'] as String?),
        amount: (json['amount'] as num).toDouble(),
        date: DateTime.parse(json['date'] as String),
        cardBalanceAfter: (json['cardBalanceAfter'] as num?)?.toDouble() ?? 0,
        description: json['description'] as String?,
        installmentPlanId: (json['installmentPlanId'] as num?)?.toInt(),
      );
}

enum InstallmentStatus { pending, billed }

/// Una cuota de un plan: `InstallmentResponse`.
class Installment {
  const Installment({
    required this.number,
    required this.capitalAmount,
    required this.interestAmount,
    required this.dueDate,
    required this.status,
  });

  final int number;
  final double capitalAmount;
  final double interestAmount;
  final DateTime dueDate;
  final InstallmentStatus status;

  double get total => capitalAmount + interestAmount;

  factory Installment.fromJson(Map<String, dynamic> json) => Installment(
        number: (json['number'] as num).toInt(),
        capitalAmount: (json['capitalAmount'] as num).toDouble(),
        interestAmount: (json['interestAmount'] as num).toDouble(),
        dueDate: DateTime.parse(json['dueDate'] as String),
        status: (json['status'] as String?) == 'BILLED'
            ? InstallmentStatus.billed
            : InstallmentStatus.pending,
      );
}
