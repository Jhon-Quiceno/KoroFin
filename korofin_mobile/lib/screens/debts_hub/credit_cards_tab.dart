import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../models/credit_card.dart';
import '../../state/cards/cards_controller.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/credit_card_tile.dart';
import 'credit_card_detail_screen.dart';
import 'new_card_sheet.dart';

/// Sub-tab "Tarjetas de crédito" del hub de Deudas (pantalla 9), contra
/// `/api/cards`.
class CreditCardsTab extends ConsumerWidget {
  const CreditCardsTab({super.key});

  Future<void> _add(BuildContext context, WidgetRef ref) async {
    final CardFormData? data = await showNewCardSheet(context);
    if (data == null) return;
    try {
      await ref.read(cardsProvider.notifier).create(
            name: data.name,
            bank: data.bank,
            franchise: data.franchise,
            creditLimit: data.creditLimit,
            monthlyRate: data.monthlyRate,
            cutoffDay: data.cutoffDay,
            paymentDueDay: data.paymentDueDay,
          );
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<List<CreditCard>> cards = ref.watch(cardsProvider);

    return Scaffold(
      floatingActionButton: FloatingActionButton.small(
        heroTag: 'add-card',
        onPressed: () => _add(context, ref),
        child: const Icon(Icons.add),
      ),
      body: cards.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorView(
          message: error is ApiException
              ? error.message
              : 'No se pudieron cargar las tarjetas.',
          onRetry: () => ref.read(cardsProvider.notifier).refresh(),
        ),
        data: (list) {
          if (list.isEmpty) {
            return RefreshIndicator(
              onRefresh: () => ref.read(cardsProvider.notifier).refresh(),
              child: ListView(children: [
                const SizedBox(height: AppSpacing.xxxl),
                EmptyState(
                  icon: Icons.credit_card_outlined,
                  title: 'Sin tarjetas registradas',
                  description: 'Agregá tu primera tarjeta de crédito.',
                  child: ElevatedButton(
                      onPressed: () => _add(context, ref),
                      child: const Text('Agregar tarjeta')),
                ),
              ]),
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.read(cardsProvider.notifier).refresh(),
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
              itemCount: list.length,
              separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
              itemBuilder: (context, index) => CreditCardTile(
                card: list[index],
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) =>
                        CreditCardDetailScreen(cardId: list[index].id),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.lg),
            FilledButton.tonal(
                onPressed: onRetry, child: const Text('Reintentar')),
          ]),
        ),
      );
}
