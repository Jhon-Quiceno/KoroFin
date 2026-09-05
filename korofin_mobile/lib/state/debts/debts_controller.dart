import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/debt_repository.dart';
import '../../models/debt.dart';

final debtRepositoryProvider = Provider<DebtRepository>(
  (ref) => DebtRepository(ref.read(apiClientProvider)),
);

/// Lista de deudas del usuario. Sin paginación en la UI: se trae una página
/// grande y las mutaciones actualizan la lista en memoria.
final debtsProvider =
    AsyncNotifierProvider<DebtsController, List<Debt>>(DebtsController.new);

class DebtsController extends AsyncNotifier<List<Debt>> {
  DebtRepository get _repo => ref.read(debtRepositoryProvider);

  @override
  Future<List<Debt>> build() async => (await _repo.list(size: 100)).content;

  List<Debt> get _current => state.valueOrNull ?? const <Debt>[];

  Future<void> refresh() async {
    state = const AsyncValue<List<Debt>>.loading();
    state = await AsyncValue.guard(() async => (await _repo.list(size: 100)).content);
  }

  void replace(Debt debt) {
    state = AsyncValue<List<Debt>>.data(<Debt>[
      for (final Debt d in _current) if (d.id == debt.id) debt else d,
    ]);
  }

  Future<Debt> create({
    required String name,
    required double totalAmount,
    double? interestRate,
    DateTime? dueDate,
  }) async {
    final Debt created = await _repo.create(
      name: name,
      totalAmount: totalAmount,
      interestRate: interestRate,
      dueDate: dueDate,
    );
    state = AsyncValue<List<Debt>>.data(<Debt>[created, ..._current]);
    return created;
  }

  Future<void> edit(
    int id, {
    required String name,
    double? interestRate,
    DateTime? dueDate,
  }) async {
    final Debt updated = await _repo.update(
      id,
      name: name,
      interestRate: interestRate,
      dueDate: dueDate,
    );
    replace(updated);
  }

  Future<void> remove(int id) async {
    await _repo.delete(id);
    state = AsyncValue<List<Debt>>.data(
      _current.where((Debt d) => d.id != id).toList(growable: false),
    );
  }
}

/// Detalle de una deuda: la deuda + su historial de abonos y cargos.
typedef DebtDetail = ({Debt debt, List<DebtPayment> payments, List<DebtCharge> charges});

final debtDetailProvider =
    FutureProvider.family<DebtDetail, int>((ref, debtId) async {
  final DebtRepository repo = ref.read(debtRepositoryProvider);
  final Debt debt = await repo.get(debtId);
  final payments = (await repo.payments(debtId)).content;
  final charges = (await repo.charges(debtId)).content;
  return (debt: debt, payments: payments, charges: charges);
});
