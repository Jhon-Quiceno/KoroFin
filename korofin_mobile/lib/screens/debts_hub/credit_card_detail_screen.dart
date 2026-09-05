import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../data/formatters.dart';
import '../../models/credit_card.dart';
import '../../state/cards/cards_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/list_items/credit_card_tile.dart';
import 'new_card_sheet.dart';

/// Detalle de una tarjeta: historial de movimientos, plan de cuotas y acciones
/// de registrar compra (con cuotas opcionales) y pago.
class CreditCardDetailScreen extends ConsumerWidget {
  const CreditCardDetailScreen({super.key, required this.cardId});

  final int cardId;

  Future<void> _guard(BuildContext context, WidgetRef ref,
      Future<void> Function() action) async {
    try {
      await action();
      ref.invalidate(cardDetailProvider(cardId));
      ref.invalidate(cardsProvider);
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _purchase(BuildContext context, WidgetRef ref) async {
    final _PurchaseInput? r = await showDialog<_PurchaseInput>(
      context: context,
      builder: (_) => const _PurchaseDialog(),
    );
    if (r == null || !context.mounted) return;
    await _guard(
      context,
      ref,
      () => ref.read(cardRepositoryProvider).registerPurchase(
            cardId,
            amount: r.amount,
            description: r.description,
            installmentCount: r.installments,
          ),
    );
  }

  Future<void> _payment(BuildContext context, WidgetRef ref) async {
    final double? amount = await _amountDialog(context, 'Registrar pago');
    if (amount == null || !context.mounted) return;
    await _guard(
      context,
      ref,
      () => ref
          .read(cardRepositoryProvider)
          .registerPayment(cardId, amount: amount),
    );
  }

  Future<void> _edit(BuildContext context, WidgetRef ref, CreditCard card) async {
    final CardFormData? data = await showNewCardSheet(context, initial: card);
    if (data == null || !context.mounted) return;
    await _guard(
      context,
      ref,
      () => ref.read(cardsProvider.notifier).edit(
            cardId,
            name: data.name,
            bank: data.bank,
            monthlyRate: data.monthlyRate,
            cutoffDay: data.cutoffDay,
            paymentDueDay: data.paymentDueDay,
          ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final bool ok = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('¿Borrar la tarjeta?'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.of(context).pop(false),
                  child: const Text('Cancelar')),
              FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  child: const Text('Borrar')),
            ],
          ),
        ) ??
        false;
    if (!ok) return;
    try {
      await ref.read(cardsProvider.notifier).remove(cardId);
      if (context.mounted) Navigator.of(context).pop();
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _showInstallments(
      BuildContext context, WidgetRef ref, int movementId) async {
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) => Consumer(
        builder: (context, ref, _) {
          final installments = ref.watch(cardInstallmentsProvider(
              (cardId: cardId, movementId: movementId)));
          return Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: installments.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, _) => const Text('No se pudo cargar el plan.'),
              data: (list) => Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Plan de cuotas',
                      style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: AppSpacing.md),
                  for (final i in list)
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      leading: CircleAvatar(
                          radius: 14, child: Text('${i.number}')),
                      title: Text(AppFormatters.currency(i.total)),
                      subtitle: Text(
                          'Capital ${AppFormatters.currency(i.capitalAmount)} · '
                          'Interés ${AppFormatters.currency(i.interestAmount)}'),
                      trailing: Text(
                        i.status == InstallmentStatus.billed
                            ? 'Facturada'
                            : AppFormatters.shortDate(i.dueDate),
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<CardDetail> detail = ref.watch(cardDetailProvider(cardId));
    final koro = context.koroColors;

    return Scaffold(
      appBar: AppBar(
        title: Text(detail.valueOrNull?.card.name ?? 'Tarjeta'),
        actions: [
          if (detail.hasValue) ...[
            IconButton(
                icon: const Icon(Icons.edit_outlined),
                onPressed: () => _edit(context, ref, detail.value!.card)),
            IconButton(
                icon: const Icon(Icons.delete_outline),
                onPressed: () => _delete(context, ref)),
          ],
        ],
      ),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Text(error is ApiException
              ? error.message
              : 'No se pudo cargar la tarjeta.'),
        ),
        data: (data) => ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            CreditCardTile(card: data.card, onTap: () {}),
            const SizedBox(height: AppSpacing.md),
            Text(
              'Corte día ${data.card.cutoffDay} · Pago día ${data.card.paymentDueDay} · '
              'Tasa ${data.card.monthlyRate.toStringAsFixed(2)}%',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.lg),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _purchase(context, ref),
                    icon: const Icon(Icons.shopping_cart_outlined, size: 18),
                    label: const Text('Compra'),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () => _payment(context, ref),
                    icon: const Icon(Icons.payments_outlined, size: 18),
                    label: const Text('Pago'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xl),
            Text('Movimientos',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            if (data.movements.isEmpty)
              const Text('Sin movimientos todavía.')
            else
              for (final m in data.movements)
                Card(
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor:
                          (m.kind.reducesBalance ? koro.success : koro.accent)
                              .withValues(alpha: 0.14),
                      child: Icon(
                        m.kind.reducesBalance
                            ? Icons.south_west
                            : Icons.north_east,
                        size: 16,
                        color:
                            m.kind.reducesBalance ? koro.success : koro.accent,
                      ),
                    ),
                    title: Text(m.description?.isNotEmpty == true
                        ? m.description!
                        : m.kind.label),
                    subtitle: Text(
                      '${m.kind.label} · ${AppFormatters.shortDate(m.date)}',
                    ),
                    trailing: Text(AppFormatters.currency(m.amount),
                        style: Theme.of(context).textTheme.titleMedium),
                    onTap: m.hasInstallments
                        ? () => _showInstallments(context, ref, m.id)
                        : null,
                  ),
                ),
          ],
        ),
      ),
    );
  }
}

