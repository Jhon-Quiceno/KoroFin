/// A row detected while parsing an imported bank statement (PDF/CSV), shown
/// in the confirmation preview table before import.
class ImportedMovement {
  ImportedMovement({
    required this.description,
    required this.amount,
    required this.date,
    this.selected = true,
  });

  final String description;
  final double amount;
  final DateTime date;
  bool selected;
}
