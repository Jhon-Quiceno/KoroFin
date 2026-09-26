import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../core/providers.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/income_repository.dart';
import '../../models/movement.dart';
import '../../models/page_response.dart';
import '../auth/auth_controller.dart';
import '../offline_queue/offline_queue_controller.dart';
import '../offline_queue/offline_queue_repository_provider.dart';

/// Resultado de [MovementsController.add]: se creó contra el backend, o (sin
/// conexión) quedó encolado localmente para sincronizar después.
enum AddOutcome { created, queued }

final expenseRepositoryProvider = Provider<ExpenseRepository>(
  (ref) => ExpenseRepository(ref.read(apiClientProvider)),
);

final incomeRepositoryProvider = Provider<IncomeRepository>(
  (ref) => IncomeRepository(ref.read(apiClientProvider)),
);

/// Lista paginada de movimientos de un tipo (gastos o ingresos). Una instancia
/// por [MovementType]. Acumula páginas con `loadMore` y mantiene la lista en
/// memoria tras crear/editar/borrar.
final movementsProvider = AsyncNotifierProvider.family<MovementsController,
    List<Movement>, MovementType>(MovementsController.new);

class MovementsController
    extends FamilyAsyncNotifier<List<Movement>, MovementType> {
  int _page = 0;
  bool _hasMore = true;
  bool _loadingMore = false;

  bool get hasMore => _hasMore;

  @override
  Future<List<Movement>> build(MovementType arg) async {
    _page = 0;
    return _fetchPage(0);
  }

  Future<List<Movement>> _fetchPage(int page) async {
    final PageResponse<Movement> result = arg == MovementType.expense
        ? await ref.read(expenseRepositoryProvider).list(page: page)
        : await ref.read(incomeRepositoryProvider).list(page: page);
    _hasMore = result.hasMore;
    return result.content;
  }

  List<Movement> get _current => state.valueOrNull ?? const <Movement>[];

  Future<void> refresh() async {
    _page = 0;
    state = const AsyncValue<List<Movement>>.loading();
    state = await AsyncValue.guard(() => _fetchPage(0));
  }

  Future<void> loadMore() async {
    if (_loadingMore || !_hasMore || state.isLoading) return;
    _loadingMore = true;
    try {
      final List<Movement> next = await _fetchPage(_page + 1);
      _page += 1;
      state = AsyncValue<List<Movement>>.data(<Movement>[..._current, ...next]);
    } on ApiException {
      // Silencioso: el próximo scroll reintenta.
    } finally {
      _loadingMore = false;
    }
  }

  /// Crea el movimiento contra el backend. Si falla por falta de conexión, en
  /// vez de propagar el error lo encola localmente (ver `OfflineQueueRepository`)
  /// para sincronizarlo automáticamente cuando vuelva la red — cualquier otro
  /// error (validación, servidor) sigue propagándose tal cual, como antes.
  Future<AddOutcome> add(MovementDraft draft) async {
    try {
      final Movement created = arg == MovementType.expense
          ? await ref.read(expenseRepositoryProvider).create(draft)
          : await ref.read(incomeRepositoryProvider).create(draft);
      state =
          AsyncValue<List<Movement>>.data(<Movement>[created, ..._current]);
      return AddOutcome.created;
    } on ApiException catch (error) {
      if (!error.isNetworkError) rethrow;

      // Encolar requiere saber a nombre de quién: crear un movimiento ya
      // exige estar autenticado, así que esto no debería pasar nunca en la
      // práctica. Si pasara, mejor propagar la falla de red original que
      // arriesgar una fila huérfana sin dueño en la cola.
      final int? userId = ref.read(authControllerProvider).user?.id;
      if (userId == null) rethrow;

      if (arg == MovementType.expense) {
        await ref
            .read(offlineQueueRepositoryProvider)
            .enqueueExpense(userId: userId, draft: draft);
      } else {
        await ref
            .read(offlineQueueRepositoryProvider)
            .enqueueIncome(userId: userId, draft: draft);
      }
      unawaited(
        ref.read(offlineQueueControllerProvider.notifier).refreshPendingCount(),
      );
      return AddOutcome.queued;
    }
  }

  Future<void> edit(int id, MovementDraft draft) async {
    final Movement updated = arg == MovementType.expense
        ? await ref.read(expenseRepositoryProvider).update(id, draft)
        : await ref.read(incomeRepositoryProvider).update(id, draft);
    state = AsyncValue<List<Movement>>.data(<Movement>[
      for (final Movement m in _current) if (m.id == id) updated else m,
    ]);
  }

  Future<void> remove(int id) async {
    if (arg == MovementType.expense) {
      await ref.read(expenseRepositoryProvider).delete(id);
    } else {
      await ref.read(incomeRepositoryProvider).delete(id);
    }
    state = AsyncValue<List<Movement>>.data(
      _current.where((Movement m) => m.id != id).toList(growable: false),
    );
  }
}
