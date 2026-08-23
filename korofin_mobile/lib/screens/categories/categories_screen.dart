import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/category.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import 'category_form_sheet.dart';

/// Screen 8 — Categorías: icon+color grid with a CRUD sheet. Only reachable
/// as a sub-flow from the expense category picker, not from the bottom nav.
class CategoriesScreen extends StatefulWidget {
  const CategoriesScreen({super.key});

  @override
  State<CategoriesScreen> createState() => _CategoriesScreenState();
}

class _CategoriesScreenState extends State<CategoriesScreen> {
  late List<AppCategory> _categories = List.of(MockData.categories);

  Future<void> _create() async {
    final result = await showCategoryForm(context);
    if (result != null) setState(() => _categories.add(result));
  }

  Future<void> _edit(AppCategory category) async {
    final result = await showCategoryForm(context, initial: category);
    if (result != null) {
      setState(() => _categories = [for (final c in _categories) if (c.id == category.id) result else c]);
    }
  }

  void _delete(AppCategory category) {
    setState(() => _categories.removeWhere((c) => c.id == category.id));
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      appBar: AppBar(title: const Text('Categorías')),
      body: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: GridView.builder(
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 3,
            mainAxisSpacing: AppSpacing.md,
            crossAxisSpacing: AppSpacing.md,
            childAspectRatio: 0.85,
          ),
          itemCount: _categories.length,
          itemBuilder: (context, index) {
            final category = _categories[index];
            return InkWell(
              borderRadius: BorderRadius.circular(AppRadii.lg),
              onTap: () => _edit(category),
              onLongPress: () => _delete(category),
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
                      decoration: BoxDecoration(color: category.color.withValues(alpha: 0.14), shape: BoxShape.circle),
                      child: Icon(category.icon, color: category.color, size: 20),
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    Text(category.name, style: AppTextStyles.bodyMediumMedium(koro.foreground), textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton(onPressed: _create, child: const Icon(Icons.add)),
    );
  }
}
