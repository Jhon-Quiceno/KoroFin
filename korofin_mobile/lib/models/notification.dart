import 'package:flutter/material.dart';

/// Tipo de notificación. En el backend es `NotificationType`.
enum NotificationKind {
  paymentReminder('PAYMENT_REMINDER', Icons.schedule),
  overspendAlert('OVERSPEND_ALERT', Icons.trending_up),
  weeklySummary('WEEKLY_SUMMARY', Icons.summarize_outlined),
  inactivityReminder('INACTIVITY_REMINDER', Icons.notifications_active_outlined),
  monthEndPrediction('MONTH_END_PREDICTION', Icons.auto_awesome),
  cardCycleClose('CARD_CYCLE_CLOSE', Icons.credit_card),
  system('SYSTEM', Icons.notifications_outlined);

  const NotificationKind(this.wire, this.icon);
  final String wire;
  final IconData icon;

  static NotificationKind fromWire(String? v) => NotificationKind.values
      .firstWhere((k) => k.wire == v, orElse: () => NotificationKind.system);
}

/// Una notificación in-app: `GET /api/notifications` (`NotificationResponse`).
/// Se llama `AppNotification` para no chocar con `Notification` de Flutter.
class AppNotification {
  const AppNotification({
    required this.id,
    required this.kind,
    required this.title,
    required this.message,
    required this.read,
    required this.createdAt,
  });

  final int id;
  final NotificationKind kind;
  final String title;
  final String message;
  final bool read;
  final DateTime createdAt;

  AppNotification copyWith({bool? read}) => AppNotification(
        id: id,
        kind: kind,
        title: title,
        message: message,
        read: read ?? this.read,
        createdAt: createdAt,
      );

  factory AppNotification.fromJson(Map<String, dynamic> json) =>
      AppNotification(
        id: (json['id'] as num).toInt(),
        kind: NotificationKind.fromWire(json['type'] as String?),
        title: json['title'] as String? ?? '',
        message: json['message'] as String? ?? '',
        read: json['read'] as bool? ?? false,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

/// Preferencias de notificación: `GET/PUT /api/notifications/preferences`.
class NotificationPreferences {
  const NotificationPreferences({
    required this.paymentReminders,
    required this.overspendAlerts,
    required this.weeklySummary,
    required this.inactivityReminders,
    required this.cardCycleClose,
    required this.emailEnabled,
  });

  final bool paymentReminders;
  final bool overspendAlerts;
  final bool weeklySummary;
  final bool inactivityReminders;
  final bool cardCycleClose;
  final bool emailEnabled;

  NotificationPreferences copyWith({
    bool? paymentReminders,
    bool? overspendAlerts,
    bool? weeklySummary,
    bool? inactivityReminders,
    bool? cardCycleClose,
    bool? emailEnabled,
  }) =>
      NotificationPreferences(
        paymentReminders: paymentReminders ?? this.paymentReminders,
        overspendAlerts: overspendAlerts ?? this.overspendAlerts,
        weeklySummary: weeklySummary ?? this.weeklySummary,
        inactivityReminders: inactivityReminders ?? this.inactivityReminders,
        cardCycleClose: cardCycleClose ?? this.cardCycleClose,
        emailEnabled: emailEnabled ?? this.emailEnabled,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'paymentReminders': paymentReminders,
        'overspendAlerts': overspendAlerts,
        'weeklySummary': weeklySummary,
        'inactivityReminders': inactivityReminders,
        'cardCycleClose': cardCycleClose,
        'emailEnabled': emailEnabled,
      };

  factory NotificationPreferences.fromJson(Map<String, dynamic> json) =>
      NotificationPreferences(
        paymentReminders: json['paymentReminders'] as bool? ?? true,
        overspendAlerts: json['overspendAlerts'] as bool? ?? true,
        weeklySummary: json['weeklySummary'] as bool? ?? true,
        inactivityReminders: json['inactivityReminders'] as bool? ?? true,
        cardCycleClose: json['cardCycleClose'] as bool? ?? true,
        emailEnabled: json['emailEnabled'] as bool? ?? false,
      );
}
