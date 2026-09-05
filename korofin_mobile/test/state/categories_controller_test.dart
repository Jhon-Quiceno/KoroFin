import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/category_repository.dart';
import 'package:korofin_mobile/models/category.dart';
import 'package:korofin_mobile/state/categories/categories_controller.dart';

class _FakeCategoryRepository implements CategoryRepository {
  final List<Category> store = <Category>[
    const Category(id: 1, name: 'Comida', kind: CategoryKind.expense),
    const Category(id: 2, name: 'Salario', kind: CategoryKind.income),
  ];
  int _nextId = 3;

  @override
  Future<List<Category>> list({CategoryKind? kind}) async {
    return kind == null
        ? List<Category>.of(store)
        : store.where((c) => c.kind == kind).toList();
  }

  @override
  Future<Category> create({
    required String name,
    required CategoryKind kind,
  }) async {
    final created = Category(id: _nextId++, name: name, kind: kind);
    store.add(created);
    return created;
  }

  @override
  Future<Category> update(
    int id, {
    required String name,
    required CategoryKind kind,
  }) async {
    final updated = Category(id: id, name: name, kind: kind);
    final i = store.indexWhere((c) => c.id == id);
    store[i] = updated;
    return updated;
  }

  @override
  Future<void> delete(int id) async {
    store.removeWhere((c) => c.id == id);
  }
}

ProviderContainer _container(_FakeCategoryRepository repo) {
  final container = ProviderContainer(
    overrides: <Override>[
      categoryRepositoryProvider.overrideWithValue(repo),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

void main() {
  test('build carga la lista del repositorio', () async {
    final container = _container(_FakeCategoryRepository());

    final list = await container.read(categoriesProvider.future);

    expect(list.map((c) => c.name), containsAll(<String>['Comida', 'Salario']));
  });

  test('create agrega la categoría y la deja ordenada por nombre', () async {
    final container = _container(_FakeCategoryRepository());
    await container.read(categoriesProvider.future);

    await container
        .read(categoriesProvider.notifier)
        .create(name: 'Auto', kind: CategoryKind.expense);

    final list = container.read(categoriesProvider).requireValue;
    expect(list.map((c) => c.name), <String>['Auto', 'Comida', 'Salario']);
  });

  test('update reemplaza la categoría en la lista', () async {
    final container = _container(_FakeCategoryRepository());
    await container.read(categoriesProvider.future);

    await container
        .read(categoriesProvider.notifier)
        .edit(1, name: 'Comida y bebida', kind: CategoryKind.expense);

    final list = container.read(categoriesProvider).requireValue;
    expect(list.firstWhere((c) => c.id == 1).name, 'Comida y bebida');
  });

  test('delete la saca de la lista', () async {
    final container = _container(_FakeCategoryRepository());
    await container.read(categoriesProvider.future);

    await container.read(categoriesProvider.notifier).delete(2);

    final list = container.read(categoriesProvider).requireValue;
    expect(list.any((c) => c.id == 2), isFalse);
  });
}
