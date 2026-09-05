import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/category_visuals.dart';
import '../../models/category.dart';
import '../../state/categories/categories_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Bottom sheet para elegir una categoría dentro de los formularios de
/// gasto/ingreso. Lee las categorías reales del backend y las filtra por
/// [kind]. Termina con "Gestionar categorías", el único acceso a la pantalla
/// de Categorías.
Future<Category?> showCategoryPicker(
  BuildContext context, {
  Category? selected,
  CategoryKind? kind,
}) {
  return showModalBottomSheet<Category>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _CategoryPickerSheet(selected: selected, kind: kind),
  );
}

class _CategoryPickerSheet extends ConsumerWidget {
  const _CategoryPickerSheet({this.selected, this.kind});

  final Category? selected;
  final CategoryKind? kind;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final koro = context.koroColors;
    final AsyncValue<List<Category>> categories = ref.watch(categoriesProvider);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Elegí una categoría',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            categories.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(AppSpacing.xl),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, _) => const Padding(
                padding: EdgeInsets.all(AppSpacing.lg),
                child: Text('No se pudieron cargar las categorías.'),
              ),
              data: (all) {
                final List<Category> list = kind == null
                    ? all
                    : all.where((c) => c.kind == kind).toList(growable: false);
                if (list.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.symmetric(vertical: AppSpacing.lg),
                    child: Text(
                        'Todavía no tenés categorías de este tipo. Creá una desde "Gestionar categorías".'),
                  );
                }
                return GridView.count(
                  crossAxisCount: 4,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  mainAxisSpacing: AppSpacing.md,
                  crossAxisSpacing: AppSpacing.md,
                  children: [
                    for (final category in list)
                      _PickerCell(
                        category: category,
                        selected: selected?.id == category.id,
                        onTap: () => Navigator.of(context).pop(category),
                      ),
                  ],
                );
              },
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

class _PickerCell extends StatelessWidget {
  const _PickerCell({
    required this.category,
    required this.selected,
    required this.onTap,
  });

  final Category category;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final Color color = CategoryVisuals.colorFor(category);
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.md),
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.14),
              shape: BoxShape.circle,
              border: selected ? Border.all(color: color, width: 2) : null,
            ),
            child: Icon(CategoryVisuals.iconFor(category), color: color, size: 20),
          ),
          const SizedBox(height: 6),
          Text(
            category.name,
            style: const TextStyle(fontSize: 11),
            textAlign: TextAlign.center,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
