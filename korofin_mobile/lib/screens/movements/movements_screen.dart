import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/mock_data.dart';
import '../../models/transaction.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/transaction_tile.dart';
import '../../widgets/nav/app_header.dart';
import 'transaction_form_sheet.dart';

/// Hosts screens 4 (Gastos) and 7 (Ingresos) as tabs of the "Movimientos"
/// bottom-nav destination, plus header actions to import statements and
/// manage categories.
class MovementsScreen extends StatefulWidget {
  const MovementsScreen({super.key});

  @override
  State<MovementsScreen> createState() => _MovementsScreenState();
}

class _MovementsScreenState extends State<MovementsScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabController = TabController(length: 2, vsync: this);
  late List<AppTransaction> _expenses = List.of(MockData.expenses);
  late List<AppTransaction> _incomes = List.of(MockData.incomes);

  bool get _isExpenseTab => _tabController.index == 0;

  Future<void> _openForm({AppTransaction? initial}) async {
    final type = _isExpenseTab ? TransactionType.expense : TransactionType.income;
    final result = await showTransactionForm(context, type: type, initial: initial);
    if (result == null) return;
    setState(() {
      if (type == TransactionType.expense) {
        _expenses = initial == null
            ? [result, ..._expenses]
            : [for (final t in _expenses) if (t.id == initial.id) result else t];
      } else {
        _incomes = initial == null
            ? [result, ..._incomes]
            : [for (final t in _incomes) if (t.id == initial.id) result else t];
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Column(
      children: [
        AppHeader(
          title: 'Movimientos',
          subtitle: 'Ingresos y gastos',
          onNotificationsTap: () => context.push('/notifications'),
          onProfileTap: () => context.push('/settings'),
          onSettingsTap: () => context.push('/settings'),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          child: Row(
            children: [
              Expanded(
                child: TabBar(
                  controller: _tabController,
                  onTap: (_) => setState(() {}),
                  labelColor: koro.accent,
                  unselectedLabelColor: koro.mutedForeground,
                  indicatorColor: koro.accent,
                  tabs: const [Tab(text: 'Gastos'), Tab(text: 'Ingresos')],
                ),
              ),
              IconButton(
                tooltip: 'Importar extracto',
                onPressed: () => context.push('/import-statement'),
                icon: const Icon(Icons.upload_file_outlined),
              ),
              IconButton(
                tooltip: 'Categorías',
                onPressed: () => context.push('/categories'),
                icon: const Icon(Icons.category_outlined),
              ),
              IconButton(
                tooltip: 'Agregar',
                onPressed: () => _openForm(),
                icon: Icon(Icons.add_circle, color: koro.accent),
              ),
            ],
          ),
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _MovementsList(items: _expenses, onTapItem: (t) => _openForm(initial: t)),
              _MovementsList(items: _incomes, onTapItem: (t) => _openForm(initial: t)),
            ],
          ),
        ),
      ],
    );
  }
}

class _MovementsList extends StatelessWidget {
  const _MovementsList({required this.items, required this.onTapItem});

  final List<AppTransaction> items;
  final ValueChanged<AppTransaction> onTapItem;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const EmptyState(
        icon: Icons.receipt_long_outlined,
        title: 'Sin movimientos todavía',
        description: 'Los que registres aparecerán acá.',
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.all(AppSpacing.lg),
      itemCount: items.length,
      separatorBuilder: (_, _) => const SizedBox(height: 2),
      itemBuilder: (context, index) => TransactionTile(transaction: items[index], onTap: () => onTapItem(items[index])),
    );
  }
}
