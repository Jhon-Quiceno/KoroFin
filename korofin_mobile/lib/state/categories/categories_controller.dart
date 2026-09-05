import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/category_repository.dart';
import '../../models/category.dart';

final categoryRepositoryProvider = Provider<CategoryRepository>(
  (ref) => CategoryRepository(ref.read(apiClientProvider)),
);

/// Lista de categorías del usuario. Las mutaciones actualizan la lista en
/// memoria en vez de recargar todo, para que la UI responda al instante.
final categoriesProvider =
    AsyncNotifierProvider<CategoriesController, List<Category>>(
  CategoriesController.new,
);

class CategoriesController extends AsyncNotifier<List<Category>> {
  CategoryRepository get _repo => ref.read(categoryRepositoryProvider);

  @override
  Future<List<Category>> build() => _repo.list();

  Future<void> refresh() async {
    state = const AsyncValue<List<Category>>.loading();
    state = await AsyncValue.guard(_repo.list);
  }

  Future<Category> create({
    required String name,
    required CategoryKind kind,
  }) async {
    final Category created = await _repo.create(name: name, kind: kind);
    final List<Category> current = state.valueOrNull ?? <Category>[];
    state = AsyncValue<List<Category>>.data(
      <Category>[...current, created]..sort(_byName),
    );
    return created;
  }

  /// Se llama `edit` y no `update` porque `AsyncNotifier` ya define `update`.
  Future<void> edit(
    int id, {
    required String name,
    required CategoryKind kind,
  }) async {
    final Category updated = await _repo.update(id, name: name, kind: kind);
    final List<Category> current = state.valueOrNull ?? <Category>[];
    state = AsyncValue<List<Category>>.data(
      <Category>[
        for (final Category c in current) if (c.id == id) updated else c,
      ]..sort(_byName),
    );
  }

  Future<void> delete(int id) async {
    await _repo.delete(id);
    final List<Category> current = state.valueOrNull ?? <Category>[];
    state = AsyncValue<List<Category>>.data(
      current.where((Category c) => c.id != id).toList(growable: false),
    );
  }

  static int _byName(Category a, Category b) =>
      a.name.toLowerCase().compareTo(b.name.toLowerCase());
}
