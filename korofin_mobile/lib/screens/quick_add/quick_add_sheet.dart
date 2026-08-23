import 'package:flutter/material.dart';

import '../../models/transaction.dart';
import '../movements/transaction_form_sheet.dart';

/// Screen 17 — Quick Add: the fast bottom-sheet entry point reachable from
/// the FAB on every main screen (Inicio, Movimientos, Deudas, Asistente).
/// Reuses the shared transaction form with the Ingreso/Gasto toggle on.
Future<AppTransaction?> showQuickAddSheet(BuildContext context) {
  return showTransactionForm(context, type: TransactionType.expense, allowTypeToggle: true);
}
