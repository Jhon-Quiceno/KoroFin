/// Frecuencia de un pago recurrente. En el backend es `RecurringFrequency`.
enum RecurringFrequency {
  monthly('MONTHLY', 'Mensual'),
  weekly('WEEKLY', 'Semanal');

  const RecurringFrequency(this.wire, this.label);
  final String wire;
  final String label;

  static RecurringFrequency fromWire(String? v) => RecurringFrequency.values
      .firstWhere((f) => f.wire == v, orElse: () => RecurringFrequency.monthly);
}

/// Un pago recurrente (suscripción, servicio, membresía):
/// `GET /api/recurring` (`RecurringPaymentResponse`).
class RecurringPayment {
  const RecurringPayment({
    required this.id,
    required this.name,
    required this.amount,
    required this.frequency,
    required this.nextPaymentDate,
    required this.isActive,
  });

  final int id;
  final String name;
  final double amount;
  final RecurringFrequency frequency;
  final DateTime nextPaymentDate;
  final bool isActive;

  bool get isDueSoon =>
      isActive && nextPaymentDate.difference(DateTime.now()).inDays < 5;

  factory RecurringPayment.fromJson(Map<String, dynamic> json) =>
      RecurringPayment(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String,
        amount: (json['amount'] as num).toDouble(),
        frequency: RecurringFrequency.fromWire(json['frequency'] as String?),
        nextPaymentDate: DateTime.parse(json['nextPaymentDate'] as String),
        isActive: json['isActive'] as bool? ?? true,
      );
}
