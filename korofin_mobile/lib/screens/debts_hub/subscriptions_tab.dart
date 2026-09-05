import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../models/recurring_payment.dart';
import '../../state/recurring/recurring_controller.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/subscription_tile.dart';
import 'new_subscription_sheet.dart';

/// Sub-tab "Servicios / Suscripciones" del hub de Deudas (pantalla 10), contra
/// `/api/recurring`.
class SubscriptionsTab extends ConsumerWidget {
  const SubscriptionsTab({super.key});

  Future<void> _guard(
      BuildContext context, Future<void> Function() action) async {
    try {
      await action();
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _add(BuildContext context, WidgetRef ref) async {
    final RecurringFormData? data = await showNewSubscriptionSheet(context);
    if (data == null || !context.mounted) return;
    await _guard(
      context,
      () => ref.read(recurringPaymentsProvider.notifier).create(
            name: data.name,
            amount: data.amount,
            frequency: data.frequency,
            firstPaymentDate: data.firstPaymentDate,
          ),
    );
  }

  Future<void> _edit(
      BuildContext context, WidgetRef ref, RecurringPayment p) async {
    final RecurringFormData? data =
        await showNewSubscriptionSheet(context, initial: p);
    if (data == null || !context.mounted) return;
    await _guard(
      context,
      () => ref.read(recurringPaymentsProvider.notifier).edit(
            p.id,
            name: data.name,
            amount: data.amount,
            frequency: data.frequency,
          ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<List<RecurringPayment>> payments =
        ref.watch(recurringPaymentsProvider);
    final notifier = ref.read(recurringPaymentsProvider.notifier);

    return Scaffold(
      floatingActionButton: FloatingActionButton.small(
        heroTag: 'add-subscription',
        onPressed: () => _add(context, ref),
        child: const Icon(Icons.add),
      ),
      body: payments.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorView(
          message: error is ApiException
              ? error.message
              : 'No se pudieron cargar los servicios.',
          onRetry: notifier.refresh,
        ),
        data: (list) {
          if (list.isEmpty) {
            return RefreshIndicator(
              onRefresh: notifier.refresh,
              child: ListView(children: [
                const SizedBox(height: AppSpacing.xxxl),
                EmptyState(
                  icon: Icons.subscriptions_outlined,
                  title: 'Sin servicios registrados',
                  description:
                      'Agregá tus pagos recurrentes para no olvidarlos.',
                  child: ElevatedButton(
                      onPressed: () => _add(context, ref),
                      child: const Text('Agregar servicio')),
                ),
              ]),
            );
          }
          return RefreshIndicator(
            onRefresh: notifier.refresh,
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
              itemCount: list.length,
              separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
              itemBuilder: (context, index) {
                final RecurringPayment p = list[index];
                return Dismissible(
                  key: ValueKey<int>(p.id),
                  direction: DismissDirection.endToStart,
                  background: Container(
                    alignment: Alignment.centerRight,
                    padding: const EdgeInsets.only(right: AppSpacing.lg),
                    color: Theme.of(context)
                        .colorScheme
                        .error
                        .withValues(alpha: 0.15),
                    child: Icon(Icons.delete_outline,
                        color: Theme.of(context).colorScheme.error),
                  ),
                  confirmDismiss: (_) => showDialog<bool>(
                    context: context,
                    builder: (context) => AlertDialog(
                      title: Text('¿Borrar "${p.name}"?'),
                      actions: [
                        TextButton(
                            onPressed: () => Navigator.of(context).pop(false),
                            child: const Text('Cancelar')),
                        FilledButton(
                            onPressed: () => Navigator.of(context).pop(true),
                            child: const Text('Borrar')),
                      ],
                    ),
                  ),
                  onDismissed: (_) =>
                      _guard(context, () => notifier.remove(p.id)),
                  child: SubscriptionTile(
                    payment: p,
                    onTap: () => _edit(context, ref, p),
                    onPay: () => _guard(context, () => notifier.pay(p.id)),
                    onToggle: () =>
                        _guard(context, () => notifier.toggle(p.id)),
                  ),
                );
              },
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
  final Future<void> Function() onRetry;

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
