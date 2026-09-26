import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/core/providers.dart';
import 'package:korofin_mobile/core/network/connectivity_gateway.dart';
import 'package:korofin_mobile/core/storage/offline_queue_store.dart';
import 'package:korofin_mobile/data/repositories/expense_repository.dart';
import 'package:korofin_mobile/data/repositories/income_repository.dart';
import 'package:korofin_mobile/data/repositories/offline_queue_repository.dart';
import 'package:korofin_mobile/models/movement.dart';
import 'package:korofin_mobile/models/page_response.dart';
import 'package:korofin_mobile/models/user.dart';
import 'package:korofin_mobile/state/auth/auth_controller.dart';
import 'package:korofin_mobile/state/auth/auth_state.dart';
import 'package:korofin_mobile/state/movements/movements_controller.dart';
import 'package:korofin_mobile/state/offline_queue/offline_queue_repository_provider.dart';

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

/// Repo que simula que no hay conexión al crear: devuelve la lista normalmente
/// pero falla el `create` con un error de red, que es la condición exacta que
/// tiene que disparar el encolado offline.
class _OfflineExpenseRepository extends _FakeExpenseRepository {
  @override
  Future<Movement> create(MovementDraft draft) async {
    createCalls++;
    throw const ApiException(message: 'sin conexión', isNetworkError: true);
  }
}

/// Repo que falla con un error que NO es de red: debe propagarse tal cual, sin
/// encolar nada.
class _RejectingExpenseRepository extends _FakeExpenseRepository {
  @override
  Future<Movement> create(MovementDraft draft) async {
    createCalls++;
    throw const ApiException(message: 'categoría inválida', statusCode: 422);
  }
}

/// Contenedor con sesión iniciada y la cola offline apuntando a un store en
/// memoria, para poder inspeccionar qué quedó encolado.
ProviderContainer _offlineContainer(
  ExpenseRepository repo,
  OfflineQueueStore store, {
  int userId = 7,
}) {
  final OfflineQueueRepository queue =
      OfflineQueueRepository(store, repo, _UnusedIncomeRepository());
  final container = ProviderContainer(overrides: <Override>[
    expenseRepositoryProvider.overrideWithValue(repo),
    incomeRepositoryProvider.overrideWithValue(_UnusedIncomeRepository()),
    offlineQueueRepositoryProvider.overrideWithValue(queue),
    connectivityGatewayProvider
        .overrideWithValue(FakeConnectivityGateway(connected: false)),
    authControllerProvider.overrideWith(() => _LoggedInAuthController(userId)),
  ]);
  addTearDown(container.dispose);
  return container;
}

/// Controller de auth ya autenticado: `add` necesita el id del usuario para
/// saber a nombre de quién encola.
class _LoggedInAuthController extends AuthController {
  _LoggedInAuthController(this.userId);

  final int userId;

  @override
  AuthState build() => AuthState(
        status: AuthStatus.authenticated,
        user: User(
          id: userId,
          name: 'Ana',
          email: 'ana@korofin.dev',
          theme: 'SYSTEM',
          currency: 'COP',
          language: 'ES',
        ),
      );
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

  test('add encola el movimiento cuando falla por falta de conexión', () async {
    final store = InMemoryOfflineQueueStore();
    final repo = _OfflineExpenseRepository();
    final container = _offlineContainer(repo, store);
    await container.read(movementsProvider(MovementType.expense).future);

    final AddOutcome outcome = await container
        .read(movementsProvider(MovementType.expense).notifier)
        .add(MovementDraft(amount: 2500, date: DateTime(2026, 9, 1)));

    expect(outcome, AddOutcome.queued);
    final pending = await store.pendingFor(7);
    expect(pending, hasLength(1));
    expect(pending.single.draft.amount, 2500);
    expect(pending.single.type, MovementType.expense);
  });

  test('lo encolado queda a nombre del usuario de la sesión, no de otro',
      () async {
    final store = InMemoryOfflineQueueStore();
    final container = _offlineContainer(_OfflineExpenseRepository(), store,
        userId: 7);
    await container.read(movementsProvider(MovementType.expense).future);

    await container
        .read(movementsProvider(MovementType.expense).notifier)
        .add(MovementDraft(amount: 900, date: DateTime(2026, 9, 1)));

    // El scoping es la garantía de que otro usuario que entre en el mismo
    // dispositivo no herede ni sincronice movimientos ajenos.
    expect(await store.pendingFor(7), hasLength(1));
    expect(await store.pendingFor(8), isEmpty);
  });

  test('un error que no es de red se propaga y no encola nada', () async {
    final store = InMemoryOfflineQueueStore();
    final container = _offlineContainer(_RejectingExpenseRepository(), store);
    await container.read(movementsProvider(MovementType.expense).future);

    await expectLater(
      container
          .read(movementsProvider(MovementType.expense).notifier)
          .add(MovementDraft(amount: 100, date: DateTime(2026, 9, 1))),
      throwsA(isA<ApiException>()),
    );
    expect(await store.pendingFor(7), isEmpty);
  });
}
