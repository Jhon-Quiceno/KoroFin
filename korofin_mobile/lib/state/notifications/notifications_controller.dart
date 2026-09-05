import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/notification_repository.dart';
import '../../models/notification.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>(
  (ref) => NotificationRepository(ref.read(apiClientProvider)),
);

final notificationsProvider =
    AsyncNotifierProvider<NotificationsController, List<AppNotification>>(
  NotificationsController.new,
);

class NotificationsController extends AsyncNotifier<List<AppNotification>> {
  NotificationRepository get _repo => ref.read(notificationRepositoryProvider);

  @override
  Future<List<AppNotification>> build() async => (await _repo.list()).content;

  List<AppNotification> get _current =>
      state.valueOrNull ?? const <AppNotification>[];

  Future<void> refresh() async {
    state = const AsyncValue<List<AppNotification>>.loading();
    state = await AsyncValue.guard(() async => (await _repo.list()).content);
    ref.invalidate(unreadCountProvider);
  }

  Future<void> markAsRead(int id) async {
    await _repo.markAsRead(id);
    state = AsyncValue<List<AppNotification>>.data(<AppNotification>[
      for (final AppNotification n in _current)
        if (n.id == id) n.copyWith(read: true) else n,
    ]);
    ref.invalidate(unreadCountProvider);
  }

  Future<void> markAllAsRead() async {
    await _repo.markAllAsRead();
    state = AsyncValue<List<AppNotification>>.data(
      _current.map((n) => n.copyWith(read: true)).toList(growable: false),
    );
    ref.invalidate(unreadCountProvider);
  }
}

/// Contador de no leídas para el badge de la campana. Se refresca cuando el
/// controlador marca notificaciones como leídas.
final unreadCountProvider = FutureProvider<int>(
  (ref) => ref.read(notificationRepositoryProvider).unreadCount(),
);

/// Preferencias de notificación (usadas en Configuración).
final notificationPreferencesProvider = AsyncNotifierProvider<
    NotificationPreferencesController, NotificationPreferences>(
  NotificationPreferencesController.new,
);

class NotificationPreferencesController
    extends AsyncNotifier<NotificationPreferences> {
  NotificationRepository get _repo => ref.read(notificationRepositoryProvider);

  @override
  Future<NotificationPreferences> build() => _repo.preferences();

  Future<void> save(NotificationPreferences prefs) async {
    state = AsyncValue<NotificationPreferences>.data(prefs); // optimista
    state = await AsyncValue.guard(() => _repo.updatePreferences(prefs));
  }
}
