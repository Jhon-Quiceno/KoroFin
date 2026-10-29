import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/notifications/local_notifications_gateway.dart';
import '../../core/notifications/nequi_notification_gateway.dart';
import '../../core/notifications/nequi_notification_parser.dart';
import '../../core/notifications/pending_nequi_match.dart';
import '../../core/providers.dart';
import '../../models/movement.dart';

/// `true` si el usuario ya activó el acceso a notificaciones del sistema para
/// la app. Lo lee la pantalla de permisos; se invalida tras volver de
/// Ajustes.
final nequiPermissionStatusProvider = FutureProvider<bool>(
  (ref) => ref.read(nequiNotificationGatewayProvider).isPermissionGranted(),
);

final nequiCaptureControllerProvider =
    NotifierProvider<NequiCaptureController, PendingNequiMatch?>(
  NequiCaptureController.new,
);

/// Escucha el stream de notificaciones del sistema, detecta pagos/envíos de
/// Nequi con [NequiNotificationParser] y deja en [state] el match pendiente
/// de confirmación (si hay alguno). Dispara además una notificación local
/// propia para que el usuario pueda confirmarlo aunque la app esté cerrada.
///
/// Nunca crea el movimiento por su cuenta: eso lo hace quien escuche este
/// estado (ver `ScaffoldWithNav`) mostrando `showTransactionForm` y recién
/// persistiendo cuando el usuario confirma.
class NequiCaptureController extends Notifier<PendingNequiMatch?> {
  StreamSubscription<NequiRawNotification>? _subscription;
  int _nextNotificationId = 0;

  @override
  PendingNequiMatch? build() {
    ref.onDispose(() => _subscription?.cancel());
    unawaited(_init());
    return null;
  }

  NequiNotificationGateway get _notifications =>
      ref.read(nequiNotificationGatewayProvider);

  LocalNotificationsGateway get _localNotifications =>
      ref.read(localNotificationsGatewayProvider);

  Future<void> _init() async {
    await _localNotifications.initialize(onNotificationTap: _onNotificationTap);
    await _localNotifications.requestPermission();

    final String? launchPayload = await _localNotifications.consumeLaunchPayload();
    final PendingNequiMatch? launchMatch = PendingNequiMatch.decode(launchPayload);
    if (launchMatch != null) state = launchMatch;

    _subscription = _notifications.notifications.listen(_onNotification);
  }

  void _onNotificationTap(String? payload) {
    final PendingNequiMatch? match = PendingNequiMatch.decode(payload);
    if (match != null) state = match;
  }

  void _onNotification(NequiRawNotification raw) {
    if (!_looksLikeNequi(raw.packageName)) return;

    final NequiParseResult result = NequiNotificationParser.parse(
      title: raw.title ?? '',
      body: raw.content ?? '',
    );
    final PendingNequiMatch? match = switch (result) {
      NequiIgnored() => null,
      NequiIncomeDetected(amount: final amount) =>
        PendingNequiMatch(type: MovementType.income, amount: amount),
      NequiExpenseDetected(amount: final amount, description: final description) =>
        PendingNequiMatch(type: MovementType.expense, amount: amount, description: description),
    };
    if (match == null) return;

    state = match;
    unawaited(_notifyMatch(match));
  }

  /// `packageName` es best-effort: si el plugin no lo expone (`null`), no se
  /// bloquea nada acá — el gate real es que [NequiNotificationParser] haya
  /// reconocido el texto.
  bool _looksLikeNequi(String? packageName) =>
      packageName == null || packageName.toLowerCase().contains('nequi');

  Future<void> _notifyMatch(PendingNequiMatch match) {
    final String body = switch (match.type) {
      MovementType.income =>
        'Nequi: te llegaron ${match.amount.toStringAsFixed(0)}. Tocá para confirmarlo en KoroFin.',
      MovementType.expense => match.description != null
          ? 'Nequi: pagaste ${match.amount.toStringAsFixed(0)} en ${match.description}. Tocá para confirmarlo en KoroFin.'
          : 'Nequi: pagaste ${match.amount.toStringAsFixed(0)}. Tocá para confirmarlo en KoroFin.',
    };
    return _localNotifications.showActionableNotification(
      id: _nextNotificationId++,
      title: '¿Registrar este movimiento?',
      body: body,
      payload: match.encode(),
    );
  }

  /// Limpia el match pendiente una vez que quien lo consumió ya mostró el
  /// formulario (se haya confirmado o cancelado).
  void dismiss() => state = null;

  Future<bool> requestPermission() => _notifications.requestPermission();
}
