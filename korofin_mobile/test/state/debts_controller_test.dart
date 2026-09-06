import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/debt_repository.dart';
import 'package:korofin_mobile/models/debt.dart';
import 'package:korofin_mobile/models/page_response.dart';
import 'package:korofin_mobile/state/debts/debts_controller.dart';

Debt _debt(int id, {double remaining = 100}) => Debt(
      id: id,
      name: 'D$id',
      totalAmount: 200,
      remainingAmount: remaining,
    );

class _FakeDebtRepository implements DebtRepository {
  final List<Debt> store = <Debt>[_debt(1), _debt(2)];
  int _next = 3;

  @override
  Future<PageResponse<Debt>> list({int page = 0, int size = 20}) async =>
      PageResponse<Debt>(
        content: List<Debt>.of(store),
        page: 0,
        totalElements: store.length,
        totalPages: 1,
        isLast: true,
      );

  @override
  Future<Debt> create({
    required String name,
    required double totalAmount,
    double? interestRate,
    DateTime? dueDate,
  }) async {
    final d = Debt(
        id: _next++,
        name: name,
        totalAmount: totalAmount,
        remainingAmount: totalAmount);
    store.insert(0, d);
    return d;
  }

  @override
  Future<Debt> update(int id,
      {required String name, double? interestRate, DateTime? dueDate}) async {
    final d = Debt(id: id, name: name, totalAmount: 200, remainingAmount: 100);
    store[store.indexWhere((x) => x.id == id)] = d;
    return d;
  }

  @override
  Future<void> delete(int id) async => store.removeWhere((x) => x.id == id);

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

ProviderContainer _container(DebtRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    debtRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build carga las deudas', () async {
    final c = _container(_FakeDebtRepository());
    final list = await c.read(debtsProvider.future);
    expect(list.map((d) => d.id), <int>[1, 2]);
  });

  test('create antepone la deuda nueva', () async {
    final c = _container(_FakeDebtRepository());
    await c.read(debtsProvider.future);

    await c.read(debtsProvider.notifier).create(name: 'Auto', totalAmount: 5000);

    expect(c.read(debtsProvider).requireValue.first.name, 'Auto');
  });

  test('edit reemplaza y remove elimina', () async {
    final c = _container(_FakeDebtRepository());
    await c.read(debtsProvider.future);

    await c.read(debtsProvider.notifier).edit(1, name: 'Renombrada');
    expect(
        c.read(debtsProvider).requireValue.firstWhere((d) => d.id == 1).name,
        'Renombrada');

    await c.read(debtsProvider.notifier).remove(2);
    expect(c.read(debtsProvider).requireValue.any((d) => d.id == 2), isFalse);
  });
}
