import 'dart:io';

import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Notificación local propia de la app ("¿Registrar este movimiento?") y su
/// manejo de permisos/toque. Es una interfaz para poder inyectar un fake en
/// los tests: el plugin nativo no está disponible en `flutter test`.
abstract interface class LocalNotificationsGateway {
  /// Inicializa el plugin y registra [onNotificationTap] para cuando el
  /// usuario toca la notificación mientras la app sigue viva (primer o
  /// segundo plano).
  Future<void> initialize({required void Function(String? payload) onNotificationTap});

  /// Si la app se abrió justo porque el usuario tocó una notificación
  /// nuestra, devuelve su payload; `null` en cualquier otro caso (arranque
  /// normal, o la notificación ya fue consumida).
  Future<String?> consumeLaunchPayload();

  Future<void> showActionableNotification({
    required int id,
    required String title,
    required String body,
    required String payload,
  });

  /// Pide el permiso de notificaciones (Android 13+); `null` si no aplica.
  Future<bool?> requestPermission();
}

/// Implementación real sobre `flutter_local_notifications`. Android-only:
/// en cualquier otra plataforma es un no-op, porque la captura de Nequi
/// solo existe en Android.
class FlutterLocalNotificationsGateway implements LocalNotificationsGateway {
  FlutterLocalNotificationsGateway({FlutterLocalNotificationsPlugin? plugin})
      : _plugin = plugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;

  static const String _channelId = 'nequi_capture';
  static const String _channelName = 'Captura de pagos (Nequi)';
  static const String _channelDescription =
      'Avisa cuando se detecta un posible gasto o ingreso desde una notificación de Nequi, para confirmarlo.';

  @override
  Future<void> initialize({required void Function(String? payload) onNotificationTap}) async {
    if (!Platform.isAndroid) return;
    await _plugin.initialize(
      const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      ),
      onDidReceiveNotificationResponse: (response) => onNotificationTap(response.payload),
    );
  }

  @override
  Future<String?> consumeLaunchPayload() async {
    if (!Platform.isAndroid) return null;
    final NotificationAppLaunchDetails? details = await _plugin.getNotificationAppLaunchDetails();
    if (details == null || !details.didNotificationLaunchApp) return null;
    return details.notificationResponse?.payload;
  }

  @override
  Future<void> showActionableNotification({
    required int id,
    required String title,
    required String body,
    required String payload,
  }) async {
    if (!Platform.isAndroid) return;
    await _plugin.show(
      id,
      title,
      body,
      const NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          _channelName,
          channelDescription: _channelDescription,
          importance: Importance.high,
          priority: Priority.high,
        ),
      ),
      payload: payload,
    );
  }

  @override
  Future<bool?> requestPermission() async {
    if (!Platform.isAndroid) return null;
    return _plugin
        .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();
  }
}
