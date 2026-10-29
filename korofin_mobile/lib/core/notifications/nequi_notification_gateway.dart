import 'dart:io';

import 'package:notification_listener_service/notification_listener_service.dart';

/// Notificación cruda de Android, recortada a los únicos campos que necesita
/// el parser de Nequi (`NequiNotificationParser`).
class NequiRawNotification {
  const NequiRawNotification({this.packageName, this.title, this.content});

  final String? packageName;
  final String? title;
  final String? content;
}

/// Acceso al listener de notificaciones del sistema. Es una interfaz para
/// poder inyectar un fake en los tests: el plugin nativo solo existe en
/// Android y no está disponible en `flutter test`.
abstract interface class NequiNotificationGateway {
  Stream<NequiRawNotification> get notifications;

  Future<bool> isPermissionGranted();

  /// Abre los ajustes del sistema para que el usuario active el acceso a
  /// notificaciones; devuelve `true` una vez concedido.
  Future<bool> requestPermission();
}

/// Implementación real sobre `notification_listener_service`. Android-only:
/// en cualquier otra plataforma se comporta como si nunca hubiera permiso ni
/// notificaciones (sin lanzar), porque el plugin solo opera en Android.
class AndroidNequiNotificationGateway implements NequiNotificationGateway {
  @override
  Stream<NequiRawNotification> get notifications {
    if (!Platform.isAndroid) return const Stream<NequiRawNotification>.empty();
    return NotificationListenerService.notificationsStream.map(
      (event) => NequiRawNotification(
        packageName: event.packageName,
        title: event.title,
        content: event.content,
      ),
    );
  }

  @override
  Future<bool> isPermissionGranted() {
    if (!Platform.isAndroid) return Future<bool>.value(false);
    return NotificationListenerService.isPermissionGranted();
  }

  @override
  Future<bool> requestPermission() {
    if (!Platform.isAndroid) return Future<bool>.value(false);
    return NotificationListenerService.requestPermission();
  }
}
