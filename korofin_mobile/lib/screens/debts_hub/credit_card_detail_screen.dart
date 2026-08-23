import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../data/mock_data.dart';
import '../../models/credit_card.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/list_items/credit_card_tile.dart';

/// Credit card detail: movement history plus quick actions to register a
/// purchase or a payment.
class CreditCardDetailScreen extends StatelessWidget {
  const CreditCardDetailScreen({super.key, required this.card});

  final AppCreditCard card;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      appBar: AppBar(title: Text(card.name)),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          CreditCardTile(card: card, onTap: () {}),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.shopping_cart_outlined, size: 18),
                  label: const Text('Registrar compra'),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.payments_outlined, size: 18),
                  label: const Text('Registrar pago'),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          Text('Movimientos', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.sm),
          for (final movement in MockData.cardMovements)
            Card(
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: (movement.type == CardMovementType.payment ? koro.success : koro.accent).withValues(alpha: 0.14),
                  child: Icon(
                    movement.type == CardMovementType.payment ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded,
                    size: 16,
                    color: movement.type == CardMovementType.payment ? koro.success : koro.accent,
                  ),
                ),
                title: Text(movement.description),
                subtitle: Text(AppFormatters.shortDate(movement.date)),
                trailing: Text(AppFormatters.currency(movement.amount), style: Theme.of(context).textTheme.titleMedium),
              ),
            ),
        ],
      ),
    );
  }
}
