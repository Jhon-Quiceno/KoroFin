import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/notification_repository.dart';
import 'package:korofin_mobile/models/notification.dart';

import '../support/capturing_adapter.dart';

NotificationRepository _repo(CapturingAdapter a) => NotificationRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  test('AppNotification.fromJson mapea el tipo y read', () {
    final n = AppNotification.fromJson(<String, dynamic>{
      'id': 5,
      'type': 'PAYMENT_REMINDER',
      'title': 'Pago próximo',
      'message': 'Netflix vence mañana',
      'read': false,
      'readAt': null,
      'createdAt': '2026-09-05T10:00:00Z',
    });
    expect(n.kind, NotificationKind.paymentReminder);
    expect(n.read, isFalse);
  });

  test('unreadCount parsea un número plano', () async {
    final a = CapturingAdapter(body: 7);
    final count = await _repo(a).unreadCount();
    expect(a.lastRequest.path, '/api/notifications/unread-count');
    expect(count, 7);
  });

  test('markAsRead hace PATCH a /{id}/read', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'id': 5,
      'type': 'SYSTEM',
      'title': 't',
      'message': 'm',
      'read': true,
      'readAt': '2026-09-05T11:00:00Z',
      'createdAt': '2026-09-05T10:00:00Z',
    });
    final n = await _repo(a).markAsRead(5);
    expect(a.lastRequest.method, 'PATCH');
    expect(a.lastRequest.path, '/api/notifications/5/read');
    expect(n.read, isTrue);
  });

  test('preferences round-trip (fromJson + toJson con las 6 claves)', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'paymentReminders': true,
      'overspendAlerts': false,
      'weeklySummary': true,
      'inactivityReminders': true,
      'cardCycleClose': false,
      'emailEnabled': true,
    });
    final p = await _repo(a).preferences();
    expect(p.overspendAlerts, isFalse);
    expect(p.emailEnabled, isTrue);

    final a2 = CapturingAdapter(body: <String, dynamic>{
      'paymentReminders': true,
      'overspendAlerts': true,
      'weeklySummary': true,
      'inactivityReminders': true,
      'cardCycleClose': true,
      'emailEnabled': true,
    });
    await _repo(a2).updatePreferences(p.copyWith(overspendAlerts: true));
    final body = a2.lastRequest.data as Map<String, dynamic>;
    expect(body.keys, containsAll(<String>[
      'paymentReminders',
      'overspendAlerts',
      'weeklySummary',
      'inactivityReminders',
      'cardCycleClose',
      'emailEnabled',
    ]));
    expect(body['overspendAlerts'], isTrue);
  });
}
