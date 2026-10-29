# Captura de notificaciones de pago (Nequi) -> pre-llenar gasto/ingreso

Objetivo: cuando llega una notificación de Nequi de un pago/envío/recibo de plata, la app
detecta el texto, lo parsea, y le ofrece al usuario (nunca automático) un formulario
pre-llenado para confirmar el gasto o ingreso. Android únicamente (iOS no tiene API para
leer notificaciones de otras apps). Rama: `develop` (feature branch nueva desde ahí,
`feat/notificaciones-nequi` o similar). Test runner: `cd korofin_mobile && flutter analyze`
/ `flutter test`. TDD donde aplica (lógica pura del parser); el resto (servicio nativo,
UI de permisos) se verifica funcionalmente en dispositivo real.

## Specs

- S1. Pedido original: "me gustaria implementar usando que cuando me lleguen las
  notificaciones de pago de algo, pro ejemplo que pago algo suando nfc y me llega la
  notificacion que me agregue en seguida en la aplicacion".
- S2. Alcance confirmado por el usuario (pregunta cerrada ya resuelta): "Solo Android, pero
  el usuario confirma antes de crear el gasto" — nunca se crea el movimiento sin que el
  usuario lo confirme en un formulario.
- S3. Prioridad actual, solo Nequi para probar primero: "mejor vamos a impelmntar lo de
  nequi, para ver si funciona, me mandan algo de dinero y lea el psuh de nequi y me abra la
  app o me abra un nodal para llenar el gasto o ingreso". Nu y Rappi quedan fuera de esta
  iteración (el usuario los había mencionado como interés a futuro pero nunca mandó el
  formato real de sus notificaciones — sin texto real no se arma el parser, igual que se
  decidió para Nequi en su momento).
- S4. Formato real de las notificaciones de Nequi (verbatim, mandado por el usuario):

  > 1. Al recibir dinero
  > Título: ¡Te enviaron plata! o ¡Te llegó una plata!
  > Mensaje: [Nombre de quien envía] te envió $[Monto]. Tu disponible ahora es
  > $[Nuevo saldo]. Si es por Transfiya: ¡Te enviaron $[Monto] por Transfiya! Entra a Nequi
  > para aceptarla.
  >
  > 2. Al enviar dinero
  > Título: ¡Envío exitoso! o ¡Plata enviada!
  > Mensaje: Enviaste $[Monto] a [Nombre o número de celular]. Tu disponible ahora es
  > $[Nuevo saldo].
  >
  > 3. Al pagar algo (Datáfono, QR, PSE o Facturas)
  > Título: ¡Pago exitoso! o ¡Compra exitosa!
  > Mensaje: Pagaste $[Monto] en [Nombre del comercio o servicio]. Tu disponible ahora es
  > $[Nuevo saldo].
  >
  > Aspectos clave: si la vista previa de notificaciones está oculta (pantalla de bloqueo),
  > el mensaje dice solo "Nequi: Tienes una nueva notificación" hasta desbloquear — en ese
  > caso no hay texto para parsear, se ignora esa notificación.

## Decisiones de arquitectura (tomadas para poder implementar, no bloquean)

- Sin plugin existente de notification-listener en el proyecto ni MethodChannel/EventChannel
  previo (confirmado por exploración). Se agrega el paquete `notification_listener_service`
  (pub.dev) en vez de escribir el `NotificationListenerService` nativo a mano — evita
  escribir y mantener Kotlin de bajo nivel para algo que el paquete ya resuelve
  (enable-check, stream de eventos, redirect a `ACTION_NOTIFICATION_LISTENER_SETTINGS`).
- El filtro de "es de Nequi" combina `packageName` (best-effort, no verificado en
  dispositivo real todavía) + las frases de S4 como gate real — así no depende de acertar
  el nombre exacto del paquete a la primera.
- Para que funcione incluso con la app cerrada (no solo en primer plano), se usa
  `flutter_local_notifications` para que, al detectar un match, la propia app muestre una
  notificación local accionable ("¿Registrar este movimiento?"); al tocarla, abre la app
  con los datos parseados (vía extras de `Intent` leídos en `MainActivity`/al arrancar).
  Si la app ya está corriendo, además se intenta mostrar el modal al toque en caliente vía
  el stream del plugin. No se implementa ejecución Dart headless (`flutter_background_service`
  o similar) — se evalúa más adelante solo si el modal al tocar la notificación propia no
  alcanza.
- Reutiliza `showTransactionForm(context, type:, initial:, allowTypeToggle: false)`
  (`korofin_mobile/lib/screens/movements/transaction_form_sheet.dart:23`) para el
  formulario pre-llenado — no hace falta un sheet nuevo, ya soporta `initial`.
- Creación real vía `ref.read(movementsProvider(type).notifier).add(draft)`
  (`korofin_mobile/lib/state/movements/movements_controller.dart:68`), igual que
  `quick_add_sheet`.

## Tasks

- [x] T1 (S4) — Parser puro Dart: texto de notificación (título+cuerpo) -> resultado
  tipado (ignorar / ingreso / egreso, monto, descripción sugerida). TDD: tests primero con
  los textos exactos de S4 como fixtures (incluidos los casos "Te enviaron" / "Te llegó" /
  "Transfiya" / "Envío exitoso" / "Plata enviada" / "Pago exitoso" / "Compra exitosa" /
  pantalla bloqueada sin contenido). Ruta: inline (archivo nuevo acotado + tests).
  `lib/core/notifications/nequi_notification_parser.dart` +
  `test/core/nequi_notification_parser_test.dart` (RED confirmado antes de implementar,
  10/10 tests en verde).
