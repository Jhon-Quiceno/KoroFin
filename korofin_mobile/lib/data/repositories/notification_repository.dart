import '../../core/network/api_client.dart';
import '../../models/notification.dart';
import '../../models/page_response.dart';

/// Acceso a `/api/notifications` (notificaciones in-app y preferencias).
class NotificationRepository {
  NotificationRepository(this._client);

  final ApiClient _client;

  Future<PageResponse<AppNotification>> list(
      {int page = 0, int size = 30}) async {
    final response = await _client.get(
      '/api/notifications',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResponse<AppNotification>.fromJson(
      response.data as Map<String, dynamic>,
      AppNotification.fromJson,
    );
  }

  Future<int> unreadCount() async {
    final response = await _client.get('/api/notifications/unread-count');
    return (response.data as num).toInt();
  }

  Future<AppNotification> markAsRead(int id) async {
    final response = await _client.patch('/api/notifications/$id/read');
    return AppNotification.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> markAllAsRead() async {
    await _client.patch('/api/notifications/read-all');
  }

  Future<NotificationPreferences> preferences() async {
    final response = await _client.get('/api/notifications/preferences');
    return NotificationPreferences.fromJson(
        response.data as Map<String, dynamic>);
  }

  Future<NotificationPreferences> updatePreferences(
      NotificationPreferences prefs) async {
    final response = await _client.put(
      '/api/notifications/preferences',
      body: prefs.toJson(),
    );
    return NotificationPreferences.fromJson(
        response.data as Map<String, dynamic>);
  }
}
