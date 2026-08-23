import 'package:flutter/material.dart';

import '../../data/mock_data.dart';
import '../../models/notification_item.dart';
import '../../widgets/common/empty_state.dart';
import '../../widgets/list_items/notification_tile.dart';

/// Screen 16 — Notificaciones: tabs "Todas/No leídas", each item swipeable
/// to mark as read.
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabController = TabController(length: 2, vsync: this);
  late List<NotificationItem> _notifications = List.of(MockData.notifications);

  void _markRead(String id) {
    setState(() {
      _notifications = [
        for (final n in _notifications) if (n.id == id) n.copyWith(isRead: true) else n,
      ];
    });
  }

  @override
  Widget build(BuildContext context) {
    final unread = _notifications.where((n) => !n.isRead).toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notificaciones'),
        bottom: TabBar(
          controller: _tabController,
          tabs: [Tab(text: 'Todas (${_notifications.length})'), Tab(text: 'No leídas (${unread.length})')],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _NotificationList(items: _notifications, onRead: _markRead),
          _NotificationList(items: unread, onRead: _markRead),
        ],
      ),
    );
  }
}

class _NotificationList extends StatelessWidget {
  const _NotificationList({required this.items, required this.onRead});

  final List<NotificationItem> items;
  final ValueChanged<String> onRead;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const EmptyState(
        icon: Icons.notifications_none_rounded,
        title: 'Estás al día',
        description: 'No tenés notificaciones pendientes.',
      );
    }
    return ListView.separated(
      itemCount: items.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) => NotificationTile(notification: items[index], onRead: () => onRead(items[index].id)),
    );
  }
}
