import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/credit_card.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/credit_card_tile.dart';
import 'credit_card_detail_screen.dart';
import 'new_card_sheet.dart';

/// Sub-tab "Tarjetas de crédito" inside the Deudas hub (screen 9).
class CreditCardsTab extends StatefulWidget {
  const CreditCardsTab({super.key});

  @override
  State<CreditCardsTab> createState() => _CreditCardsTabState();
}

class _CreditCardsTabState extends State<CreditCardsTab> {
  late List<AppCreditCard> _cards = List.of(MockData.creditCards);

  Future<void> _addCard() async {
    final result = await showNewCardSheet(context);
    if (result != null) setState(() => _cards = [result, ..._cards]);
  }

  void _openDetail(AppCreditCard card) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => CreditCardDetailScreen(card: card)));
  }

  @override
  Widget build(BuildContext context) {
    if (_cards.isEmpty) {
      return EmptyState(
        icon: Icons.credit_card_outlined,
        title: 'Sin tarjetas registradas',
        description: 'Agregá tu primera tarjeta de crédito.',
        child: ElevatedButton(onPressed: _addCard, child: const Text('Agregar tarjeta')),
      );
    }
    return Stack(
      children: [
        ListView.separated(
          padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
          itemCount: _cards.length,
          separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
          itemBuilder: (context, index) {
            final card = _cards[index];
            return CreditCardTile(card: card, onTap: () => _openDetail(card));
          },
        ),
        Positioned(
          right: AppSpacing.lg,
          bottom: AppSpacing.lg,
          child: FloatingActionButton.small(heroTag: 'add-card', onPressed: _addCard, child: const Icon(Icons.add)),
        ),
      ],
    );
  }
}
