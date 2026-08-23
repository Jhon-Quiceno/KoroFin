import 'package:flutter/material.dart';

import '../../models/credit_card.dart';
import '../../theme/app_spacing.dart';

/// Modal for registering a new credit card (screen 9).
Future<AppCreditCard?> showNewCardSheet(BuildContext context) {
  return showModalBottomSheet<AppCreditCard>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => const _NewCardSheet(),
  );
}

class _NewCardSheet extends StatefulWidget {
  const _NewCardSheet();

  @override
  State<_NewCardSheet> createState() => _NewCardSheetState();
}

class _NewCardSheetState extends State<_NewCardSheet> {
  final _nameController = TextEditingController();
  final _bankController = TextEditingController();
  final _limitController = TextEditingController();
  final _digitsController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Nueva tarjeta de crédito', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(controller: _nameController, decoration: const InputDecoration(labelText: 'Nombre de la tarjeta')),
            const SizedBox(height: AppSpacing.md),
            TextField(controller: _bankController, decoration: const InputDecoration(labelText: 'Banco')),
            const SizedBox(height: AppSpacing.md),
            TextField(controller: _limitController, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Cupo total')),
            const SizedBox(height: AppSpacing.md),
            TextField(controller: _digitsController, maxLength: 4, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Últimos 4 dígitos')),
            const SizedBox(height: AppSpacing.sm),
            ElevatedButton(
              onPressed: () {
                final limit = double.tryParse(_limitController.text) ?? 0;
                if (_nameController.text.trim().isEmpty || limit <= 0) return;
                Navigator.of(context).pop(
                  AppCreditCard(
                    id: DateTime.now().millisecondsSinceEpoch.toString(),
                    name: _nameController.text.trim(),
                    bank: _bankController.text.trim().isEmpty ? 'Sin especificar' : _bankController.text.trim(),
                    totalLimit: limit,
                    usedAmount: 0,
                    closingDay: 1,
                    lastFourDigits: _digitsController.text.trim().isEmpty ? '0000' : _digitsController.text.trim(),
                  ),
                );
              },
              child: const Text('Guardar tarjeta'),
            ),
          ],
        ),
      ),
    );
  }
}
