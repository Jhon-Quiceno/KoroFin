import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../data/category_visuals.dart';
import '../../models/category.dart';
import '../../state/categories/categories_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../../widgets/common/empty_state.dart';
import 'category_form_sheet.dart';

/// Pantalla 8 — Categorías: grilla con CRUD contra `/api/categories`. Se llega
/// desde "Gestionar categorías" en el picker de categorías.
class CategoriesScreen extends ConsumerStatefulWidget {
  const CategoriesScreen({super.key});

  @override
  ConsumerState<CategoriesScreen> createState() => _CategoriesScreenState();
}

class _CategoriesScreenState extends ConsumerState<CategoriesScreen> {
  CategoryKind? _filter;

  Future<void> _create() async {
    final CategoryFormData? data = await showCategoryForm(context);
    if (data == null) return;
    await _run(() => ref
        .read(categoriesProvider.notifier)
        .create(name: data.name, kind: data.kind));
  }

  Future<void> _edit(Category category) async {
    final CategoryFormData? data =
        await showCategoryForm(context, initial: category);
    if (data == null) return;
    await _run(() => ref
        .read(categoriesProvider.notifier)
        .edit(category.id, name: data.name, kind: data.kind));
  }

  Future<void> _confirmDelete(Category category) async {
    final bool ok = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text('¿Borrar "${category.name}"?'),
            content: const Text(
                'Los movimientos que la usan quedan sin categoría.'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.of(context).pop(false),
                  child: const Text('Cancelar')),
              FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  child: const Text('Borrar')),
            ],
          ),
        ) ??
        false;
    if (ok) {
      await _run(() => ref.read(categoriesProvider.notifier).delete(category.id));
    }
  }

  /// Ejecuta una mutación y muestra el error del backend si falla (p. ej. 409
  /// por nombre duplicado).
  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<List<Category>> categories = ref.watch(categoriesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Categorías')),
      floatingActionButton: FloatingActionButton(
        onPressed: _create,
        child: const Icon(Icons.add),
      ),
      body: categories.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorView(
          message: error is ApiException
              ? error.message
              : 'No se pudieron cargar las categorías.',
          onRetry: () => ref.read(categoriesProvider.notifier).refresh(),
        ),
        data: (all) {
          final List<Category> list = _filter == null
              ? all
              : all.where((c) => c.kind == _filter).toList(growable: false);
          return RefreshIndicator(
            onRefresh: () => ref.read(categoriesProvider.notifier).refresh(),
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                      AppSpacing.lg, AppSpacing.md, AppSpacing.lg, 0),
                  child: SegmentedButton<CategoryKind?>(
                    segments: const [
                      ButtonSegment(value: null, label: Text('Todas')),
                      ButtonSegment(
                          value: CategoryKind.expense, label: Text('Gastos')),
                      ButtonSegment(
                          value: CategoryKind.income, label: Text('Ingresos')),
                    ],
                    selected: {_filter},
                    onSelectionChanged: (s) => setState(() => _filter = s.first),
                  ),
                ),
                Expanded(
                  child: list.isEmpty
                      ? ListView(
                          children: const [
                            SizedBox(height: AppSpacing.xxxl),
                            EmptyState(
                              icon: Icons.category_outlined,
                              title: 'Sin categorías',
                              description:
                                  'Creá una con el botón + para empezar a clasificar tus movimientos.',
                            ),
                          ],
                        )
                      : GridView.builder(
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          gridDelegate:
                              const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 3,
                            mainAxisSpacing: AppSpacing.md,
                            crossAxisSpacing: AppSpacing.md,
                            childAspectRatio: 0.85,
                          ),
                          itemCount: list.length,
                          itemBuilder: (context, index) =>
                              _CategoryTile(
                            category: list[index],
                            onTap: () => _edit(list[index]),
                            onLongPress: () => _confirmDelete(list[index]),
                          ),
                        ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _CategoryTile extends StatelessWidget {
  const _CategoryTile({
    required this.category,
    required this.onTap,
    required this.onLongPress,
  });

  final Category category;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final Color color = CategoryVisuals.colorFor(category);
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.lg),
      onTap: onTap,
      onLongPress: onLongPress,
      child: Container(
        decoration: BoxDecoration(
          color: koro.surface,
          border: Border.all(color: koro.border),
          borderRadius: BorderRadius.circular(AppRadii.lg),
        ),
        padding: const EdgeInsets.all(AppSpacing.sm),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.14),
                shape: BoxShape.circle,
              ),
              child: Icon(CategoryVisuals.iconFor(category),
                  color: color, size: 20),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              category.name,
              style: AppTextStyles.bodyMediumMedium(koro.foreground),
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.lg),
            FilledButton.tonal(
                onPressed: onRetry, child: const Text('Reintentar')),
          ],
        ),
      ),
    );
  }
}
