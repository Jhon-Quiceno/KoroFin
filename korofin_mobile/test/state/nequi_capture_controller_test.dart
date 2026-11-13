import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/notifications/local_notifications_gateway.dart';
import 'package:korofin_mobile/core/notifications/nequi_notification_gateway.dart';
import 'package:korofin_mobile/core/notifications/pending_nequi_match.dart';
import 'package:korofin_mobile/core/providers.dart';
import 'package:korofin_mobile/core/storage/nequi_seen_store.dart';
import 'package:korofin_mobile/models/movement.dart';
import 'package:korofin_mobile/state/nequi_capture/nequi_capture_controller.dart';

class _FakeNequiGateway implements NequiNotificationGateway {
  final StreamController<NequiRawNotification> controller =
      StreamController<NequiRawNotification>.broadcast();

  /// Lo que sigue en la bandeja del sistema.
  List<NequiRawNotification> active = <NequiRawNotification>[];

  @override
  Stream<NequiRawNotification> get notifications => controller.stream;

  @override
  Future<List<NequiRawNotification>> activeNotifications() async => active;

  @override
  Future<bool> isPermissionGranted() async => true;

  @override
  Future<bool> requestPermission() async => true;

  @override
  Future<void> openAppDetails() async {}
}

class _ShownNotification {
  _ShownNotification(this.id, this.title, this.body, this.payload);
  final int id;
  final String title;
  final String body;
  final String payload;
}

class _FakeLocalNotifications implements LocalNotificationsGateway {
  final List<_ShownNotification> shown = <_ShownNotification>[];
  void Function(String? payload)? onTap;
  String? launchPayload;

  @override
  Future<void> initialize({
    required void Function(String? payload) onNotificationTap,
  }) async =>
      onTap = onNotificationTap;

  @override
  Future<String?> consumeLaunchPayload() async => launchPayload;

  @override
  Future<void> showActionableNotification({
    required int id,
    required String title,
    required String body,
    required String payload,
  }) async =>
      shown.add(_ShownNotification(id, title, body, payload));

  @override
  Future<bool?> requestPermission() async => true;
}

NequiRawNotification _income({
  int id = 1,
  String amount = '1000',
  bool removed = false,
}) =>
    NequiRawNotification(
      id: id,
      packageName: 'com.nequi.MobileApp',
      title: 'Envío',
      content: 'ANUAR MARRIAGA te envió $amount, ¡lo mejor!',
      removed: removed,
    );

class _Harness {
  _Harness({
    _FakeNequiGateway? gateway,
    _FakeLocalNotifications? local,
    NequiSeenStore? seen,
  })  : gateway = gateway ?? _FakeNequiGateway(),
        local = local ?? _FakeLocalNotifications(),
        seen = seen ?? InMemoryNequiSeenStore() {
    container = ProviderContainer(
      overrides: <Override>[
        nequiNotificationGatewayProvider.overrideWithValue(this.gateway),
        localNotificationsGatewayProvider.overrideWithValue(this.local),
        nequiSeenStoreProvider.overrideWithValue(this.seen),
      ],
    );
    addTearDown(container.dispose);
  }

  final _FakeNequiGateway gateway;
  final _FakeLocalNotifications local;
  final NequiSeenStore seen;
  late final ProviderContainer container;

  NequiCaptureController get notifier =>
      container.read(nequiCaptureControllerProvider.notifier);

  PendingNequiMatch? get pending =>
      container.read(nequiCaptureControllerProvider);

  Future<void> start() => notifier.ready;

  Future<void> emit(NequiRawNotification raw) async {
    gateway.controller.add(raw);
    await Future<void>.delayed(Duration.zero);
  }

  void toBackground() =>
      notifier.didChangeAppLifecycleState(AppLifecycleState.paused);

