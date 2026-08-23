enum NotificationType { dueDate, ai, system }

/// A single entry in the "Notificaciones" center.
class NotificationItem {
  const NotificationItem({
    required this.id,
    required this.title,
    required this.description,
    required this.date,
    required this.type,
    this.isRead = false,
  });

  final String id;
  final String title;
  final String description;
  final DateTime date;
  final NotificationType type;
  final bool isRead;

  NotificationItem copyWith({bool? isRead}) => NotificationItem(
        id: id,
        title: title,
        description: description,
        date: date,
        type: type,
        isRead: isRead ?? this.isRead,
      );
}
