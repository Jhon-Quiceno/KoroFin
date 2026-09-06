import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/expense_repository.dart';
import 'package:korofin_mobile/data/repositories/income_repository.dart';
import 'package:korofin_mobile/models/movement.dart';
import 'package:korofin_mobile/models/page_response.dart';
import 'package:korofin_mobile/state/movements/movements_controller.dart';

Movement _mv(int id) => Movement(
      id: id,
      type: MovementType.expense,
      amount: id * 100,
      date: DateTime(2026, 9, 1),
    );

/// Repo falso con dos páginas de 2 elementos.
class _FakeExpenseRepository implements ExpenseRepository {
  int createCalls = 0;

  @override
  Future<PageResponse<Movement>> list({
    int page = 0,
    int size = 20,
    int? categoryId,
    DateTime? from,
    DateTime? to,
    PaymentMethod? paymentMethod,
  }) async {
    final items = page == 0
        ? <Movement>[_mv(1), _mv(2)]
        : <Movement>[_mv(3), _mv(4)];
    return PageResponse<Movement>(
      content: items,
      page: page,
      totalElements: 4,
      totalPages: 2,
      isLast: page >= 1,
    );
  }

  @override
  Future<Movement> create(MovementDraft draft) async {
    createCalls++;
    return _mv(99);
  }

  @override
  Future<Movement> update(int id, MovementDraft draft) async =>
      Movement(id: id, type: MovementType.expense, amount: 1, date: DateTime(2026, 1, 1));

  @override
  Future<void> delete(int id) async {}
}

class _UnusedIncomeRepository implements IncomeRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('no debería usarse en este test');
}

ProviderContainer _container(ExpenseRepository repo) {
  final container = ProviderContainer(overrides: <Override>[
    expenseRepositoryProvider.overrideWithValue(repo),
    incomeRepositoryProvider.overrideWithValue(_UnusedIncomeRepository()),
  ]);
  addTearDown(container.dispose);
  return container;
}

void main() {
  test('build carga la primera página', () async {
    final container = _container(_FakeExpenseRepository());

    final list =
        await container.read(movementsProvider(MovementType.expense).future);

    expect(list.map((m) => m.id), <int>[1, 2]);
  });

  test('loadMore agrega la página siguiente y agota hasMore', () async {
    final container = _container(_FakeExpenseRepository());
    await container.read(movementsProvider(MovementType.expense).future);
    final notifier =
        container.read(movementsProvider(MovementType.expense).notifier);

    expect(notifier.hasMore, isTrue);
    await notifier.loadMore();

    final list =
        container.read(movementsProvider(MovementType.expense)).requireValue;
    expect(list.map((m) => m.id), <int>[1, 2, 3, 4]);
    expect(notifier.hasMore, isFalse);
  });

  test('add antepone el movimiento creado', () async {
    final repo = _FakeExpenseRepository();
    final container = _container(repo);
    await container.read(movementsProvider(MovementType.expense).future);

    await container
        .read(movementsProvider(MovementType.expense).notifier)
        .add(MovementDraft(amount: 1, date: DateTime(2026, 1, 1)));

    final list =
        container.read(movementsProvider(MovementType.expense)).requireValue;
    expect(list.first.id, 99);
    expect(repo.createCalls, 1);
  });

  test('remove saca el movimiento de la lista', () async {
    final container = _container(_FakeExpenseRepository());
    await container.read(movementsProvider(MovementType.expense).future);

    await container
        .read(movementsProvider(MovementType.expense).notifier)
        .remove(1);

    final list =
        container.read(movementsProvider(MovementType.expense)).requireValue;
    expect(list.any((m) => m.id == 1), isFalse);
  });
}
