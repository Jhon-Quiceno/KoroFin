import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/notifications/local_notifications_gateway.dart';
import '../../core/notifications/nequi_notification_gateway.dart';
import '../../core/notifications/nequi_notification_parser.dart';
import '../../core/notifications/pending_nequi_match.dart';
import '../../core/providers.dart';
import '../../core/storage/nequi_seen_store.dart';
import '../../data/formatters.dart';
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

/// Detecta pagos/envíos de Nequi con [NequiNotificationParser] y deja en
/// [state] el movimiento pendiente de confirmación (si hay alguno).
///
/// El flujo depende de dónde esté la app cuando llega la notificación:
/// - **abierta**: el movimiento queda en [state] y el formulario se abre solo;
/// - **en segundo plano**: se muestra un aviso propio con "Registrar" y
///   "Descartar". Recién al aceptarlo el movimiento pasa a [state], así que
///   descartarlo no deja nada pendiente;
/// - **cerrada**: el stream no escucha, pero al abrir la app (o volver a
///   ella) se revisa la bandeja y se ofrece lo que siga ahí.
///
/// Nunca crea el movimiento por su cuenta: eso lo hace quien escuche este
/// estado (ver `ScaffoldWithNav`) mostrando `showTransactionForm` y recién
/// persistiendo cuando el usuario confirma.
class NequiCaptureController extends Notifier<PendingNequiMatch?>
    with WidgetsBindingObserver {
  /// Cuántas notificaciones ya ofrecidas se recuerdan. Solo hace falta cubrir
  /// las que pueden seguir en la bandeja.
  static const int _maxSeenKeys = 50;

  final Completer<void> _ready = Completer<void>();
  final List<PendingNequiMatch> _queue = <PendingNequiMatch>[];
  final List<String> _seenKeys = <String>[];
  StreamSubscription<NequiRawNotification>? _subscription;
  bool _foreground = true;

  /// Se completa cuando terminó el arranque (permisos, aviso de lanzamiento y
  /// primera revisión de la bandeja). Útil en tests.
  Future<void> get ready => _ready.future;

  @override
  PendingNequiMatch? build() {
    final AppLifecycleState? lifecycle = WidgetsBinding.instance.lifecycleState;
    _foreground = lifecycle == null || lifecycle == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    ref.onDispose(() {
      WidgetsBinding.instance.removeObserver(this);
      _subscription?.cancel();
    });
    unawaited(_init().whenComplete(() {
      if (!_ready.isCompleted) _ready.complete();
    }));
    return null;
  }

  NequiNotificationGateway get _notifications =>
      ref.read(nequiNotificationGatewayProvider);

  LocalNotificationsGateway get _localNotifications =>
      ref.read(localNotificationsGatewayProvider);

  NequiSeenStore get _seenStore => ref.read(nequiSeenStoreProvider);

  Future<void> _init() async {
    _seenKeys.addAll(await _seenStore.readKeys());

    await _localNotifications.initialize(onNotificationTap: _onNotificationTap);
    await _localNotifications.requestPermission();
    _onNotificationTap(await _localNotifications.consumeLaunchPayload());

    _subscription = _notifications.notifications.listen(_onNotification);
    await _offerActiveNotifications();
  }

  // El parámetro se renombra a propósito: `state` ya es la propiedad de
  // [Notifier] que guarda el match pendiente.
  @override
  // ignore: avoid_renaming_method_parameters
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) {
    _foreground = lifecycle == AppLifecycleState.resumed;
    if (_foreground) unawaited(_offerActiveNotifications());
  }

  /// El usuario tocó el aviso propio o su botón "Registrar".
  void _onNotificationTap(String? payload) {
    final PendingNequiMatch? match = PendingNequiMatch.decode(payload);
    if (match != null) _enqueue(match);
  }

  void _onNotification(NequiRawNotification raw) {
    final PendingNequiMatch? match = _newMatch(raw);
    if (match == null) return;
    if (_foreground) {
      _enqueue(match);
    } else {
      unawaited(_notifyMatch(match));
    }
  }

  /// La app está abierta: lo que siga en la bandeja se ofrece directo en el
  /// formulario, sin aviso propio.
  Future<void> _offerActiveNotifications() async {
    final List<NequiRawNotification> active =
        await _notifications.activeNotifications();
    for (final NequiRawNotification raw in active) {
      final PendingNequiMatch? match = _newMatch(raw);
      if (match != null) _enqueue(match);
    }
  }

  /// Devuelve el movimiento detectado en [raw], o `null` si no es un
  /// movimiento de Nequi o si esa notificación ya se había ofrecido.
  PendingNequiMatch? _newMatch(NequiRawNotification raw) {
    if (raw.removed || !_looksLikeNequi(raw.packageName)) return null;

    final NequiParseResult result = NequiNotificationParser.parse(
      title: raw.title ?? '',
      body: raw.content ?? '',
    );
    final String key = '${raw.id}|${raw.title}|${raw.content}';
    final PendingNequiMatch? match = switch (result) {
      NequiIgnored() => null,
      NequiIncomeDetected(amount: final amount) =>
        PendingNequiMatch(type: MovementType.income, amount: amount, key: key),
      NequiExpenseDetected(amount: final amount, description: final description) =>
        PendingNequiMatch(
          type: MovementType.expense,
          amount: amount,
          description: description,
          key: key,
        ),
    };
    if (match == null || _seenKeys.contains(key)) return null;

    _seenKeys.add(key);
    if (_seenKeys.length > _maxSeenKeys) {
      _seenKeys.removeRange(0, _seenKeys.length - _maxSeenKeys);
    }
    unawaited(_seenStore.saveKeys(List<String>.of(_seenKeys)));
    return match;
  }

  /// `packageName` es best-effort: si el plugin no lo expone (`null`), no se
  /// bloquea nada acá — el gate real es que [NequiNotificationParser] haya
  /// reconocido el texto.
  bool _looksLikeNequi(String? packageName) =>
      packageName == null || packageName.toLowerCase().contains('nequi');

  void _enqueue(PendingNequiMatch match) {
    final bool alreadyQueued =
        match.key != null && _queue.any((queued) => queued.key == match.key);
    if (alreadyQueued) return;
    _queue.add(match);
    state ??= match;
  }

  Future<void> _notifyMatch(PendingNequiMatch match) {
    final bool isIncome = match.type == MovementType.income;
    final String amount = AppFormatters.currency(match.amount);
    final String? description = match.description;
    return _localNotifications.showActionableNotification(
      id: match.key.hashCode & 0x7fffffff,
      title: isIncome ? '¿Registrar este ingreso?' : '¿Registrar este gasto?',
      body: description == null ? 'Nequi · $amount' : 'Nequi · $amount · $description',
      payload: match.encode(),
    );
  }

  /// Saca de la cola el movimiento que se estaba ofreciendo (se haya
  /// confirmado o cancelado) y pasa al siguiente, si hay.
  void dismiss() {
    if (_queue.isNotEmpty) _queue.removeAt(0);
    state = _queue.isEmpty ? null : _queue.first;
  }

  Future<bool> requestPermission() => _notifications.requestPermission();

  Future<void> openAppDetails() => _notifications.openAppDetails();
}
