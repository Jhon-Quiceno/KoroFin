import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/subscription.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/subscription_tile.dart';
import 'new_subscription_sheet.dart';

/// Sub-tab "Servicios / Suscripciones" inside the Deudas hub (screen 10).
class SubscriptionsTab extends StatefulWidget {
  const SubscriptionsTab({super.key});

  @override
  State<SubscriptionsTab> createState() => _SubscriptionsTabState();
}

class _SubscriptionsTabState extends State<SubscriptionsTab> {
  late List<Subscription> _subscriptions = List.of(MockData.subscriptions);

  Future<void> _add() async {
    final result = await showNewSubscriptionSheet(context);
    if (result != null) setState(() => _subscriptions = [result, ..._subscriptions]);
  }

  @override
  Widget build(BuildContext context) {
    if (_subscriptions.isEmpty) {
      return EmptyState(
        icon: Icons.subscriptions_outlined,
        title: 'Sin servicios registrados',
        description: 'Agregá tus pagos recurrentes para no olvidarlos.',
        child: ElevatedButton(onPressed: _add, child: const Text('Agregar servicio')),
      );
    }
    return Stack(
      children: [
        ListView.separated(
          padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
          itemCount: _subscriptions.length,
          separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
          itemBuilder: (context, index) => SubscriptionTile(subscription: _subscriptions[index]),
        ),
        Positioned(
          right: AppSpacing.lg,
          bottom: AppSpacing.lg,
          child: FloatingActionButton.small(heroTag: 'add-subscription', onPressed: _add, child: const Icon(Icons.add)),
        ),
      ],
    );
  }
}
