import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../models/movement.dart';
import '../../state/movements/movements_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../movements/transaction_form_sheet.dart';

/// Pantalla 17 — Quick Add: alta rápida desde el FAB de cualquier pantalla
/// principal. Primero deja elegir entre cargar el movimiento a mano o
/// escanear un recibo; "a mano" reusa el formulario compartido con el toggle
/// Ingreso/Gasto y persiste el movimiento contra el backend, "escanear"
/// navega a la pantalla de escaneo (que ya tiene su propio flujo de guardado).
Future<void> showQuickAddSheet(BuildContext context, WidgetRef ref) async {
  final _QuickAddOption? option = await showModalBottomSheet<_QuickAddOption>(
    context: context,
    showDragHandle: true,
    builder: (_) => const _QuickAddOptionsSheet(),
  );
  if (option == null || !context.mounted) return;

  switch (option) {
    case _QuickAddOption.manual:
      await _addManually(context, ref);
    case _QuickAddOption.scanReceipt:
      context.push('/receipt-scan');
  }
}

Future<void> _addManually(BuildContext context, WidgetRef ref) async {
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

enum _QuickAddOption { manual, scanReceipt }

/// Sheet chico con las dos formas de registrar un movimiento desde el FAB.
class _QuickAddOptionsSheet extends StatelessWidget {
  const _QuickAddOptionsSheet();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Agregar movimiento',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            _QuickAddOptionTile(
              icon: Icons.edit_outlined,
              label: 'Agregar manualmente',
              onTap: () =>
                  Navigator.of(context).pop(_QuickAddOption.manual),
            ),
            const SizedBox(height: AppSpacing.sm),
            _QuickAddOptionTile(
              icon: Icons.document_scanner_outlined,
              label: 'Escanear recibo',
              onTap: () =>
                  Navigator.of(context).pop(_QuickAddOption.scanReceipt),
            ),
          ],
        ),
      ),
    );
  }
}

class _QuickAddOptionTile extends StatelessWidget {
  const _QuickAddOptionTile(
      {required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(icon, size: 20, color: koro.accent),
      title: Text(label),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}
