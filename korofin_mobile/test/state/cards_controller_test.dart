import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/card_repository.dart';
import 'package:korofin_mobile/models/credit_card.dart';
import 'package:korofin_mobile/models/page_response.dart';
import 'package:korofin_mobile/state/cards/cards_controller.dart';

CreditCard _card(int id) => CreditCard(
      id: id,
      name: 'C$id',
      franchise: CardFranchise.visa,
      creditLimit: 1000,
      monthlyRate: 2,
      cutoffDay: 1,
      paymentDueDay: 15,
      currentBalance: 0,
      availableCredit: 1000,
    );

class _FakeCardRepository implements CardRepository {
  final List<CreditCard> store = <CreditCard>[_card(1)];
  int _next = 2;

  @override
  Future<PageResponse<CreditCard>> list({int page = 0, int size = 50}) async =>
      PageResponse<CreditCard>(
        content: List<CreditCard>.of(store),
        page: 0,
        totalElements: store.length,
        totalPages: 1,
        isLast: true,
      );

  @override
  Future<CreditCard> create({
    required String name,
    String? bank,
    required CardFranchise franchise,
    required double creditLimit,
    required double monthlyRate,
    required int cutoffDay,
    required int paymentDueDay,
  }) async {
    final c = _card(_next++);
    store.insert(0, c);
    return c;
  }

  @override
  Future<void> delete(int id) async => store.removeWhere((c) => c.id == id);

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

ProviderContainer _container(CardRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    cardRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build carga las tarjetas', () async {
    final c = _container(_FakeCardRepository());
    final list = await c.read(cardsProvider.future);
    expect(list.single.id, 1);
  });

  test('create antepone y remove elimina', () async {
    final c = _container(_FakeCardRepository());
    await c.read(cardsProvider.future);

    await c.read(cardsProvider.notifier).create(
          name: 'Nueva',
          franchise: CardFranchise.mastercard,
          creditLimit: 2000,
          monthlyRate: 2,
          cutoffDay: 5,
          paymentDueDay: 20,
        );
    expect(c.read(cardsProvider).requireValue, hasLength(2));

    await c.read(cardsProvider.notifier).remove(1);
    expect(c.read(cardsProvider).requireValue.any((x) => x.id == 1), isFalse);
  });
}