- [x] T2 (S2, S4) — Capa nativa Android: agregar `notification_listener_service` +
  `flutter_local_notifications` a `pubspec.yaml`; declarar permiso/listener en
  `AndroidManifest.xml`; pantalla de "activar acceso a notificaciones" (explica por qué,
  botón que abre los ajustes del sistema, muestra si ya está activado) — no hay patrón
  previo en el proyecto para esto (es un redirect a Settings, no un permiso runtime
  normal). Ruta: inline (implementado directamente por el mismo agente invocado para
  T1-T3, sin sub-delegación adicional).
  `pubspec.yaml`, `android/app/src/main/AndroidManifest.xml`,
  `lib/core/notifications/nequi_notification_gateway.dart`,
  `lib/core/notifications/local_notifications_gateway.dart`,
  `lib/screens/nequi_capture/nequi_capture_permission_screen.dart`,
  entrada en `lib/screens/settings/settings_screen.dart` y ruta
  `/settings/nequi-capture` en `lib/routes/app_router.dart`.
- [x] T3 (S2, S3, S4) — Wiring: listener del stream de notificaciones (filtra
  Nequi+patrones de T1), al detectar match dispara la notificación local accionable
  (toque -> abre la app con los datos), y si la app ya está en primer/segundo plano
  también ofrece el modal directo. Integra con `showTransactionForm` + `movementsProvider`
  para que el usuario confirme y recién ahí se cree el gasto/ingreso. Ruta: inline (idem
  T2).
  `lib/core/notifications/pending_nequi_match.dart`,
  `lib/state/nequi_capture/nequi_capture_controller.dart`,
  `lib/screens/shell/scaffold_with_nav.dart` (wiring end-to-end con `showTransactionForm` +
  `movementsProvider`). `flutter analyze`: sin issues. `flutter test`: 164/164 en verde.
  T4 (verificación manual en dispositivo real) y T5 (rebuild del APK) quedan pendientes
  para el usuario, fuera del alcance de esta sesión.
- [ ] T4 (S1) — Verificación funcional en dispositivo real: el usuario se manda o recibe
  plata por Nequi, confirma que llega la notificación propia / el modal, y que al
  confirmar el movimiento aparece en la app. No automatizable — evidencia manual.
- [ ] T5 — Rebuild del APK (`--dart-define=API_BASE_URL=https://korofin-backend.onrender.com`)
  con la feature incluida, una vez T1-T3 estén en verde.

## Log

- L1 (verbatim, pedido original dentro de la conversación de migración de despliegue):
  "me gustaria implementar usando que cuando me lleguen las notificaciones de pago de
  algo, pro ejemplo que pago algo suando nfc y me llega la notificacion que me agregue en
  seguida en la aplicacion".
- L2: decisión de alcance (Android + confirmación del usuario, no automático) ya tomada
  antes de esta sesión de implementación.
- L3: usuario seleccionó Nequi, Nu y Rappi como apps de interés, pero solo mandó el
  formato real de notificación de Nequi (ver S4) — Nu/Rappi quedan fuera hasta tener texto
  real de sus notificaciones.
- L4: disparador de esta implementación — el usuario decidió dejar el cold-start de
  Render como está (no pagar, no agregar ping externo, no tocar timeouts) y priorizar esta
  feature: "mejor vamos a impelmntar lo de nequi, para ver si funciona...".
- L5: investigación previa (agente de exploración, solo lectura) sobre `korofin_mobile`:
  sin `MethodChannel`/`EventChannel` existente, sin plugin de permisos runtime, patrón de
  `showTransactionForm(initial:)` ya soporta pre-llenado, creación real vía
  `movementsProvider(type).notifier.add(draft)`.
- L6: implementación de T1-T3 completada en esta sesión. Decisiones no cubiertas por la
  spec original: (1) el filtro de `packageName` best-effort asume que el paquete real de
  Nequi contiene la cadena "nequi" (sin confirmar en dispositivo); si viene `null` no
  bloquea, el gate real sigue siendo el parser de T1. (2) Versiones de pubspec resueltas
  por `flutter pub get`: `notification_listener_service: ^0.3.3` -> 0.3.5,
  `flutter_local_notifications: ^18.0.1` (exacta). (3) El cross-cutting entre el tap de la
  notificación local / el stream en vivo y la UI se resolvió 100% dentro de Riverpod
  (`NotifierProvider<NequiCaptureController, PendingNequiMatch?>` + `ref.listen` en
  `ScaffoldWithNav`), sin necesitar un `ValueNotifier` estático como el de
  `ThemeController` — el callback de `flutter_local_notifications.initialize()` corre
  dentro del propio `Notifier`, que ya tiene `ref`. (4) Ambos gateways nativos
  (`NequiNotificationGateway`, `LocalNotificationsGateway`) son no-op fuera de Android
  (`Platform.isAndroid`), siguiendo el patrón ya usado en
  `core/auth/biometric_authenticator.dart` para poder inyectar fakes en tests — aunque
  no se escribieron tests de controller nuevos (T1-T3 según la spec: TDD solo en el
  parser puro, el resto se verifica funcionalmente en dispositivo real, T4). Verificación
  observada: `flutter analyze` sin issues; `flutter test` 164/164 en verde (10 nuevos del
  parser + 154 preexistentes, ninguno roto).
