import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/debt.dart';
import '../../theme/app_spacing.dart';

/// Datos que el formulario devuelve. El saldo restante lo inicializa el backend
/// con `totalAmount`; no se pide.
typedef DebtFormData = ({
  String name,
  double totalAmount,
  double? interestRate,
  DateTime? dueDate,
});

/// Modal para registrar o editar una deuda. En edición no se muestra el monto
/// total (es inmutable en el backend).
Future<DebtFormData?> showNewDebtSheet(BuildContext context, {Debt? initial}) {
  return showModalBottomSheet<DebtFormData>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _NewDebtSheet(initial: initial),
  );
}

class _NewDebtSheet extends StatefulWidget {
  const _NewDebtSheet({this.initial});

  final Debt? initial;

  @override
  State<_NewDebtSheet> createState() => _NewDebtSheetState();
}

class _NewDebtSheetState extends State<_NewDebtSheet> {
  late final TextEditingController _name =
      TextEditingController(text: widget.initial?.name ?? '');
  late final TextEditingController _total = TextEditingController(
    text: widget.initial != null
        ? widget.initial!.totalAmount.toStringAsFixed(0)
        : '',
  );
  late final TextEditingController _interest = TextEditingController(
    text: widget.initial?.interestRate?.toStringAsFixed(1) ?? '',
  );
  late DateTime? _dueDate = widget.initial?.dueDate;
  String? _error;

  bool get _isEdit => widget.initial != null;

  @override
  void dispose() {
    _name.dispose();
    _total.dispose();
    _interest.dispose();
    super.dispose();
  }

  void _submit() {
    final String name = _name.text.trim();
    final double total =
        double.tryParse(_total.text.replaceAll('.', '').replaceAll(',', '')) ?? 0;
    if (name.isEmpty) {
      setState(() => _error = 'Ingresá un nombre.');
      return;
    }
    if (!_isEdit && total <= 0) {
      setState(() => _error = 'Ingresá un monto total mayor a cero.');
      return;
    }
    Navigator.of(context).pop((
      name: name,
      totalAmount: _isEdit ? widget.initial!.totalAmount : total,
      interestRate: double.tryParse(_interest.text.replaceAll(',', '.')),
      dueDate: _dueDate,
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
            Text(_isEdit ? 'Editar deuda' : 'Nueva deuda',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(
              controller: _name,
              decoration:
                  const InputDecoration(labelText: 'Nombre de la deuda'),
            ),
            const SizedBox(height: AppSpacing.md),
            if (!_isEdit) ...[
              TextField(
                controller: _total,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Monto total'),
              ),
              const SizedBox(height: AppSpacing.md),
            ],
            TextField(
              controller: _interest,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                  labelText: 'Tasa de interés % (opcional)'),
            ),
            const SizedBox(height: AppSpacing.md),
            InkWell(
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _dueDate ??
                      DateTime.now().add(const Duration(days: 180)),
                  firstDate: DateTime(2020),
                  lastDate: DateTime.now().add(const Duration(days: 365 * 15)),
                );
                if (picked != null) setState(() => _dueDate = picked);
              },
              child: InputDecorator(
                decoration: const InputDecoration(
                    labelText: 'Fecha de vencimiento (opcional)'),
                child: Text(_dueDate == null
                    ? 'Sin fecha'
                    : AppFormatters.longDate(_dueDate!)),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(_error!,
                  style:
                      TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: AppSpacing.lg),
            ElevatedButton(
              onPressed: _submit,
              child: const Text('Guardar deuda'),
            ),
          ],
        ),
      ),
    );
  }
}
