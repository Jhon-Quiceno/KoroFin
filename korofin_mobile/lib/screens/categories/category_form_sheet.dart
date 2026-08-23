import 'package:flutter/material.dart';

import '../../models/category.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';

const List<IconData> _iconChoices = [
  Icons.restaurant_outlined,
  Icons.directions_car_outlined,
  Icons.home_outlined,
  Icons.movie_outlined,
  Icons.medical_services_outlined,
  Icons.school_outlined,
  Icons.shopping_bag_outlined,
  Icons.pets_outlined,
  Icons.flight_outlined,
  Icons.sports_soccer_outlined,
  Icons.card_giftcard_outlined,
  Icons.more_horiz,
];

/// CRUD form for a category: name, color swatch picker and icon picker.
Future<AppCategory?> showCategoryForm(BuildContext context, {AppCategory? initial}) {
  return showModalBottomSheet<AppCategory>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _CategoryFormSheet(initial: initial),
  );
}

class _CategoryFormSheet extends StatefulWidget {
  const _CategoryFormSheet({this.initial});

  final AppCategory? initial;

  @override
  State<_CategoryFormSheet> createState() => _CategoryFormSheetState();
}

class _CategoryFormSheetState extends State<_CategoryFormSheet> {
  late final TextEditingController _nameController =
      TextEditingController(text: widget.initial?.name ?? '');
  late Color _color = widget.initial?.color ?? AppColors.categoryPalette.first;
  late IconData _icon = widget.initial?.icon ?? _iconChoices.first;

  @override
  Widget build(BuildContext context) {
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
            Text(widget.initial == null ? 'Nueva categoría' : 'Editar categoría', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            Center(
              child: Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(color: _color.withValues(alpha: 0.14), shape: BoxShape.circle),
                child: Icon(_icon, color: _color, size: 28),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            TextField(controller: _nameController, decoration: const InputDecoration(labelText: 'Nombre de la categoría')),
            const SizedBox(height: AppSpacing.lg),
            Text('Color', style: AppTextStyles.bodyMediumMedium(context.koroColors.foreground)),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: [
                for (final color in AppColors.categoryPalette)
                  GestureDetector(
                    onTap: () => setState(() => _color = color),
                    child: Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: color,
                        shape: BoxShape.circle,
                        border: _color == color ? Border.all(color: Colors.white, width: 2) : null,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Ícono', style: AppTextStyles.bodyMediumMedium(context.koroColors.foreground)),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: [
                for (final icon in _iconChoices)
                  GestureDetector(
                    onTap: () => setState(() => _icon = icon),
                    child: Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        color: _icon == icon ? _color.withValues(alpha: 0.2) : context.koroColors.surfaceElevated,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(icon, size: 16, color: _icon == icon ? _color : context.koroColors.mutedForeground),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.xl),
            ElevatedButton(
              onPressed: () {
                if (_nameController.text.trim().isEmpty) return;
                Navigator.of(context).pop(
                  AppCategory(
                    id: widget.initial?.id ?? DateTime.now().millisecondsSinceEpoch.toString(),
                    name: _nameController.text.trim(),
                    icon: _icon,
                    color: _color,
                  ),
                );
              },
              child: const Text('Guardar categoría'),
            ),
          ],
        ),
      ),
    );
  }
}
