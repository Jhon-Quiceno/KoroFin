import 'dart:io';

import 'package:flutter/services.dart';
import 'package:notification_listener_service/notification_event.dart';
import 'package:notification_listener_service/notification_listener_service.dart';

/// Notificación cruda de Android, recortada a los únicos campos que necesita
/// la captura de Nequi.
class NequiRawNotification {
  const NequiRawNotification({
    this.id,
    this.packageName,
    this.title,
    this.content,
    this.removed = false,
  });

  /// Id que la app de origen le dio a la notificación.
  final int? id;
  final String? packageName;
  final String? title;
  final String? content;

  /// `true` cuando el evento avisa que la notificación se quitó de la bandeja
  /// (el usuario la descartó), no que llegó una nueva.
  final bool removed;
}

/// Acceso al listener de notificaciones del sistema. Es una interfaz para
/// poder inyectar un fake en los tests: el plugin nativo solo existe en
/// Android y no está disponible en `flutter test`.
abstract interface class NequiNotificationGateway {
  /// Notificaciones que van llegando mientras la app está viva.
  Stream<NequiRawNotification> get notifications;

  /// Notificaciones que siguen en la bandeja del sistema. Cubre las que
  /// llegaron con la app cerrada, cuando el stream no estaba escuchando.
  Future<List<NequiRawNotification>> activeNotifications();

  Future<bool> isPermissionGranted();

  /// Abre los ajustes del sistema para que el usuario active el acceso a
  /// notificaciones. Devuelve el estado del permiso en ese momento, que
  /// normalmente todavía es `false`: el usuario lo concede después, en
  /// Ajustes.
  Future<bool> requestPermission();

  /// Abre "Información de la app" del sistema. Ahí se habilita la opción
  /// "Permitir ajustes restringidos", necesaria en Android 13+ cuando la app
  /// se instaló por APK y el acceso a notificaciones aparece bloqueado.
  Future<void> openAppDetails();
}

/// Implementación real sobre `notification_listener_service`. Android-only:
/// en cualquier otra plataforma se comporta como si nunca hubiera permiso ni
/// notificaciones (sin lanzar), porque el plugin solo opera en Android.
class AndroidNequiNotificationGateway implements NequiNotificationGateway {
  @override
  Stream<NequiRawNotification> get notifications {
    if (!Platform.isAndroid) return const Stream<NequiRawNotification>.empty();
    return NotificationListenerService.notificationsStream.map(_toRaw);
  }

  @override
  Future<List<NequiRawNotification>> activeNotifications() async {
    if (!Platform.isAndroid) return const <NequiRawNotification>[];
    try {
      if (!await NotificationListenerService.isPermissionGranted()) {
        return const <NequiRawNotification>[];
      }
      final List<ServiceNotificationEvent> events =
          await NotificationListenerService.getActiveNotifications();
      return events.map(_toRaw).toList();
    } on PlatformException {
      // El servicio todavía no está conectado: se reintenta al volver a la app.
      return const <NequiRawNotification>[];
    }
  }

  static NequiRawNotification _toRaw(ServiceNotificationEvent event) =>
      NequiRawNotification(
        id: event.id,
        packageName: event.packageName,
        title: event.title,
        content: event.content,
        removed: event.hasRemoved ?? false,
      );

  @override
  Future<bool> isPermissionGranted() {
    if (!Platform.isAndroid) return Future<bool>.value(false);
    return NotificationListenerService.isPermissionGranted();
  }

  // No se usa `NotificationListenerService.requestPermission()`: el plugin
  // responde dos veces a la misma llamada (una al abrir Ajustes y otra en
  // `onActivityResult`), y la segunda respuesta lanza "Reply already
  // submitted" y cierra la app al volver de Ajustes. Se abre la pantalla con
  // el canal propio, sin esperar un resultado; el estado se vuelve a leer
  // cuando la app se reanuda.
  @override
  Future<bool> requestPermission() async {
    if (!Platform.isAndroid) return false;
    await _appSettings.invokeMethod<bool>('openNotificationListenerSettings');
    return NotificationListenerService.isPermissionGranted();
  }

  static const MethodChannel _appSettings = MethodChannel('korofin/app_settings');

  @override
  Future<void> openAppDetails() async {
    if (!Platform.isAndroid) return;
    await _appSettings.invokeMethod<bool>('openAppDetails');
  }
}
