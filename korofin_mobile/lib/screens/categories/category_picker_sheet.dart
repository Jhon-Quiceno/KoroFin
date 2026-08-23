import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/mock_data.dart';
import '../../models/category.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Bottom sheet used inside the Gastos/Ingresos/Quick-Add forms to choose a
/// category. Ends with "Gestionar categorías", the only entry point into
/// the standalone Categorías screen per the KoroFin navigation spec.
Future<AppCategory?> showCategoryPicker(BuildContext context, {AppCategory? selected}) {
  return showModalBottomSheet<AppCategory>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _CategoryPickerSheet(selected: selected),
  );
}

class _CategoryPickerSheet extends StatelessWidget {
  const _CategoryPickerSheet({this.selected});

  final AppCategory? selected;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Elegí una categoría', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            GridView.count(
              crossAxisCount: 4,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: AppSpacing.md,
              crossAxisSpacing: AppSpacing.md,
              children: [
                for (final category in MockData.categories)
                  InkWell(
                    borderRadius: BorderRadius.circular(AppRadii.md),
                    onTap: () => Navigator.of(context).pop(category),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 48,
                          height: 48,
                          decoration: BoxDecoration(
                            color: category.color.withValues(alpha: 0.14),
                            shape: BoxShape.circle,
                            border: selected?.id == category.id ? Border.all(color: category.color, width: 2) : null,
                          ),
                          child: Icon(category.icon, color: category.color, size: 20),
                        ),
                        const SizedBox(height: 6),
                        Text(category.name, style: const TextStyle(fontSize: 11), textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis),
                      ],
                    ),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            Center(
              child: TextButton.icon(
                onPressed: () {
                  Navigator.of(context).pop();
                  context.push('/categories');
                },
                icon: Icon(Icons.tune, size: 16, color: koro.accent),
                label: const Text('Gestionar categorías'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
