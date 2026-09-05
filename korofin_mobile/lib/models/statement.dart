import 'movement.dart';

/// Una fila detectada al previsualizar un extracto: `ImportPreviewRow`.
/// Mutable en `selected` porque el usuario marca cuáles importar.
class StatementRow {
  StatementRow({
    required this.date,
    required this.description,
    required this.amount,
    required this.type,
    required this.isDuplicate,
    this.suggestedCategoryId,
    this.suggestedCategoryName,
    bool? selected,
  }) : selected = selected ?? !isDuplicate; // los duplicados arrancan sin marcar

  final DateTime date;
  final String description;
  final double amount;
  final MovementType type;
  final bool isDuplicate;
  final int? suggestedCategoryId;
  final String? suggestedCategoryName;
  bool selected;

  factory StatementRow.fromJson(Map<String, dynamic> json) => StatementRow(
        date: DateTime.parse(json['date'] as String),
        description: json['description'] as String? ?? '',
        amount: (json['amount'] as num).toDouble(),
        type: (json['movementType'] as String?) == 'INCOME'
            ? MovementType.income
            : MovementType.expense,
        isDuplicate: json['isDuplicate'] as bool? ?? false,
        suggestedCategoryId: (json['suggestedCategoryId'] as num?)?.toInt(),
        suggestedCategoryName: json['suggestedCategoryName'] as String?,
      );

  Map<String, dynamic> toConfirmJson() => <String, dynamic>{
        'movementType': type == MovementType.income ? 'INCOME' : 'EXPENSE',
        'amount': amount,
        'date': MovementDraft.isoDate(date),
        if (description.isNotEmpty) 'description': description,
        if (suggestedCategoryId != null) 'categoryId': suggestedCategoryId,
      };
}

/// Resultado de `POST /api/statement-imports/preview`.
class StatementPreview {
  const StatementPreview({
    required this.rows,
    required this.totalRows,
    required this.duplicateRows,
  });

  final List<StatementRow> rows;
  final int totalRows;
  final int duplicateRows;

  factory StatementPreview.fromJson(Map<String, dynamic> json) =>
      StatementPreview(
        rows: ((json['rows'] as List<dynamic>?) ?? const <dynamic>[])
            .map((e) => StatementRow.fromJson(e as Map<String, dynamic>))
            .toList(),
        totalRows: (json['totalRows'] as num?)?.toInt() ?? 0,
        duplicateRows: (json['duplicateRows'] as num?)?.toInt() ?? 0,
      );
}
