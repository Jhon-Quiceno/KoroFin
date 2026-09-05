import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../models/movement.dart';
import '../../state/movements/movements_controller.dart';
import '../movements/transaction_form_sheet.dart';

/// Pantalla 17 — Quick Add: alta rápida desde el FAB de cualquier pantalla
/// principal. Reusa el formulario compartido con el toggle Ingreso/Gasto y
/// persiste el movimiento contra el backend.
Future<void> showQuickAddSheet(BuildContext context, WidgetRef ref) async {
  final MovementFormResult? result = await showTransactionForm(
    context,
    type: MovementType.expense,
    allowTypeToggle: true,
  );
  if (result == null) return;
  try {
    await ref.read(movementsProvider(result.type).notifier).add(result.draft);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Movimiento guardado')),
      );
    }
  } on ApiException catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    }
  }
}
