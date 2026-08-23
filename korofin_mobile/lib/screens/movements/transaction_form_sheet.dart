import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/category.dart';
import '../../models/transaction.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../categories/category_picker_sheet.dart';

/// Shared alta/edición bottom sheet for Gastos (screen 4) and Ingresos
/// (screen 7) — same fields, only the [type] and accent differ. Also reused
/// by Quick Add (screen 17) for the fast-entry flow from the FAB.
Future<AppTransaction?> showTransactionForm(
  BuildContext context, {
  required TransactionType type,
  AppTransaction? initial,
  bool allowTypeToggle = false,
}) {
  return showModalBottomSheet<AppTransaction>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _TransactionFormSheet(type: type, initial: initial, allowTypeToggle: allowTypeToggle),
  );
}

class _TransactionFormSheet extends StatefulWidget {
  const _TransactionFormSheet({required this.type, this.initial, required this.allowTypeToggle});

  final TransactionType type;
  final AppTransaction? initial;
  final bool allowTypeToggle;

  @override
  State<_TransactionFormSheet> createState() => _TransactionFormSheetState();
}

class _TransactionFormSheetState extends State<_TransactionFormSheet> {
  late TransactionType _type = widget.initial?.type ?? widget.type;
  late final TextEditingController _amountController =
      TextEditingController(text: widget.initial?.amount.toStringAsFixed(0) ?? '');
  late final TextEditingController _titleController = TextEditingController(text: widget.initial?.title ?? '');
  late final TextEditingController _noteController = TextEditingController();
  late AppCategory _category = widget.initial?.category ?? MockData.categories.first;
  late DateTime _date = widget.initial?.date ?? DateTime.now();
  bool _aiSuggested = false;

  bool get _isExpense => _type == TransactionType.expense;

  Future<void> _pickCategory() async {
    final result = await showCategoryPicker(context, selected: _category);
    if (result != null) {
      setState(() {
        _category = result;
        _aiSuggested = false;
      });
    }
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null) setState(() => _date = picked);
  }

  void _save() {
    final double? amount = double.tryParse(_amountController.text.replaceAll('.', '').replaceAll(',', ''));
    if (amount == null || amount <= 0 || _titleController.text.trim().isEmpty) return;
    Navigator.of(context).pop(
      AppTransaction(
        id: widget.initial?.id ?? DateTime.now().millisecondsSinceEpoch.toString(),
        title: _titleController.text.trim(),
        category: _category,
        amount: amount,
        date: _date,
        type: _type,
        categorizedByAi: _aiSuggested,
      ),
    );
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
                SegmentedButton<TransactionType>(
                  segments: const [
                    ButtonSegment(value: TransactionType.income, label: Text('Ingreso')),
                    ButtonSegment(value: TransactionType.expense, label: Text('Gasto')),
                  ],
                  selected: {_type},
                  onSelectionChanged: (s) => setState(() => _type = s.first),
                ),
              ],
              const SizedBox(height: AppSpacing.lg),
              Center(
                child: TextField(
                  controller: _amountController,
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
              TextField(controller: _titleController, decoration: const InputDecoration(labelText: 'Descripción')),
              const SizedBox(height: AppSpacing.md),
              InkWell(
                borderRadius: BorderRadius.circular(AppRadii.md),
                onTap: _pickCategory,
                child: InputDecorator(
                  decoration: const InputDecoration(labelText: 'Categoría'),
                  child: Row(
                    children: [
                      Icon(_category.icon, size: 18, color: _category.color),
                      const SizedBox(width: AppSpacing.sm),
                      Text(_category.name),
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
                      Text('${_date.day}/${_date.month}/${_date.year}'),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              TextField(
                controller: _noteController,
                maxLines: 2,
                decoration: const InputDecoration(labelText: 'Nota (opcional)'),
              ),
              if (_isExpense) ...[
                const SizedBox(height: AppSpacing.md),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  value: _aiSuggested,
                  onChanged: (v) => setState(() => _aiSuggested = v),
                  activeThumbColor: AppColors.info,
                  title: const Text('Categorizado por IA', style: TextStyle(fontSize: 13)),
                  subtitle: const Text('Marca esta categoría como sugerida automáticamente', style: TextStyle(fontSize: 11)),
                ),
              ],
              const SizedBox(height: AppSpacing.lg),
              ElevatedButton(
                style: ElevatedButton.styleFrom(backgroundColor: accent),
                onPressed: _save,
                child: const Text('Guardar'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
