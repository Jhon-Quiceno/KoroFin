import 'package:flutter/material.dart';

import '../../models/debt.dart';
import '../../theme/app_spacing.dart';

/// Modal for registering a new debt (screen 5 — Deudas).
Future<Debt?> showNewDebtSheet(BuildContext context) {
  return showModalBottomSheet<Debt>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => const _NewDebtSheet(),
  );
}

class _NewDebtSheet extends StatefulWidget {
  const _NewDebtSheet();

  @override
  State<_NewDebtSheet> createState() => _NewDebtSheetState();
}

class _NewDebtSheetState extends State<_NewDebtSheet> {
  final _nameController = TextEditingController();
  final _lenderController = TextEditingController();
  final _totalController = TextEditingController();
  DateTime _dueDate = DateTime.now().add(const Duration(days: 365));

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Nueva deuda', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(controller: _nameController, decoration: const InputDecoration(labelText: 'Nombre de la deuda')),
            const SizedBox(height: AppSpacing.md),
            TextField(controller: _lenderController, decoration: const InputDecoration(labelText: 'Entidad')),
            const SizedBox(height: AppSpacing.md),
            TextField(controller: _totalController, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Monto total')),
            const SizedBox(height: AppSpacing.md),
            InkWell(
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _dueDate,
                  firstDate: DateTime.now(),
                  lastDate: DateTime.now().add(const Duration(days: 365 * 10)),
                );
                if (picked != null) setState(() => _dueDate = picked);
              },
              child: InputDecorator(
                decoration: const InputDecoration(labelText: 'Fecha límite'),
                child: Text('${_dueDate.day}/${_dueDate.month}/${_dueDate.year}'),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            ElevatedButton(
              onPressed: () {
                final total = double.tryParse(_totalController.text) ?? 0;
                if (_nameController.text.trim().isEmpty || total <= 0) return;
                Navigator.of(context).pop(
                  Debt(
                    id: DateTime.now().millisecondsSinceEpoch.toString(),
                    name: _nameController.text.trim(),
                    lender: _lenderController.text.trim().isEmpty ? 'Sin especificar' : _lenderController.text.trim(),
                    totalAmount: total,
                    paidAmount: 0,
                    dueDate: _dueDate,
                    payments: const [],
                  ),
                );
              },
              child: const Text('Guardar deuda'),
            ),
          ],
        ),
      ),
    );
  }
}
