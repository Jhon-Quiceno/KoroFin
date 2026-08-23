import 'package:flutter/material.dart';

import '../../models/subscription.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';

/// Modal for registering a new recurring payment (screen 10).
Future<Subscription?> showNewSubscriptionSheet(BuildContext context) {
  return showModalBottomSheet<Subscription>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => const _NewSubscriptionSheet(),
  );
}

class _NewSubscriptionSheet extends StatefulWidget {
  const _NewSubscriptionSheet();

  @override
  State<_NewSubscriptionSheet> createState() => _NewSubscriptionSheetState();
}

class _NewSubscriptionSheetState extends State<_NewSubscriptionSheet> {
  final _nameController = TextEditingController();
  final _amountController = TextEditingController();
  DateTime _nextCharge = DateTime.now().add(const Duration(days: 30));

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Nuevo servicio o suscripción', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(controller: _nameController, decoration: const InputDecoration(labelText: 'Nombre')),
            const SizedBox(height: AppSpacing.md),
            TextField(controller: _amountController, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Monto')),
            const SizedBox(height: AppSpacing.md),
            InkWell(
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _nextCharge,
                  firstDate: DateTime.now(),
                  lastDate: DateTime.now().add(const Duration(days: 365)),
                );
                if (picked != null) setState(() => _nextCharge = picked);
              },
              child: InputDecorator(
                decoration: const InputDecoration(labelText: 'Próximo cobro'),
                child: Text('${_nextCharge.day}/${_nextCharge.month}/${_nextCharge.year}'),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            ElevatedButton(
              onPressed: () {
                final amount = double.tryParse(_amountController.text) ?? 0;
                if (_nameController.text.trim().isEmpty || amount <= 0) return;
                Navigator.of(context).pop(
                  Subscription(
                    id: DateTime.now().millisecondsSinceEpoch.toString(),
                    name: _nameController.text.trim(),
                    icon: Icons.subscriptions_outlined,
                    color: AppColors.categoryPalette[3],
                    amount: amount,
                    nextChargeDate: _nextCharge,
                  ),
                );
              },
              child: const Text('Guardar'),
            ),
          ],
        ),
      ),
    );
  }
}
