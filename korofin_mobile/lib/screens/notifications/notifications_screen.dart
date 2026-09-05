import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../models/notification.dart';
import '../../state/notifications/notifications_controller.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/notification_tile.dart';

/// Pantalla 16 — Notificaciones: tabs "Todas / No leídas" contra
/// `/api/notifications`, cada ítem deslizable para marcar como leído.
class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() =>
      _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this);

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<List<AppNotification>> notifications =
        ref.watch(notificationsProvider);
    final notifier = ref.read(notificationsProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notificaciones'),
        actions: [
          if (notifications.valueOrNull?.any((n) => !n.read) ?? false)
            TextButton(
              onPressed: () => notifier.markAllAsRead(),
              child: const Text('Marcar todas'),
            ),
        ],
        bottom: TabBar(
          controller: _tabs,
          tabs: const [Tab(text: 'Todas'), Tab(text: 'No leídas')],
        ),
      ),
      body: notifications.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Text(error is ApiException
              ? error.message
              : 'No se pudieron cargar las notificaciones.'),
        ),
        data: (all) {
          final unread = all.where((n) => !n.read).toList(growable: false);
          return TabBarView(
            controller: _tabs,
            children: [
              _List(items: all, onRead: notifier.markAsRead, onRefresh: notifier.refresh),
              _List(items: unread, onRead: notifier.markAsRead, onRefresh: notifier.refresh),
            ],
          );
        },
      ),
    );
  }
}

class _List extends StatelessWidget {
  const _List({
    required this.items,
    required this.onRead,
    required this.onRefresh,
  });

  final List<AppNotification> items;
  final void Function(int id) onRead;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          children: const [
            SizedBox(height: 120),
            EmptyState(
              icon: Icons.notifications_none_rounded,
              title: 'Estás al día',
              description: 'No tenés notificaciones pendientes.',
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        itemCount: items.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) => NotificationTile(
          notification: items[index],
          onRead: () => onRead(items[index].id),
        ),
      ),
    );
  }
}
