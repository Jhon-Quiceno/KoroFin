import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/recurring_payment_repository.dart';
import '../../models/recurring_payment.dart';

final recurringPaymentRepositoryProvider =
    Provider<RecurringPaymentRepository>(
  (ref) => RecurringPaymentRepository(ref.read(apiClientProvider)),
);

final recurringPaymentsProvider =
    AsyncNotifierProvider<RecurringPaymentsController, List<RecurringPayment>>(
  RecurringPaymentsController.new,
);

class RecurringPaymentsController
    extends AsyncNotifier<List<RecurringPayment>> {
  RecurringPaymentRepository get _repo =>
      ref.read(recurringPaymentRepositoryProvider);

  @override
  Future<List<RecurringPayment>> build() async => (await _repo.list()).content;

  List<RecurringPayment> get _current =>
      state.valueOrNull ?? const <RecurringPayment>[];

  void _replace(RecurringPayment updated) {
    state = AsyncValue<List<RecurringPayment>>.data(<RecurringPayment>[
      for (final RecurringPayment r in _current)
        if (r.id == updated.id) updated else r,
    ]..sort((a, b) => a.nextPaymentDate.compareTo(b.nextPaymentDate)));
  }

  Future<void> refresh() async {
    state = const AsyncValue<List<RecurringPayment>>.loading();
    state =
        await AsyncValue.guard(() async => (await _repo.list()).content);
  }

  Future<RecurringPayment> create({
    required String name,
    required double amount,
    required RecurringFrequency frequency,
    required DateTime firstPaymentDate,
  }) async {
    final RecurringPayment created = await _repo.create(
      name: name,
      amount: amount,
      frequency: frequency,
      firstPaymentDate: firstPaymentDate,
    );
    state = AsyncValue<List<RecurringPayment>>.data(<RecurringPayment>[
      ..._current,
      created,
    ]..sort((a, b) => a.nextPaymentDate.compareTo(b.nextPaymentDate)));
    return created;
  }

  Future<void> edit(
    int id, {
    required String name,
    required double amount,
    required RecurringFrequency frequency,
  }) async {
    _replace(await _repo.update(id,
        name: name, amount: amount, frequency: frequency));
  }

  Future<void> remove(int id) async {
    await _repo.delete(id);
    state = AsyncValue<List<RecurringPayment>>.data(
      _current.where((r) => r.id != id).toList(growable: false),
    );
  }

  Future<void> toggle(int id) async => _replace(await _repo.toggle(id));

  Future<void> pay(int id) async => _replace(await _repo.pay(id));
}
