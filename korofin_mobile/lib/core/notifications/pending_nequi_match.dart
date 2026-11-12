import 'dart:convert';

import '../../models/movement.dart';

/// Un match de parsing de Nequi pendiente de confirmación del usuario: el
/// gasto/ingreso sugerido que todavía no se creó. Viaja como payload de la
/// notificación local propia (serializado con [encode]/[decode]) para
/// sobrevivir a que la app esté cerrada entre la detección y el toque del
/// usuario.
class PendingNequiMatch {
  const PendingNequiMatch({
    required this.type,
    required this.amount,
    this.description,
    this.key,
  });

  final MovementType type;
  final double amount;
  final String? description;

  /// Identifica la notificación de Nequi que originó el match, para no
  /// encolar dos veces el mismo movimiento. `null` en payloads antiguos.
  final String? key;

  /// Borrador para pre-llenar `showTransactionForm`. El `id` es un placeholder
  /// (el movimiento todavía no existe) — el formulario no lo usa para nada
  /// más que decidir si es alta o edición junto con el resto de los campos.
  Movement toMovement() => Movement(
        id: 0,
        type: type,
        amount: amount,
        date: DateTime.now(),
        description: description,
      );

  String encode() => jsonEncode(<String, dynamic>{
        'type': type.name,
        'amount': amount,
        'description': description,
        'key': key,
      });

  static PendingNequiMatch? decode(String? payload) {
    if (payload == null || payload.isEmpty) return null;
    try {
      final Map<String, dynamic> data = jsonDecode(payload) as Map<String, dynamic>;
      final MovementType type = MovementType.values.firstWhere(
        (t) => t.name == data['type'],
        orElse: () => MovementType.expense,
      );
      final num? amount = data['amount'] as num?;
      if (amount == null) return null;
      return PendingNequiMatch(
        type: type,
        amount: amount.toDouble(),
        description: data['description'] as String?,
        key: data['key'] as String?,
      );
    } on FormatException {
      return null;
    }
  }
}
