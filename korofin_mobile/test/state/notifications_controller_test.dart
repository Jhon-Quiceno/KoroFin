import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/notification_repository.dart';
import 'package:korofin_mobile/models/notification.dart';
import 'package:korofin_mobile/models/page_response.dart';
import 'package:korofin_mobile/state/notifications/notifications_controller.dart';

AppNotification _n(int id, {bool read = false}) => AppNotification(
      id: id,
      kind: NotificationKind.system,
      title: 'T$id',
      message: 'M',
      read: read,
      createdAt: DateTime(2026, 9, id + 1),
    );

class _FakeRepo implements NotificationRepository {
  final List<AppNotification> store = <AppNotification>[
    _n(1),
    _n(2),
    _n(3, read: true),
  ];

  @override
  Future<PageResponse<AppNotification>> list({int page = 0, int size = 30}) async =>
      PageResponse<AppNotification>(
        content: List<AppNotification>.of(store),
        page: 0,
        totalElements: store.length,
        totalPages: 1,
        isLast: true,
      );

  @override
  Future<int> unreadCount() async => store.where((n) => !n.read).length;

  @override
  Future<AppNotification> markAsRead(int id) async {
    final i = store.indexWhere((n) => n.id == id);
    store[i] = store[i].copyWith(read: true);
    return store[i];
  }

  @override
  Future<void> markAllAsRead() async {
    for (var i = 0; i < store.length; i++) {
      store[i] = store[i].copyWith(read: true);
    }
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

ProviderContainer _container(NotificationRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    notificationRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build carga la lista', () async {
    final c = _container(_FakeRepo());
    final list = await c.read(notificationsProvider.future);
    expect(list, hasLength(3));
  });

  test('markAsRead marca una y refresca el contador', () async {
    final c = _container(_FakeRepo());
    await c.read(notificationsProvider.future);
    expect(await c.read(unreadCountProvider.future), 2);

    await c.read(notificationsProvider.notifier).markAsRead(1);

    final list = c.read(notificationsProvider).requireValue;
    expect(list.firstWhere((n) => n.id == 1).read, isTrue);
    expect(await c.read(unreadCountProvider.future), 1);
  });

  test('markAllAsRead marca todas', () async {
    final c = _container(_FakeRepo());
    await c.read(notificationsProvider.future);

    await c.read(notificationsProvider.notifier).markAllAsRead();

    expect(
        c.read(notificationsProvider).requireValue.every((n) => n.read), isTrue);
  });
}
