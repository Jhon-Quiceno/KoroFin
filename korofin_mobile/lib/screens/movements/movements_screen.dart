import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../models/movement.dart';
import '../../state/movements/movements_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/movement_tile.dart';
import '../../widgets/nav/app_header.dart';
import 'transaction_form_sheet.dart';

/// Aloja Gastos (pantalla 4) e Ingresos (pantalla 7) como tabs del destino
/// "Movimientos" del bottom-nav. Cada tab lista contra `/api/expenses` o
/// `/api/incomes` con scroll infinito y pull-to-refresh.
class MovementsScreen extends ConsumerStatefulWidget {
  const MovementsScreen({super.key});

  @override
  ConsumerState<MovementsScreen> createState() => _MovementsScreenState();
}

class _MovementsScreenState extends ConsumerState<MovementsScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this)
    ..addListener(() => setState(() {}));

  MovementType get _currentType =>
      _tabs.index == 0 ? MovementType.expense : MovementType.income;

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _create() async {
    final MovementFormResult? result =
        await showTransactionForm(context, type: _currentType);
    if (result == null) return;
    await _run(() =>
        ref.read(movementsProvider(result.type).notifier).add(result.draft));
  }

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
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
                  controller: _tabs,
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
                onPressed: _create,
                icon: Icon(Icons.add_circle, color: koro.accent),
              ),
            ],
          ),
        ),
        Expanded(
          child: TabBarView(
            controller: _tabs,
            children: const [
              _MovementList(type: MovementType.expense),
              _MovementList(type: MovementType.income),
            ],
          ),
        ),
      ],
    );
  }
}

class _MovementList extends ConsumerStatefulWidget {
  const _MovementList({required this.type});

  final MovementType type;

  @override
  ConsumerState<_MovementList> createState() => _MovementListState();
}

class _MovementListState extends ConsumerState<_MovementList> {
  final ScrollController _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scroll.position.pixels >=
        _scroll.position.maxScrollExtent - 400) {
      ref.read(movementsProvider(widget.type).notifier).loadMore();
    }
  }

  Future<void> _edit(Movement movement) async {
    final MovementFormResult? result = await showTransactionForm(
      context,
      type: widget.type,
      initial: movement,
    );
    if (result == null) return;
    await _guard(() => ref
        .read(movementsProvider(widget.type).notifier)
        .edit(movement.id, result.draft));
  }

  Future<void> _delete(Movement movement) async {
    await _guard(() =>
        ref.read(movementsProvider(widget.type).notifier).remove(movement.id));
  }

  Future<void> _guard(Future<void> Function() action) async {
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<List<Movement>> movements =
        ref.watch(movementsProvider(widget.type));
    final controller = ref.read(movementsProvider(widget.type).notifier);

    return movements.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => _ErrorView(
        message: error is ApiException
            ? error.message
            : 'No se pudieron cargar los movimientos.',
        onRetry: controller.refresh,
      ),
      data: (items) {
        if (items.isEmpty) {
          return RefreshIndicator(
            onRefresh: controller.refresh,
            child: ListView(
              children: const [
                SizedBox(height: AppSpacing.xxxl),
                EmptyState(
                  icon: Icons.receipt_long_outlined,
                  title: 'Sin movimientos todavía',
                  description: 'Los que registres aparecerán acá.',
                ),
              ],
            ),
          );
        }
        return RefreshIndicator(
          onRefresh: controller.refresh,
          child: ListView.separated(
            controller: _scroll,
            padding: const EdgeInsets.all(AppSpacing.lg),
            itemCount: items.length + (controller.hasMore ? 1 : 0),
            separatorBuilder: (_, _) => const SizedBox(height: 2),
            itemBuilder: (context, index) {
              if (index >= items.length) {
                return const Padding(
                  padding: EdgeInsets.all(AppSpacing.lg),
                  child: Center(child: CircularProgressIndicator()),
                );
              }
              final Movement movement = items[index];
              return Dismissible(
                key: ValueKey<int>(movement.id),
                direction: DismissDirection.endToStart,
                background: Container(
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.only(right: AppSpacing.lg),
                  color: Theme.of(context).colorScheme.error.withValues(alpha: 0.15),
                  child: Icon(Icons.delete_outline,
                      color: Theme.of(context).colorScheme.error),
                ),
                confirmDismiss: (_) => showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('¿Borrar el movimiento?'),
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
                onDismissed: (_) => _delete(movement),
                child: MovementTile(
                  movement: movement,
                  onTap: () => _edit(movement),
                ),
              );
            },
          ),
        );
      },
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
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
}
