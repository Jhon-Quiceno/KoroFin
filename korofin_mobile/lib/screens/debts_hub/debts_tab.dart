import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/debt.dart';
import '../../theme/app_spacing.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/debt_tile.dart';
import 'debt_detail_screen.dart';
import 'new_debt_sheet.dart';

/// Sub-tab "Deudas" inside the Deudas hub (screen 5).
class DebtsTab extends StatefulWidget {
  const DebtsTab({super.key});

  @override
  State<DebtsTab> createState() => _DebtsTabState();
}

class _DebtsTabState extends State<DebtsTab> {
  late List<Debt> _debts = List.of(MockData.debts);

  Future<void> _addDebt() async {
    final result = await showNewDebtSheet(context);
    if (result != null) setState(() => _debts = [result, ..._debts]);
  }

  @override
  Widget build(BuildContext context) {
    if (_debts.isEmpty) {
      return EmptyState(
        icon: Icons.account_balance_wallet_outlined,
        title: 'Sin deudas activas',
        description: 'Registrá una deuda para hacerle seguimiento.',
        child: ElevatedButton(onPressed: _addDebt, child: const Text('Agregar deuda')),
      );
    }
    return Stack(
      children: [
        ListView.separated(
          padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
          itemCount: _debts.length,
          separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
          itemBuilder: (context, index) {
            final debt = _debts[index];
            return DebtTile(
              debt: debt,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => DebtDetailScreen(debt: debt)),
              ),
            );
          },
        ),
        Positioned(
          right: AppSpacing.lg,
          bottom: AppSpacing.lg,
          child: FloatingActionButton.small(heroTag: 'add-debt', onPressed: _addDebt, child: const Icon(Icons.add)),
        ),
      ],
    );
  }
}