  Future<void> toForeground() async {
    notifier.didChangeAppLifecycleState(AppLifecycleState.resumed);
    await Future<void>.delayed(Duration.zero);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('con la app abierta, un ingreso de Nequi abre el formulario sin aviso propio',
      () async {
    final h = _Harness();
    await h.start();

    await h.emit(_income());

    expect(h.pending?.type, MovementType.income);
    expect(h.pending?.amount, 1000);
    expect(h.local.shown, isEmpty);
  });

  test(
      'con la app en segundo plano, muestra el aviso propio y no abre el '
      'formulario hasta que el usuario lo acepte', () async {
    final h = _Harness();
    await h.start();
    h.toBackground();

    await h.emit(_income());

    expect(h.pending, isNull);
    expect(h.local.shown, hasLength(1));
    expect(h.local.shown.single.title, '¿Registrar este ingreso?');
    expect(h.local.shown.single.body, contains('1.000'));
  });

  test('aceptar el aviso propio abre el formulario con el movimiento detectado',
      () async {
    final h = _Harness();
    await h.start();
    h.toBackground();
    await h.emit(_income(amount: '25.000'));

    h.local.onTap!(h.local.shown.single.payload);

    expect(h.pending?.type, MovementType.income);
    expect(h.pending?.amount, 25000);
  });

  test('si la app se abrió desde el aviso propio, abre el formulario al arrancar',
      () async {
    final local = _FakeLocalNotifications()
      ..launchPayload = const PendingNequiMatch(
        type: MovementType.expense,
        amount: 5000,
        description: 'Tienda',
        key: 'k1',
      ).encode();
    final h = _Harness(local: local);

    await h.start();

    expect(h.pending?.type, MovementType.expense);
    expect(h.pending?.amount, 5000);
    expect(h.pending?.description, 'Tienda');
  });

  test('la misma notificación de Nequi se ofrece una sola vez', () async {
    final h = _Harness();
    await h.start();

    await h.emit(_income());
    h.notifier.dismiss();
    await h.emit(_income());

    expect(h.pending, isNull);
  });

  test('descartar la notificación de Nequi de la bandeja no la ofrece de nuevo',
      () async {
    final h = _Harness();
    await h.start();

    await h.emit(_income(removed: true));

    expect(h.pending, isNull);
    expect(h.local.shown, isEmpty);
  });

  test(
      'al abrir la app, ofrece las notificaciones de Nequi que llegaron con la '
      'app cerrada y siguen en la bandeja', () async {
    final gateway = _FakeNequiGateway()
      ..active = [_income(id: 7, amount: '8.000')];
    final h = _Harness(gateway: gateway);

    await h.start();

    expect(h.pending?.amount, 8000);
    expect(h.local.shown, isEmpty);
  });

  test('no vuelve a ofrecer en otro arranque una notificación ya ofrecida',
      () async {
    final seen = InMemoryNequiSeenStore();
    final first = _Harness(
      gateway: _FakeNequiGateway()..active = [_income(id: 7)],
      seen: seen,
    );
    await first.start();
    expect(first.pending, isNotNull);

    final second = _Harness(
      gateway: _FakeNequiGateway()..active = [_income(id: 7)],
      seen: seen,
    );
    await second.start();

    expect(second.pending, isNull);
  });

  test('al volver a la app, revisa la bandeja otra vez', () async {
    final h = _Harness();
    await h.start();
    h.toBackground();
    h.gateway.active = [_income(id: 9, amount: '3.000')];

    await h.toForeground();

    expect(h.pending?.amount, 3000);
  });

  test('varios movimientos pendientes se ofrecen de a uno, en orden', () async {
    final h = _Harness();
    await h.start();

    await h.emit(_income(id: 1, amount: '1000'));
    await h.emit(_income(id: 2, amount: '2000'));
    expect(h.pending?.amount, 1000);

    h.notifier.dismiss();
    expect(h.pending?.amount, 2000);

    h.notifier.dismiss();
    expect(h.pending, isNull);
  });

  test('una notificación de otra app con texto parecido se ignora', () async {
    final h = _Harness();
    await h.start();

    await h.emit(const NequiRawNotification(
      id: 3,
      packageName: 'com.whatsapp',
      title: 'Juan',
      content: 'Pedro te envió 1000, ¡lo mejor!',
    ));

    expect(h.pending, isNull);
  });

  test('una notificación de Nequi que no es un movimiento se ignora', () async {
    final h = _Harness();
    await h.start();

    await h.emit(const NequiRawNotification(
      id: 4,
      packageName: 'com.nequi.MobileApp',
      title: 'Resumen semanal',
      content: 'Revisá tu resumen de gastos de esta semana en Nequi.',
    ));

    expect(h.pending, isNull);
  });

  test(
      'un envío propio sin monto se ofrece como gasto y el aviso pide completar '
      'el monto', () async {
    final h = _Harness();
    await h.start();
    h.toBackground();

    await h.emit(const NequiRawNotification(
      id: 5,
      packageName: 'com.nequi.MobileApp',
      title: 'Tu plata llegó con éxito 💜',
      content: 'Envío exitoso, la plata ya está en el Nequi destino.',
    ));

    expect(h.local.shown.single.title, '¿Registrar este gasto?');
    expect(h.local.shown.single.body, 'Nequi · Envío exitoso · falta el monto');
    h.local.onTap!(h.local.shown.single.payload);
    expect(h.pending?.type, MovementType.expense);
    expect(h.pending?.amount, 0);
  });
}
