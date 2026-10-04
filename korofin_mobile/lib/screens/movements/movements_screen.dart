import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../data/formatters.dart';
import '../../models/movement.dart';
import '../../state/movements/movements_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/movement_tile.dart';
import '../../widgets/nav/app_header.dart';
import '../quick_add/quick_add_sheet.dart';
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

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      body: Column(
        children: [
          AppHeader(
            title: 'Movimientos',
            subtitle: 'Ingresos y gastos',
            onNotificationsTap: () => context.push('/notifications'),
            onProfileTap: () => context.push('/settings'),
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
                TextButton.icon(
                  onPressed: () => context.push('/categories'),
                  style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
                  ),
                  icon: const Icon(Icons.category_outlined, size: 18),
                  label: const Text('Categorías'),
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
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => showQuickAddSheet(context, ref),
        child: const Icon(Icons.add),
      ),
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
  DateTimeRange? _dateFilter;

  // Piso razonable para no permitir fechas disparatadamente antiguas; no hay
  // forma simple de consultar la fecha de creación de la cuenta desde este
  // controller.
  DateTime get _firstSelectableDate =>
      DateTime(DateTime.now().year - 5);

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

  Future<void> _pickDateFilter() async {
    final _DateFilterMode? mode = await showModalBottomSheet<_DateFilterMode>(
      context: context,
      showDragHandle: true,
      builder: (context) => const _DateFilterModeSheet(),
    );
    if (mode == null || !mounted) return;
    switch (mode) {
      case _DateFilterMode.day:
        final DateTime? picked = await showDatePicker(
          context: context,
          initialDate: _dateFilter?.start ?? DateTime.now(),
          firstDate: _firstSelectableDate,
          lastDate: DateTime.now(),
        );
        if (picked != null) {
          setState(() => _dateFilter = DateTimeRange(start: picked, end: picked));
        }
      case _DateFilterMode.range:
        final DateTimeRange? picked = await showDateRangePicker(
          context: context,
          firstDate: _firstSelectableDate,
          lastDate: DateTime.now(),
          initialDateRange: _dateFilter,
        );
        if (picked != null) setState(() => _dateFilter = picked);
    }
  }

  void _clearDateFilter() => setState(() => _dateFilter = null);

  List<Movement> _applyDateFilter(List<Movement> items) {
    final DateTimeRange? range = _dateFilter;
    if (range == null) return items;
    final DateTime start = DateTime(range.start.year, range.start.month, range.start.day);
    final DateTime end = DateTime(range.end.year, range.end.month, range.end.day, 23, 59, 59);
    return items
        .where((m) => !m.date.isBefore(start) && !m.date.isAfter(end))
        .toList(growable: false);
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
    final koro = context.koroColors;

    return Column(
      children: [
        _DateFilterChip(
          range: _dateFilter,
          accent: koro.accent,
          onPick: _pickDateFilter,
          onClear: _clearDateFilter,
        ),
        Expanded(
          child: movements.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => _ErrorView(
              message: error is ApiException
                  ? error.message
                  : 'No se pudieron cargar los movimientos.',
              onRetry: controller.refresh,
            ),
            data: (allItems) {
              final List<Movement> items = _applyDateFilter(allItems);
              if (items.isEmpty) {
                return RefreshIndicator(
                  onRefresh: controller.refresh,
                  child: ListView(
                    children: [
                      const SizedBox(height: AppSpacing.xxxl),
                      EmptyState(
                        icon: Icons.receipt_long_outlined,
                        title: _dateFilter == null
                            ? 'Sin movimientos todavía'
                            : 'Sin movimientos en ese rango',
                        description: _dateFilter == null
                            ? 'Los que registres aparecerán acá.'
                            : 'Probá con otro rango de fechas.',
                      ),
                    ],
                  ).animate(key: ValueKey<DateTimeRange?>(_dateFilter)).fadeIn(
                        duration: 200.ms,
                        curve: Curves.easeOut,
                      ),
                );
              }
              return RefreshIndicator(
                onRefresh: controller.refresh,
                child: ListView.separated(
                  controller: _scroll,
                  padding: const EdgeInsets.fromLTRB(
                      AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, 96),
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
                      )
                          .animate()
                          .fadeIn(duration: 220.ms, curve: Curves.easeOut)
                          .slideY(begin: 0.08, end: 0, curve: Curves.easeOut),
                    );
                  },
                ).animate(key: ValueKey<DateTimeRange?>(_dateFilter)).fadeIn(
                      duration: 200.ms,
                      curve: Curves.easeOut,
                    ),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Dos formas de filtrar movimientos por fecha: un día puntual o un rango.
enum _DateFilterMode { day, range }

/// Bottom sheet chico para elegir el tipo de filtro de fecha antes de abrir
/// el picker estándar correspondiente (`showDatePicker`/`showDateRangePicker`).
class _DateFilterModeSheet extends StatelessWidget {
  const _DateFilterModeSheet();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.today_outlined),
            title: const Text('Un día específico'),
            onTap: () =>
                Navigator.of(context).pop(_DateFilterMode.day),
          ),
          ListTile(
            leading: const Icon(Icons.date_range_outlined),
            title: const Text('Un rango de fechas'),
            onTap: () =>
                Navigator.of(context).pop(_DateFilterMode.range),
          ),
          const SizedBox(height: AppSpacing.sm),
        ],
      ),
    );
  }
}

/// Único punto de entrada para filtrar la lista de movimientos por fecha (un
/// día puntual o un rango). El filtrado es puramente local sobre los items ya
/// cargados.
class _DateFilterChip extends StatelessWidget {
  const _DateFilterChip({
    required this.range,
    required this.accent,
    required this.onPick,
    required this.onClear,
  });

  final DateTimeRange? range;
  final Color accent;
  final VoidCallback onPick;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final bool isSingleDay = range != null && range!.start == range!.end;
    final String label = range == null
        ? 'Filtrar por fecha'
        : isSingleDay
            ? AppFormatters.shortDate(range!.start)
            : '${AppFormatters.shortDate(range!.start)} – ${AppFormatters.shortDate(range!.end)}';
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg, AppSpacing.sm, AppSpacing.lg, 0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(AppRadii.pill),
            onTap: onPick,
            child: Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.md, vertical: AppSpacing.xs),
              decoration: BoxDecoration(
                color: context.koroColors.surfaceElevated,
                borderRadius: BorderRadius.circular(AppRadii.pill),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.calendar_today_outlined,
                      size: 14, color: range == null ? null : accent),
                  const SizedBox(width: AppSpacing.xs),
                  Text(label, style: Theme.of(context).textTheme.labelSmall),
                ],
              ),
            ),
          ),
          if (range != null) ...[
            const SizedBox(width: AppSpacing.xs),
            IconButton(
              visualDensity: VisualDensity.compact,
              tooltip: 'Quitar filtro',
              onPressed: onClear,
              icon: const Icon(Icons.close_rounded, size: 16),
            ),
          ],
        ],
      ),
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
