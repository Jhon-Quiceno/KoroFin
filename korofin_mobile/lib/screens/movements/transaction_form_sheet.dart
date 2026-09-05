import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/category_visuals.dart';
import '../../data/formatters.dart';
import '../../models/category.dart';
import '../../models/movement.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../categories/category_picker_sheet.dart';

/// Resultado del formulario: el tipo final (puede haber cambiado con el toggle
/// de Quick-Add) y el borrador a persistir.
typedef MovementFormResult = ({MovementType type, MovementDraft draft});

/// Bottom sheet de alta/edición compartido por Gastos e Ingresos, y reusado por
/// Quick-Add con el toggle Ingreso/Gasto activo.
Future<MovementFormResult?> showTransactionForm(
  BuildContext context, {
  required MovementType type,
  Movement? initial,
  bool allowTypeToggle = false,
}) {
  return showModalBottomSheet<MovementFormResult>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _TransactionFormSheet(
      type: type,
      initial: initial,
      allowTypeToggle: allowTypeToggle,
    ),
  );
}

class _TransactionFormSheet extends ConsumerStatefulWidget {
  const _TransactionFormSheet({
    required this.type,
    required this.allowTypeToggle,
    this.initial,
  });

  final MovementType type;
  final Movement? initial;
  final bool allowTypeToggle;

  @override
  ConsumerState<_TransactionFormSheet> createState() =>
      _TransactionFormSheetState();
}

class _TransactionFormSheetState extends ConsumerState<_TransactionFormSheet> {
  late MovementType _type = widget.initial?.type ?? widget.type;
  late final TextEditingController _amount = TextEditingController(
    text: widget.initial != null
        ? widget.initial!.amount.toStringAsFixed(0)
        : '',
  );
  late final TextEditingController _description =
      TextEditingController(text: widget.initial?.description ?? '');

  late DateTime _date = widget.initial?.date ?? DateTime.now();
  late PaymentMethod _paymentMethod =
      widget.initial?.paymentMethod ?? PaymentMethod.cash;

  int? _categoryId;
  String? _categoryName;
  String? _error;

  bool get _isExpense => _type == MovementType.expense;

  @override
  void initState() {
    super.initState();
    _categoryId = widget.initial?.categoryId;
    _categoryName = widget.initial?.categoryName;
  }

  @override
  void dispose() {
    _amount.dispose();
    _description.dispose();
    super.dispose();
  }

  double? get _parsedAmount {
    final String raw =
        _amount.text.replaceAll('.', '').replaceAll(',', '').trim();
    final double? value = double.tryParse(raw);
    return (value == null || value <= 0) ? null : value;
  }

  Future<void> _pickCategory() async {
    final Category? picked = await showCategoryPicker(
      context,
      kind: _isExpense ? CategoryKind.expense : CategoryKind.income,
    );
    if (picked != null) {
      setState(() {
        _categoryId = picked.id;
        _categoryName = picked.name;
      });
    }
  }

  Future<void> _pickDate() async {
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime.now(), // el backend rechaza fechas futuras
    );
    if (picked != null) setState(() => _date = picked);
  }

  void _submit() {
    final double? amount = _parsedAmount;
    if (amount == null) {
      setState(() => _error = 'Ingresá un monto válido mayor a cero.');
      return;
    }
    final MovementDraft draft = MovementDraft(
      amount: amount,
      date: _date,
      description: _description.text.trim().isEmpty
          ? null
          : _description.text.trim(),
      categoryId: _categoryId,
      paymentMethod: _isExpense ? _paymentMethod : null,
    );
    Navigator.of(context).pop((type: _type, draft: draft));
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final Color accent = _isExpense ? koro.accent : koro.success;

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.lg,
          0,
          AppSpacing.lg,
          AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.initial != null
                    ? (_isExpense ? 'Editar gasto' : 'Editar ingreso')
                    : (_isExpense ? 'Nuevo gasto' : 'Nuevo ingreso'),
                style: Theme.of(context).textTheme.titleLarge,
              ),
              if (widget.allowTypeToggle) ...[
                const SizedBox(height: AppSpacing.md),
                SegmentedButton<MovementType>(
                  segments: const [
                    ButtonSegment(
                        value: MovementType.income, label: Text('Ingreso')),
                    ButtonSegment(
                        value: MovementType.expense, label: Text('Gasto')),
                  ],
                  selected: {_type},
                  onSelectionChanged: (s) => setState(() {
                    _type = s.first;
                    _categoryId = null;
                    _categoryName = null;
                  }),
                ),
              ],
              const SizedBox(height: AppSpacing.lg),
              Center(
                child: TextField(
                  controller: _amount,
                  keyboardType: TextInputType.number,
                  textAlign: TextAlign.center,
                  style: AppTextStyles.numericLarge(accent),
                  decoration: const InputDecoration(
                    hintText: r'$ 0',
                    border: InputBorder.none,
                    filled: false,
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              TextField(
                controller: _description,
                textCapitalization: TextCapitalization.sentences,
                decoration:
                    const InputDecoration(labelText: 'Descripción (opcional)'),
              ),
              const SizedBox(height: AppSpacing.md),
              InkWell(
                borderRadius: BorderRadius.circular(AppRadii.md),
                onTap: _pickCategory,
                child: InputDecorator(
                  decoration: const InputDecoration(labelText: 'Categoría'),
                  child: Row(
                    children: [
                      Icon(CategoryVisuals.iconForName(_categoryName),
                          size: 18,
                          color: CategoryVisuals.colorForName(_categoryName,
                              income: !_isExpense)),
                      const SizedBox(width: AppSpacing.sm),
                      Text(_categoryName ?? 'Sin categoría'),
                      const Spacer(),
                      const Icon(Icons.chevron_right_rounded, size: 18),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              InkWell(
                borderRadius: BorderRadius.circular(AppRadii.md),
                onTap: _pickDate,
                child: InputDecorator(
                  decoration: const InputDecoration(labelText: 'Fecha'),
                  child: Row(
                    children: [
                      const Icon(Icons.calendar_today_outlined, size: 16),
                      const SizedBox(width: AppSpacing.sm),
                      Text(AppFormatters.longDate(_date)),
                    ],
                  ),
                ),
              ),
              if (_isExpense) ...[
                const SizedBox(height: AppSpacing.md),
                InputDecorator(
                  decoration:
                      const InputDecoration(labelText: 'Método de pago'),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<PaymentMethod>(
                      isDense: true,
                      isExpanded: true,
                      value: _paymentMethod,
                      items: [
                        for (final PaymentMethod m in PaymentMethod.values)
                          DropdownMenuItem(value: m, child: Text(m.label)),
                      ],
                      onChanged: (m) =>
                          setState(() => _paymentMethod = m ?? _paymentMethod),
                    ),
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
                style: ElevatedButton.styleFrom(backgroundColor: accent),
                onPressed: _submit,
                child: const Text('Guardar'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
