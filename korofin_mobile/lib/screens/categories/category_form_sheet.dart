import 'package:flutter/material.dart';

import '../../data/category_visuals.dart';
import '../../models/category.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';

/// Datos que el formulario devuelve al confirmar. El ícono y el color no se
/// piden: los deriva la app del nombre (el backend solo guarda nombre y tipo).
typedef CategoryFormData = ({String name, CategoryKind kind});

/// Formulario (bottom sheet) para crear o editar una categoría.
Future<CategoryFormData?> showCategoryForm(
  BuildContext context, {
  Category? initial,
}) {
  return showModalBottomSheet<CategoryFormData>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _CategoryFormSheet(initial: initial),
  );
}

class _CategoryFormSheet extends StatefulWidget {
  const _CategoryFormSheet({this.initial});

  final Category? initial;

  @override
  State<_CategoryFormSheet> createState() => _CategoryFormSheetState();
}

class _CategoryFormSheetState extends State<_CategoryFormSheet> {
  late final TextEditingController _name =
      TextEditingController(text: widget.initial?.name ?? '');
  late CategoryKind _kind = widget.initial?.kind ?? CategoryKind.expense;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Category get _preview => Category(
        id: widget.initial?.id ?? 0,
        name: _name.text.trim().isEmpty ? 'Categoría' : _name.text.trim(),
        kind: _kind,
      );

  void _submit() {
    final String name = _name.text.trim();
    if (name.isEmpty) return;
    Navigator.of(context).pop((name: name, kind: _kind));
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final Color color = CategoryVisuals.colorFor(_preview);
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.lg,
          0,
          AppSpacing.lg,
          AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.initial == null ? 'Nueva categoría' : 'Editar categoría',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: AppSpacing.lg),
            Center(
              child: Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.14),
                  shape: BoxShape.circle,
                ),
                child: Icon(CategoryVisuals.iconFor(_preview),
                    color: color, size: 28),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            TextField(
              controller: _name,
              autofocus: widget.initial == null,
              textCapitalization: TextCapitalization.sentences,
              decoration:
                  const InputDecoration(labelText: 'Nombre de la categoría'),
              onChanged: (_) => setState(() {}),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Tipo',
                style: AppTextStyles.bodyMediumMedium(koro.foreground)),
            const SizedBox(height: AppSpacing.sm),
            SegmentedButton<CategoryKind>(
              segments: const [
                ButtonSegment(
                    value: CategoryKind.expense, label: Text('Gasto')),
                ButtonSegment(
                    value: CategoryKind.income, label: Text('Ingreso')),
              ],
              selected: {_kind},
              onSelectionChanged: (s) => setState(() => _kind = s.first),
            ),
            const SizedBox(height: AppSpacing.xl),
            ElevatedButton(
              onPressed: _name.text.trim().isEmpty ? null : _submit,
              child: const Text('Guardar categoría'),
            ),
          ],
        ),
      ),
    );
  }
}
