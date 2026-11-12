# Mejoras de sesión, captura de Nequi e identidad visual

Objetivo: que la sesión/huella no se pierda tan rápido, que la captura de Nequi sea un flujo simple de aviso + confirmación (ingresos y envíos), y darle a KoroFin un logo/identidad y un login/registro mejor diseñados.
Ramas: `fix/sesion-duracion` (T1-T2), `feat/nequi-flujo-simple` (T3-T5), `feat/identidad-visual` (T6-T9), todas desde `develop`. Entrega: `ask-on-risk`.
Test runner: `cd korofin_mobile && flutter test` / `flutter analyze`.

## Specs

- S1: "la duracion de la huella, bueno el inicio de sesion debe deurar un poco más ya que dura muy poco."
  - Supuesto (no confirmado con logs): la sesión se pierde porque cualquier fallo de red durante el refresh borra el refresh token (el backend ya da 7 días de refresh). Solo un rechazo real del backend (401/403) debe cerrar la sesión.
  - Supuesto: la huella se pide en cada regreso del background, por breve que sea. Se agrega un período de gracia.
- S2: "cuando me llega la notificacion push de nequi, necesito que me salga para registrar el ingreso o gasto como un pop menu, y para confirmar si me entra a la aplciacion y me abra lo que ingrese de manera autoamtica."
- S3: "ahor amismo no lee cuando yo mando algo, entonces toca registrar eso msimo, con el msimo flujo."
  - Bloqueado: falta el texto real de la notificación de Nequi al enviar plata.
- S4: "hacerlo lo más simple posible."
- S5: "usa claude design para mejorar los diseños actuales, investiga diseños mucho mejores, el login y register, que estan muy basicos. usa esat skill: https://github.com/kaankiziltug/logo-design-skill esta skill es para crear logo, una identidad de mi aplicacion"
- S6: "tambien hazme una audioria de mi aplicaicon usando esta skill: https://github.com/kaankiziltug/logo-design-skill"
  - Ambiguo: la URL es la misma skill de logos, que solo audita logos/identidad (modo Critique). Se aplica a la identidad actual; una auditoría general de la app necesitaría otra skill.
- S7: "las skills en local para el proyecto apenas."

## Tasks

- [x] T1 (S1): un fallo transitorio del refresh no borra la sesión; solo 401/403. — inline — Risk: item 2 — `70fbc0d` (rama `fix/sesion-duracion`)
- [x] T2 (S1): período de gracia de 2 min del bloqueo con huella al volver del background. — inline — Risk: item 2 — `fce2f99` + endurecimiento ante reloj atrasado
- [x] T3 (S2, S4): aviso propio con acciones "Registrar" / "Descartar"; "Registrar" abre la app con el formulario pre-llenado. — inline — `ac3d8db` (rama `feat/nequi-flujo-simple`)
- [x] T4 (S2, S4): al abrir la app, ofrecer las notificaciones de Nequi que sigan en la bandeja (cubre el caso de la app cerrada), sin repetir. — inline — `ac3d8db`
- [ ] T5 (S3): reconocer el envío propio con el texto real. — bloqueado, falta muestra
- [x] T6 (S7): skill `logo-design` instalada en `.claude/skills/logo-design` (solo local, excluida de git).
- [ ] T7 (S5, S6): crítica de la identidad actual + tres conceptos de logo (`design/identidad/conceptos/conceptos.png`); esperando que el usuario elija dirección.
- [ ] T8 (S5): kit del logo elegido + íconos de la app. — espera T7
- [ ] T9 (S5): rediseño de login y registro con la identidad elegida. — espera T7

## Log

- L1 (2026-11-12): Pedido del usuario verbatim:
  "cosas a mejorar, la duracion de la huella, bueno el inicio de sesion debe deurar un poco más ya que dura muy poco.
  Necesito mejorar el flujo de lectura.
  1. cuando me llega la notificacion push de nequi, necesito que me salga para registrar el ingreso o gasto como un pop menu, y para confirmar si me entra a la aplciacion y me abra lo que ingrese de manera autoamtica.
  2. ahor amismo no lee cuando yo mando algo, entonces toca registrar eso msimo, con el msimo flujo.
  3. hacerlo lo más simple posible.
  4. usa claude design para mejorar los diseños actuales, investiga diseños mucho mejores, el login y register, que estan muy basicos. usa esat skill: https://github.com/kaankiziltug/logo-design-skill esta skill es para crear logo, una identidad de mi aplicacion
  5. tambien hazme una audioria de mi aplicaicon usando esta skill: https://github.com/kaankiziltug/logo-design-skill
  6. las skills en local para el proyecto apenas."
- L2: `JWT_REFRESH_EXPIRATION_MS=604800000` (7 días) y access 15 min en `.env.example` y docs; el valor real en Render no se verificó. `AuthController._bootstrap` y `onRefresh` borran la sesión ante cualquier `ApiException`, incluidos timeouts.
- L3: Engram no está disponible en esta sesión: espejo `odd/mejoras-sesion-nequi-identidad/tasks` pendiente.
- L4: T1-T2 verificados por un verificador independiente (solo lectura del diff): PASS en ambos, sin bloqueantes. Avisos: el período de gracia usaba el reloj de pared (corregido: un tiempo negativo bloquea); un fallo transitorio del refresh muestra el mensaje "Tu sesión expiró" aunque la sesión siga abierta (pendiente); el backend invalida la sesión si un refresh rota el token y la respuesta se pierde (`RefreshTokenService`, preexistente, pendiente de decisión).
- L5: T3-T4: `flutter test` 210 en verde en `feat/nequi-flujo-simple`, `flutter analyze` limpio, `flutter build apk --release` compila. Sin probar en dispositivo. La parte de `ScaffoldWithNav` (reofrecer tras el bloqueo) no tiene test de widget.
- L6: Con la app cerrada del todo no hay aviso en tiempo real: el plugin solo entrega notificaciones con el motor de Flutter vivo. Se cubre al abrir la app (T4). Tiempo real con la app cerrada exigiría un listener nativo en Kotlin.

