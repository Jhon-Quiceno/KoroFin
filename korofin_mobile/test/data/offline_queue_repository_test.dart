import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/core/storage/offline_queue_store.dart';
import 'package:korofin_mobile/data/repositories/expense_repository.dart';
import 'package:korofin_mobile/data/repositories/income_repository.dart';
import 'package:korofin_mobile/data/repositories/offline_queue_repository.dart';
import 'package:korofin_mobile/models/movement.dart';

Movement _created(int id) =>
    Movement(id: id, type: MovementType.expense, amount: 1, date: DateTime(2026, 1, 1));

/// Repo falso cuyo comportamiento por llamada se define a mano: cada entrada
/// de [behaviors] es o bien un `Movement` (éxito) o una `ApiException` (falla)
/// para el N-ésimo `create()`. Si se agota la lista, el resto de las llamadas
/// tienen éxito.
class _ScriptedExpenseRepository implements ExpenseRepository {
  _ScriptedExpenseRepository(this.behaviors);

  final List<Object> behaviors;
  int calls = 0;
  final List<MovementDraft> receivedDrafts = <MovementDraft>[];

  @override
  Future<Movement> create(MovementDraft draft) async {
    receivedDrafts.add(draft);
    final int index = calls;
    calls++;
    final Object behavior =
        index < behaviors.length ? behaviors[index] : _created(index);
    if (behavior is ApiException) throw behavior;
    return behavior as Movement;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('no debería usarse en este test');
}

class _UnusedIncomeRepository implements IncomeRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('no debería usarse en este test');
}

MovementDraft _draft() =>
    MovementDraft(amount: 1000, date: DateTime(2026, 9, 1));

void main() {
  test('una sincronización exitosa vacía la cola', () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final expenses = _ScriptedExpenseRepository(<Object>[]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    final SyncResult result = await repo.sync(1);

    expect(result.synced, 1);
    expect(result.discarded, 0);
    expect(await store.pendingFor(1), isEmpty);
  });

  test('un fallo de red conserva la entrada para el próximo intento',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final expenses = _ScriptedExpenseRepository(<Object>[
      const ApiException(message: 'sin red', isNetworkError: true),
    ]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    final SyncResult result = await repo.sync(1);

    expect(result.synced, 0);
    expect(result.discarded, 0);
    expect(await store.pendingFor(1), hasLength(1));
  });

  test(
      'un fallo de red detiene el resto del lote sin descartar las siguientes entradas',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 2));
    final expenses = _ScriptedExpenseRepository(<Object>[
      const ApiException(message: 'sin red', isNetworkError: true),
    ]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    await repo.sync(1);

    expect(expenses.calls, 1); // nunca llegó a intentar la segunda entrada
    expect(await store.pendingFor(1), hasLength(2));
  });

  test('un fallo permanente (422) descarta esa entrada sin bloquear el resto',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 2));
    final expenses = _ScriptedExpenseRepository(<Object>[
      const ApiException(message: 'categoría inválida', statusCode: 422),
      _created(2),
    ]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    final SyncResult result = await repo.sync(1);

    expect(result.discarded, 1);
    expect(result.synced, 1);
    expect(await store.pendingFor(1), isEmpty);
  });

  test(
      'un fallo permanente no se reintenta jamás, ni una sola vez adicional',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final expenses = _ScriptedExpenseRepository(<Object>[
      const ApiException(message: 'duplicado', statusCode: 409),
    ]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    await repo.sync(1);

    expect(expenses.calls, 1);
    expect(await store.pendingFor(1), isEmpty);
  });

  test(
      'un 401/403 (sesión no recuperable) conserva la entrada, igual que un fallo de red',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final expenses = _ScriptedExpenseRepository(<Object>[
      const ApiException(message: 'sesión expirada', statusCode: 401),
    ]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    await repo.sync(1);

    expect(await store.pendingFor(1), hasLength(1));
  });

  test(
      'un fallo ambiguo (5xx) no genera reintentos infinitos: se descarta tras el tope',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final serverError =
        const ApiException(message: 'error del servidor', statusCode: 500);
    final expenses = _ScriptedExpenseRepository(
        List<Object>.filled(10, serverError)); // siempre falla
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    // Cada sync() drena una sola pasada de la cola; hay que llamar varias
    // veces para simular varios eventos de reconexión sucesivos.
    for (int i = 0; i < 5; i++) {
      await repo.sync(1);
    }

    expect(await store.pendingFor(1), isEmpty,
        reason: 'debió descartarse al agotar el tope de reintentos');
    expect(expenses.calls, lessThan(10),
        reason: 'no debió reintentar indefinidamente');
  });

  test('se respeta el orden FIFO al sincronizar', () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: MovementDraft(amount: 1, date: DateTime(2026, 9, 1)),
        createdAt: DateTime(2026, 9, 1));
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: MovementDraft(amount: 2, date: DateTime(2026, 9, 2)),
        createdAt: DateTime(2026, 9, 2));
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: MovementDraft(amount: 3, date: DateTime(2026, 9, 3)),
        createdAt: DateTime(2026, 9, 3));
    final expenses = _ScriptedExpenseRepository(<Object>[]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    await repo.sync(1);

    expect(expenses.receivedDrafts.map((d) => d.amount), <double>[1, 2, 3]);
  });

  test('dos sincronizaciones concurrentes no duplican envíos', () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final expenses = _ScriptedExpenseRepository(<Object>[]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    // Dos llamadas disparadas "a la vez" (sin esperar la primera antes de
    // lanzar la segunda), como pasaría si el evento de conectividad y el
    // arranque de la app dispararan sync() casi al mismo tiempo.
    final Future<SyncResult> first = repo.sync(1);
    final Future<SyncResult> second = repo.sync(1);
    await Future.wait(<Future<SyncResult>>[first, second]);

    expect(expenses.calls, 1);
  });

  test('el scoping por userId aísla la sincronización entre usuarios',
      () async {
    final store = InMemoryOfflineQueueStore();
    await store.enqueue(
        userId: 1,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    await store.enqueue(
        userId: 2,
        type: MovementType.expense,
        draft: _draft(),
        createdAt: DateTime(2026, 9, 1));
    final expenses = _ScriptedExpenseRepository(<Object>[]);
    final repo =
        OfflineQueueRepository(store, expenses, _UnusedIncomeRepository());

    await repo.sync(1);

    expect(expenses.calls, 1); // solo la del usuario 1
    expect(await store.pendingFor(1), isEmpty);
    expect(await store.pendingFor(2), hasLength(1)); // la del usuario 2 sigue intacta
  });
}
