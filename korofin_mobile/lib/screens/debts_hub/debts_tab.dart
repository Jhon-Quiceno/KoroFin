import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../models/debt.dart';
import '../../state/debts/debts_controller.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/debt_tile.dart';
import 'debt_detail_screen.dart';
import 'new_debt_sheet.dart';

/// Sub-tab "Deudas" del hub de Deudas (pantalla 5), contra `/api/debts`.
class DebtsTab extends ConsumerWidget {
  const DebtsTab({super.key});

  Future<void> _add(BuildContext context, WidgetRef ref) async {
    final DebtFormData? data = await showNewDebtSheet(context);
    if (data == null) return;
    try {
      await ref.read(debtsProvider.notifier).create(
            name: data.name,
            totalAmount: data.totalAmount,
            interestRate: data.interestRate,
            dueDate: data.dueDate,
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
    final AsyncValue<List<Debt>> debts = ref.watch(debtsProvider);

    return Scaffold(
      floatingActionButton: FloatingActionButton.small(
        heroTag: 'add-debt',
        onPressed: () => _add(context, ref),
        child: const Icon(Icons.add),
      ),
      body: debts.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorView(
          message: error is ApiException
              ? error.message
              : 'No se pudieron cargar las deudas.',
          onRetry: () => ref.read(debtsProvider.notifier).refresh(),
        ),
        data: (list) {
          if (list.isEmpty) {
            return RefreshIndicator(
              onRefresh: () => ref.read(debtsProvider.notifier).refresh(),
              child: ListView(
                children: [
                  const SizedBox(height: AppSpacing.xxxl),
                  EmptyState(
                    icon: Icons.account_balance_wallet_outlined,
                    title: 'Sin deudas activas',
                    description: 'Registrá una deuda para hacerle seguimiento.',
                    child: ElevatedButton(
                      onPressed: () => _add(context, ref),
                      child: const Text('Agregar deuda'),
                    ),
                  ),
                ],
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.read(debtsProvider.notifier).refresh(),
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
              itemCount: list.length,
              separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
              itemBuilder: (context, index) => DebtTile(
                debt: list[index],
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => DebtDetailScreen(debtId: list[index].id),
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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: AppSpacing.lg),
              FilledButton.tonal(
                  onPressed: onRetry, child: const Text('Reintentar')),
            ],
          ),
        ),
      );
}
