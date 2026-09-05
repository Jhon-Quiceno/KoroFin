import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/card_repository.dart';
import '../../models/credit_card.dart';

final cardRepositoryProvider = Provider<CardRepository>(
  (ref) => CardRepository(ref.read(apiClientProvider)),
);

final cardsProvider =
    AsyncNotifierProvider<CardsController, List<CreditCard>>(CardsController.new);

class CardsController extends AsyncNotifier<List<CreditCard>> {
  CardRepository get _repo => ref.read(cardRepositoryProvider);

  @override
  Future<List<CreditCard>> build() async => (await _repo.list()).content;

  List<CreditCard> get _current => state.valueOrNull ?? const <CreditCard>[];

  Future<void> refresh() async {
    state = const AsyncValue<List<CreditCard>>.loading();
    state = await AsyncValue.guard(() async => (await _repo.list()).content);
  }

  Future<CreditCard> create({
    required String name,
    String? bank,
    required CardFranchise franchise,
    required double creditLimit,
    required double monthlyRate,
    required int cutoffDay,
    required int paymentDueDay,
  }) async {
    final CreditCard created = await _repo.create(
      name: name,
      bank: bank,
      franchise: franchise,
      creditLimit: creditLimit,
      monthlyRate: monthlyRate,
      cutoffDay: cutoffDay,
      paymentDueDay: paymentDueDay,
    );
    state = AsyncValue<List<CreditCard>>.data(<CreditCard>[created, ..._current]);
    return created;
  }

  Future<void> edit(
    int id, {
    required String name,
    String? bank,
    required double monthlyRate,
    required int cutoffDay,
    required int paymentDueDay,
  }) async {
    final CreditCard updated = await _repo.update(
      id,
      name: name,
      bank: bank,
      monthlyRate: monthlyRate,
      cutoffDay: cutoffDay,
      paymentDueDay: paymentDueDay,
    );
    state = AsyncValue<List<CreditCard>>.data(<CreditCard>[
      for (final CreditCard c in _current) if (c.id == id) updated else c,
    ]);
  }

  Future<void> remove(int id) async {
    await _repo.delete(id);
    state = AsyncValue<List<CreditCard>>.data(
      _current.where((CreditCard c) => c.id != id).toList(growable: false),
    );
  }
}

typedef CardDetail = ({CreditCard card, List<CardMovement> movements});

final cardDetailProvider =
    FutureProvider.family<CardDetail, int>((ref, cardId) async {
  final CardRepository repo = ref.read(cardRepositoryProvider);
  final CreditCard card = await repo.get(cardId);
  final movements = (await repo.movements(cardId)).content;
  return (card: card, movements: movements);
});

final cardInstallmentsProvider =
    FutureProvider.family<List<Installment>, ({int cardId, int movementId})>(
  (ref, arg) => ref
      .read(cardRepositoryProvider)
      .installments(arg.cardId, arg.movementId),
);
