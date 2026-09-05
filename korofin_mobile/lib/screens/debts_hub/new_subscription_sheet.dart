import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/recurring_payment.dart';
import '../../theme/app_spacing.dart';

typedef RecurringFormData = ({
  String name,
  double amount,
  RecurringFrequency frequency,
  DateTime firstPaymentDate,
});

/// Modal para registrar o editar un pago recurrente. En edición no se pide la
/// fecha del primer pago (la maneja el backend).
Future<RecurringFormData?> showNewSubscriptionSheet(BuildContext context,
    {RecurringPayment? initial}) {
  return showModalBottomSheet<RecurringFormData>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _NewSubscriptionSheet(initial: initial),
  );
}

class _NewSubscriptionSheet extends StatefulWidget {
  const _NewSubscriptionSheet({this.initial});
  final RecurringPayment? initial;

  @override
  State<_NewSubscriptionSheet> createState() => _NewSubscriptionSheetState();
}

class _NewSubscriptionSheetState extends State<_NewSubscriptionSheet> {
  late final TextEditingController _name =
      TextEditingController(text: widget.initial?.name ?? '');
  late final TextEditingController _amount = TextEditingController(
      text: widget.initial != null
          ? widget.initial!.amount.toStringAsFixed(0)
          : '');
  late RecurringFrequency _frequency =
      widget.initial?.frequency ?? RecurringFrequency.monthly;
  DateTime _firstDate = DateTime.now().add(const Duration(days: 7));
  String? _error;

  bool get _isEdit => widget.initial != null;

  @override
  void dispose() {
    _name.dispose();
    _amount.dispose();
    super.dispose();
  }

  void _submit() {
    final String name = _name.text.trim();
    final double amount = double.tryParse(
            _amount.text.replaceAll('.', '').replaceAll(',', '')) ??
        0;
    if (name.isEmpty || amount <= 0) {
      setState(() => _error = 'Revisá el nombre y el monto.');
      return;
    }
    Navigator.of(context).pop((
      name: name,
      amount: amount,
      frequency: _frequency,
      firstPaymentDate:
          _isEdit ? widget.initial!.nextPaymentDate : _firstDate,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg,
            AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(_isEdit ? 'Editar servicio' : 'Nuevo servicio o suscripción',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(
                controller: _name,
                decoration: const InputDecoration(labelText: 'Nombre')),
            const SizedBox(height: AppSpacing.md),
            TextField(
                controller: _amount,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Monto')),
            const SizedBox(height: AppSpacing.md),
            SegmentedButton<RecurringFrequency>(
              segments: const [
                ButtonSegment(
                    value: RecurringFrequency.monthly, label: Text('Mensual')),
                ButtonSegment(
                    value: RecurringFrequency.weekly, label: Text('Semanal')),
              ],
              selected: {_frequency},
              onSelectionChanged: (s) => setState(() => _frequency = s.first),
            ),
            if (!_isEdit) ...[
              const SizedBox(height: AppSpacing.md),
              InkWell(
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: _firstDate,
                    firstDate: DateTime.now(),
                    lastDate:
                        DateTime.now().add(const Duration(days: 365)),
                  );
                  if (picked != null) setState(() => _firstDate = picked);
                },
                child: InputDecorator(
                  decoration:
                      const InputDecoration(labelText: 'Primer pago'),
                  child: Text(AppFormatters.longDate(_firstDate)),
                ),
              ),
            ],
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(_error!,
                  style: TextStyle(
                      color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: AppSpacing.lg),
            ElevatedButton(
                onPressed: _submit, child: const Text('Guardar')),
          ],
        ),
      ),
    );
  }
}