class _PurchaseInput {
  const _PurchaseInput(this.amount, this.description, this.installments);
  final double amount;
  final String? description;
  final int? installments;
}

class _PurchaseDialog extends StatefulWidget {
  const _PurchaseDialog();

  @override
  State<_PurchaseDialog> createState() => _PurchaseDialogState();
}

class _PurchaseDialogState extends State<_PurchaseDialog> {
  final _amount = TextEditingController();
  final _desc = TextEditingController();
  int _installments = 1;

  @override
  void dispose() {
    _amount.dispose();
    _desc.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Registrar compra'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _amount,
            keyboardType: TextInputType.number,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'Monto'),
          ),
          const SizedBox(height: AppSpacing.sm),
          TextField(
            controller: _desc,
            decoration:
                const InputDecoration(labelText: 'Descripción (opcional)'),
          ),
          const SizedBox(height: AppSpacing.sm),
          InputDecorator(
            decoration: const InputDecoration(labelText: 'Cuotas'),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<int>(
                isExpanded: true,
                isDense: true,
                value: _installments,
                items: [
                  for (final n in const [1, 3, 6, 12, 18, 24, 36, 48])
                    DropdownMenuItem(
                        value: n,
                        child: Text(n == 1 ? 'Sin cuotas' : '$n cuotas')),
                ],
                onChanged: (v) => setState(() => _installments = v ?? 1),
              ),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancelar')),
        FilledButton(
          onPressed: () {
            final double amount = double.tryParse(_amount.text
                    .replaceAll('.', '')
                    .replaceAll(',', '')) ??
                0;
            if (amount <= 0) return;
            Navigator.of(context).pop(_PurchaseInput(
              amount,
              _desc.text.trim().isEmpty ? null : _desc.text.trim(),
              _installments > 1 ? _installments : null,
            ));
          },
          child: const Text('Guardar'),
        ),
      ],
    );
  }
}

Future<double?> _amountDialog(BuildContext context, String title) {
  final controller = TextEditingController();
  return showDialog<double>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: TextField(
        controller: controller,
        keyboardType: TextInputType.number,
        autofocus: true,
        decoration: const InputDecoration(labelText: 'Monto'),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancelar')),
        FilledButton(
          onPressed: () {
            final double amount = double.tryParse(controller.text
                    .replaceAll('.', '')
                    .replaceAll(',', '')) ??
                0;
            if (amount > 0) Navigator.of(context).pop(amount);
          },
          child: const Text('Guardar'),
        ),
      ],
    ),
  );
}
