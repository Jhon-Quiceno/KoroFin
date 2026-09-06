import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../data/formatters.dart';
import '../../models/debt.dart';
import '../../state/debts/debts_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../../widgets/cards/section_card.dart';
import 'new_debt_sheet.dart';

/// Detalle de una deuda con su historial de abonos y cargos y las acciones de
/// registrar abono / cargo. Recarga la deuda del backend al abrirse.
class DebtDetailScreen extends ConsumerWidget {
  const DebtDetailScreen({super.key, required this.debtId});

  final int debtId;

  Future<void> _guard(
      BuildContext context, WidgetRef ref, Future<void> Function() action) async {
    try {
      await action();
      ref.invalidate(debtDetailProvider(debtId));
      ref.invalidate(debtsProvider);
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _addPayment(BuildContext context, WidgetRef ref) async {
    final ({double amount, String? text})? r = await _amountDialog(
      context,
      title: 'Registrar abono',
      textLabel: 'Nota (opcional)',
    );
    if (r == null || !context.mounted) return;
    await _guard(
      context,
      ref,
      () => ref
          .read(debtRepositoryProvider)
          .addPayment(debtId, amount: r.amount, note: r.text),
    );
  }

  Future<void> _addCharge(BuildContext context, WidgetRef ref) async {
    final ({double amount, String? text})? r = await _amountDialog(
      context,
      title: 'Registrar cargo',
      textLabel: 'Descripción (opcional)',
    );
    if (r == null || !context.mounted) return;
    await _guard(
      context,
      ref,
      () => ref
          .read(debtRepositoryProvider)
          .addCharge(debtId, amount: r.amount, description: r.text),
    );
  }

  Future<void> _edit(BuildContext context, WidgetRef ref, Debt debt) async {
    final DebtFormData? data =
        await showNewDebtSheet(context, initial: debt);
    if (data == null || !context.mounted) return;
    await _guard(
      context,
      ref,
      () => ref.read(debtsProvider.notifier).edit(
            debtId,
            name: data.name,
            interestRate: data.interestRate,
            dueDate: data.dueDate,
          ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final bool ok = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('¿Borrar la deuda?'),
            content: const Text('Se borra junto con su historial.'),
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
      await ref.read(debtsProvider.notifier).remove(debtId);
      if (context.mounted) Navigator.of(context).pop();
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<DebtDetail> detail =
        ref.watch(debtDetailProvider(debtId));
    final koro = context.koroColors;

    return Scaffold(
      appBar: AppBar(
        title: Text(detail.valueOrNull?.debt.name ?? 'Deuda'),
        actions: [
          if (detail.hasValue) ...[
            IconButton(
              icon: const Icon(Icons.edit_outlined),
              onPressed: () => _edit(context, ref, detail.value!.debt),
            ),
            IconButton(
              icon: const Icon(Icons.delete_outline),
              onPressed: () => _delete(context, ref),
            ),
          ],
        ],
      ),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Text(error is ApiException
              ? error.message
              : 'No se pudo cargar la deuda.'),
        ),
        data: (data) {
          final Debt debt = data.debt;
          final List<_HistoryEntry> history = <_HistoryEntry>[
            for (final p in data.payments)
              _HistoryEntry(
                  date: p.paymentDate,
                  amount: p.amount,
                  isPayment: true,
                  text: p.note),
            for (final c in data.charges)
              _HistoryEntry(
                  date: c.chargeDate,
                  amount: c.amount,
                  isPayment: false,
                  text: c.description),
          ]..sort((a, b) => b.date.compareTo(a.date));

          return ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(AppRadii.pill),
                child: LinearProgressIndicator(
                  value: debt.progress,
                  minHeight: 10,
                  backgroundColor: koro.surfaceElevated,
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Pagado ${AppFormatters.currency(debt.paidAmount)}',
                      style: Theme.of(context).textTheme.bodyMedium),
                  Text('Total ${AppFormatters.currency(debt.totalAmount)}',
                      style: Theme.of(context).textTheme.bodyMedium),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              SectionCard(
                title: 'Resumen',
                child: Column(
                  children: [
                    _InfoRow(
                        label: 'Restante',
                        value: AppFormatters.currency(debt.remainingAmount)),
                    if (debt.interestRate != null)
                      _InfoRow(
                          label: 'Tasa de interés',
                          value:
                              '${debt.interestRate!.toStringAsFixed(1)}%'),
                    if (debt.dueDate != null)
                      _InfoRow(
                          label: 'Vencimiento',
                          value: AppFormatters.longDate(debt.dueDate!)),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.tonalIcon(
                      onPressed: debt.isSettled
                          ? null
                          : () => _addPayment(context, ref),
                      icon: const Icon(Icons.remove),
                      label: const Text('Abono'),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: FilledButton.tonalIcon(
                      onPressed: () => _addCharge(context, ref),
                      icon: const Icon(Icons.add),
                      label: const Text('Cargo'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              Text('Historial',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: AppSpacing.sm),
              if (history.isEmpty)
                const Text('Sin movimientos todavía.')
              else
                for (final e in history)
                  Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: (e.isPayment ? koro.success : koro.warning)
                            .withValues(alpha: 0.14),
                        child: Icon(
                          e.isPayment ? Icons.south_west : Icons.north_east,
                          color: e.isPayment ? koro.success : koro.warning,
                          size: 18,
                        ),
                      ),
                      title: Text(
                        '${e.isPayment ? '-' : '+'}${AppFormatters.currency(e.amount)}',
                      ),
                      subtitle: Text(
                        [
                          AppFormatters.longDate(e.date),
                          if (e.text != null && e.text!.isNotEmpty) e.text!,
                        ].join(' · '),
                      ),
                    ),
                  ),
            ],
          );
        },
      ),
    );
  }
}

class _HistoryEntry {
  _HistoryEntry({
    required this.date,
    required this.amount,
    required this.isPayment,
    this.text,
  });

  final DateTime date;
  final double amount;
  final bool isPayment;
  final String? text;
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label, style: Theme.of(context).textTheme.bodyMedium),
            Text(value,
                style: AppTextStyles.bodyMediumMedium(
                    context.koroColors.foreground)),
          ],
        ),
      );
}

/// Diálogo simple de monto + texto opcional.
Future<({double amount, String? text})?> _amountDialog(
  BuildContext context, {
  required String title,
  required String textLabel,
}) {
  final amountController = TextEditingController();
  final textController = TextEditingController();
  return showDialog<({double amount, String? text})>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: amountController,
            keyboardType: TextInputType.number,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'Monto'),
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: textController,
            decoration: InputDecoration(labelText: textLabel),
          ),
        ],
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancelar')),
        FilledButton(
          onPressed: () {
            final double amount = double.tryParse(amountController.text
                    .replaceAll('.', '')
                    .replaceAll(',', '')) ??
                0;
            if (amount <= 0) return;
            Navigator.of(context).pop((
              amount: amount,
              text: textController.text.trim().isEmpty
                  ? null
                  : textController.text.trim(),
            ));
          },
          child: const Text('Guardar'),
        ),
      ],
    ),
  );
}
