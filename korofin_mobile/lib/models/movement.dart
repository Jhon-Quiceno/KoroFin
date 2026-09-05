/// Un movimiento: gasto o ingreso. Unifica `ExpenseResponse` e `IncomeResponse`
/// del backend, que solo se diferencian en que el gasto tiene método de pago.
enum MovementType { expense, income }

/// Método de pago de un gasto. En el backend es el enum `PaymentMethodType`.
enum PaymentMethod {
  cash('CASH', 'Efectivo'),
  debitCard('DEBIT_CARD', 'Débito'),
  creditCard('CREDIT_CARD', 'Crédito'),
  transfer('TRANSFER', 'Transferencia'),
  other('OTHER', 'Otro');

  const PaymentMethod(this.wire, this.label);

  final String wire;
  final String label;

  static PaymentMethod fromWire(String? value) => PaymentMethod.values.firstWhere(
        (m) => m.wire == value,
        orElse: () => PaymentMethod.cash,
      );
}

class Movement {
  const Movement({
    required this.id,
    required this.type,
    required this.amount,
    required this.date,
    this.description,
    this.categoryId,
    this.categoryName,
    this.paymentMethod,
  });

  final int id;
  final MovementType type;
  final double amount;
  final DateTime date;
  final String? description;
  final int? categoryId;
  final String? categoryName;

  /// Solo en gastos; `null` en ingresos.
  final PaymentMethod? paymentMethod;

  bool get isIncome => type == MovementType.income;

  /// Texto principal en la lista: la descripción, o la categoría, o un genérico.
  String get title {
    final String? d = description?.trim();
    if (d != null && d.isNotEmpty) return d;
    return categoryName ?? (isIncome ? 'Ingreso' : 'Gasto');
  }

  factory Movement.fromExpenseJson(Map<String, dynamic> json) => Movement(
        id: (json['id'] as num).toInt(),
        type: MovementType.expense,
        amount: (json['amount'] as num).toDouble(),
        date: DateTime.parse(json['date'] as String),
        description: json['description'] as String?,
        categoryId: (json['categoryId'] as num?)?.toInt(),
        categoryName: json['categoryName'] as String?,
        paymentMethod: PaymentMethod.fromWire(json['paymentMethod'] as String?),
      );

  factory Movement.fromIncomeJson(Map<String, dynamic> json) => Movement(
        id: (json['id'] as num).toInt(),
        type: MovementType.income,
        amount: (json['amount'] as num).toDouble(),
        date: DateTime.parse(json['date'] as String),
        description: json['description'] as String?,
        categoryId: (json['categoryId'] as num?)?.toInt(),
        categoryName: json['categoryName'] as String?,
      );
}

/// Payload de creación/edición de un movimiento. `paymentMethod` solo aplica a
/// gastos; en ingresos se ignora.
class MovementDraft {
  const MovementDraft({
    required this.amount,
    required this.date,
    this.description,
    this.categoryId,
    this.paymentMethod,
  });

  final double amount;
  final DateTime date;
  final String? description;
  final int? categoryId;
  final PaymentMethod? paymentMethod;

  Map<String, dynamic> toExpenseJson() => <String, dynamic>{
        'amount': amount,
        if (description != null && description!.isNotEmpty)
          'description': description,
        'date': isoDate(date),
        'paymentMethod': (paymentMethod ?? PaymentMethod.cash).wire,
        if (categoryId != null) 'categoryId': categoryId,
      };

  Map<String, dynamic> toIncomeJson() => <String, dynamic>{
        'amount': amount,
        if (description != null && description!.isNotEmpty)
          'description': description,
        'date': isoDate(date),
        if (categoryId != null) 'categoryId': categoryId,
      };

  static String isoDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';
}
