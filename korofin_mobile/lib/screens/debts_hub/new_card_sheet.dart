import 'package:flutter/material.dart';

import '../../models/credit_card.dart';
import '../../theme/app_spacing.dart';

typedef CardFormData = ({
  String name,
  String? bank,
  CardFranchise franchise,
  double creditLimit,
  double monthlyRate,
  int cutoffDay,
  int paymentDueDay,
});

/// Modal para registrar o editar una tarjeta. En edición no se piden franquicia
/// ni cupo (son inmutables en el backend).
Future<CardFormData?> showNewCardSheet(BuildContext context,
    {CreditCard? initial}) {
  return showModalBottomSheet<CardFormData>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _NewCardSheet(initial: initial),
  );
}

class _NewCardSheet extends StatefulWidget {
  const _NewCardSheet({this.initial});
  final CreditCard? initial;

  @override
  State<_NewCardSheet> createState() => _NewCardSheetState();
}

class _NewCardSheetState extends State<_NewCardSheet> {
  late final TextEditingController _name =
      TextEditingController(text: widget.initial?.name ?? '');
  late final TextEditingController _bank =
      TextEditingController(text: widget.initial?.bank ?? '');
  late final TextEditingController _limit = TextEditingController(
      text: widget.initial != null
          ? widget.initial!.creditLimit.toStringAsFixed(0)
          : '');
  late final TextEditingController _rate = TextEditingController(
      text: widget.initial?.monthlyRate.toStringAsFixed(2) ?? '');
  late CardFranchise _franchise = widget.initial?.franchise ?? CardFranchise.visa;
  late int _cutoffDay = widget.initial?.cutoffDay ?? 1;
  late int _paymentDueDay = widget.initial?.paymentDueDay ?? 15;
  String? _error;

  bool get _isEdit => widget.initial != null;

  @override
  void dispose() {
    _name.dispose();
    _bank.dispose();
    _limit.dispose();
    _rate.dispose();
    super.dispose();
  }

  void _submit() {
    final String name = _name.text.trim();
    final double limit = double.tryParse(
            _limit.text.replaceAll('.', '').replaceAll(',', '')) ??
        0;
    final double rate = double.tryParse(_rate.text.replaceAll(',', '.')) ?? -1;
    if (name.isEmpty || rate < 0 || (!_isEdit && limit <= 0)) {
      setState(() => _error = 'Revisá nombre, cupo y tasa mensual.');
      return;
    }
    Navigator.of(context).pop((
      name: name,
      bank: _bank.text.trim().isEmpty ? null : _bank.text.trim(),
      franchise: _isEdit ? widget.initial!.franchise : _franchise,
      creditLimit: _isEdit ? widget.initial!.creditLimit : limit,
      monthlyRate: rate,
      cutoffDay: _cutoffDay,
      paymentDueDay: _paymentDueDay,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg,
            AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(_isEdit ? 'Editar tarjeta' : 'Nueva tarjeta de crédito',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: AppSpacing.lg),
              TextField(
                  controller: _name,
                  decoration: const InputDecoration(labelText: 'Nombre')),
              const SizedBox(height: AppSpacing.md),
              TextField(
                  controller: _bank,
                  decoration:
                      const InputDecoration(labelText: 'Banco (opcional)')),
              const SizedBox(height: AppSpacing.md),
              if (!_isEdit) ...[
                InputDecorator(
                  decoration: const InputDecoration(labelText: 'Franquicia'),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<CardFranchise>(
                      isExpanded: true,
                      isDense: true,
                      value: _franchise,
                      items: [
                        for (final f in CardFranchise.values)
                          DropdownMenuItem(value: f, child: Text(f.label)),
                      ],
                      onChanged: (f) =>
                          setState(() => _franchise = f ?? _franchise),
                    ),
                  ),
                ),
                const SizedBox(height: AppSpacing.md),
                TextField(
                    controller: _limit,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Cupo total')),
                const SizedBox(height: AppSpacing.md),
              ],
              TextField(
                  controller: _rate,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration:
                      const InputDecoration(labelText: 'Tasa mensual %')),
              const SizedBox(height: AppSpacing.md),
              Row(
                children: [
                  Expanded(
                    child: _DayField(
                      label: 'Día de corte',
                      value: _cutoffDay,
                      onChanged: (v) => setState(() => _cutoffDay = v),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: _DayField(
                      label: 'Día de pago',
                      value: _paymentDueDay,
                      onChanged: (v) => setState(() => _paymentDueDay = v),
                    ),
                  ),
                ],
              ),
              if (_error != null) ...[
                const SizedBox(height: AppSpacing.md),
                Text(_error!,
                    style: TextStyle(
                        color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: AppSpacing.lg),
              ElevatedButton(
                  onPressed: _submit, child: const Text('Guardar tarjeta')),
            ],
          ),
        ),
      ),
    );
  }
}

class _DayField extends StatelessWidget {
  const _DayField(
      {required this.label, required this.value, required this.onChanged});

  final String label;
  final int value;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => InputDecorator(
        decoration: InputDecoration(labelText: label),
        child: DropdownButtonHideUnderline(
          child: DropdownButton<int>(
            isExpanded: true,
            isDense: true,
            value: value,
            items: [
              for (int d = 1; d <= 31; d++)
                DropdownMenuItem(value: d, child: Text('$d')),
            ],
            onChanged: (v) => onChanged(v ?? value),
          ),
        ),
      );
}
