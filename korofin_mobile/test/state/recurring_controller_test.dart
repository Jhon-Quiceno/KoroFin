import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/recurring_payment_repository.dart';
import 'package:korofin_mobile/models/page_response.dart';
import 'package:korofin_mobile/models/recurring_payment.dart';
import 'package:korofin_mobile/state/recurring/recurring_controller.dart';

RecurringPayment _rp(int id, {bool active = true, DateTime? next}) =>
    RecurringPayment(
      id: id,
      name: 'S$id',
      amount: 100,
      frequency: RecurringFrequency.monthly,
      nextPaymentDate: next ?? DateTime(2026, 10, id + 1),
      isActive: active,
    );

class _FakeRepo implements RecurringPaymentRepository {
  final List<RecurringPayment> store = <RecurringPayment>[_rp(2), _rp(1)];

  @override
  Future<PageResponse<RecurringPayment>> list(
          {int page = 0, int size = 100}) async =>
      PageResponse<RecurringPayment>(
        content: List<RecurringPayment>.of(store),
        page: 0,
        totalElements: store.length,
        totalPages: 1,
        isLast: true,
      );

  @override
  Future<RecurringPayment> toggle(int id) async {
    final i = store.indexWhere((r) => r.id == id);
    final t = _rp(id, active: !store[i].isActive);
    store[i] = t;
    return t;
  }

  @override
  Future<RecurringPayment> pay(int id) async {
    final i = store.indexWhere((r) => r.id == id);
    final p = _rp(id, next: DateTime(2027, 1, 1));
    store[i] = p;
    return p;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

ProviderContainer _container(RecurringPaymentRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    recurringPaymentRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build carga la lista', () async {
    final c = _container(_FakeRepo());
    final list = await c.read(recurringPaymentsProvider.future);
    expect(list, hasLength(2));
  });

  test('toggle actualiza el estado y reordena por fecha', () async {
    final c = _container(_FakeRepo());
    await c.read(recurringPaymentsProvider.future);

    await c.read(recurringPaymentsProvider.notifier).toggle(2);

    final r = c
        .read(recurringPaymentsProvider)
        .requireValue
        .firstWhere((x) => x.id == 2);
    expect(r.isActive, isFalse);
  });

  test('pay avanza la fecha del recurrente', () async {
    final c = _container(_FakeRepo());
    await c.read(recurringPaymentsProvider.future);

    await c.read(recurringPaymentsProvider.notifier).pay(1);

    final r = c
        .read(recurringPaymentsProvider)
        .requireValue
        .firstWhere((x) => x.id == 1);
    expect(r.nextPaymentDate, DateTime(2027, 1, 1));
  });
}
