/// A single payment made against a [Debt], shown in its detail history.
class DebtPayment {
  const DebtPayment({required this.date, required this.amount});

  final DateTime date;
  final double amount;
}

/// An active debt (loan, financing, etc.) tracked in the "Deudas" tab.
class Debt {
  const Debt({
    required this.id,
    required this.name,
    required this.lender,
    required this.totalAmount,
    required this.paidAmount,
    required this.dueDate,
    required this.payments,
  });

  final String id;
  final String name;
  final String lender;
  final double totalAmount;
  final double paidAmount;
  final DateTime dueDate;
  final List<DebtPayment> payments;

  double get remainingAmount => totalAmount - paidAmount;

  double get progress => totalAmount == 0 ? 0 : (paidAmount / totalAmount).clamp(0, 1);
}
