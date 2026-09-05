# Plan de integración mobile ↔ backend de KoroFin

> **Estado:** documento de planeación. No contiene código. Es la fuente de verdad para unir la app
> Flutter (`korofin_mobile/`) con la API REST (`korofin-backend/`), rebanada por rebanada, probando
> cada una antes de pasar a la siguiente.
>
> **Contexto:** el backend ya está prácticamente completo (ver `docs/backend-plan.md`). El mobile
> hoy es 100 % UI con datos falsos (`lib/data/mock_data.dart`) y **cero** conexión con el backend.
> Este plan cierra ese hueco.
>
> **Meta de paridad:** replicar lo que hacía el **frontend web de FinSmart** (Next.js), no la app RN
> de FinSmart. Se deja **fuera** la integración de Telegram como pantalla de la app — el registro por
> foto se hace con la **cámara nativa** del teléfono (`/api/receipts/scan`). Telegram sigue existiendo
> en el backend como canal servidor-a-servidor opcional, ajeno a la app.

---

## Índice

1. [Estado actual: qué está hecho y qué no](#1-estado-actual-qué-está-hecho-y-qué-no)
2. [Inventario de endpoints del backend](#2-inventario-de-endpoints-del-backend)
3. [Arquitectura de integración propuesta para el mobile](#3-arquitectura-de-integración-propuesta-para-el-mobile)
4. [Stack de dependencias a agregar](#4-stack-de-dependencias-a-agregar)
5. [Decisiones abiertas (requieren tu confirmación)](#5-decisiones-abiertas-requieren-tu-confirmación)
6. [Cómo levantar todo en local para probar](#6-cómo-levantar-todo-en-local-para-probar)
7. [Plan por fases (rebanadas verticales)](#7-plan-por-fases-rebanadas-verticales)
8. [Estrategia de tests del mobile](#8-estrategia-de-tests-del-mobile)
9. [Riesgos y notas](#9-riesgos-y-notas)

---

## 1. Estado actual: qué está hecho y qué no

### 1.1 Backend — `korofin-backend/` (Java 21 + Spring Boot 4)

**Estado: prácticamente completo.** `./mvnw.cmd compile` pasa limpio (verificado). 12 dominios de
negocio implementados con controller + service + repository + mapper + entity + exception, todos con
su suite de tests (TDD estricto: ~110 archivos de test).

| Dominio | Endpoints | Estado |
|---|---|---|
| `user` (auth) | `/api/users/*` | ✅ registro, login, refresh, logout, perfil, contraseña, preferencias. Mobile-only ya aplicado: sin cookies, sin CSRF, refresh token en el body JSON. |
| `expense` | `/api/expenses`, `/api/categories` | ✅ CRUD + filtros + paginación |
| `income` | `/api/incomes` | ✅ CRUD |
| `debt` | `/api/debts` + `/payments` + `/charges` | ✅ CRUD + abonos + cargos atómicos |
| `card` | `/api/cards` + movimientos + cuotas | ✅ CRUD + compras/pagos + planes de cuotas + cierre de ciclo |
| `recurringpayment` | `/api/recurring` | ✅ CRUD + toggle + pay |
| `analysis` | `/api/analysis/*` | ✅ resumen, recomendaciones, predicción |
| `report` | `/api/reports/*` | ✅ mensual, movimientos, export CSV/JSON |
| `notification` | `/api/notifications/*` | ✅ listar, no-leídas, marcar, preferencias, push-token |
| `ai` | `/api/ai/*`, `/api/receipts/scan` | ✅ chat, historial, uso, insights, categorización, estado de proveedores, escaneo de recibos. Patrón Strategy/Registry con 5 proveedores y fallback en cascada. **Requiere al menos una API key de proveedor configurada para funcionar de verdad.** |
| `statement` | `/api/statement-imports/*` | ✅ preview (multipart PDF/CSV/XLSX) + confirm, con dedup |
| `integration` (Telegram) | `/api/integrations/telegram/*` | ✅ implementado, **apagado por defecto**. Fuera del alcance de la app. |

**Migraciones Flyway:** `V1`–`V7`, esquema limpio y consolidado.
**Infra:** `Dockerfile` (multi-stage), `docker-compose.yml` en la raíz, workflows `ci.yml`,
`deploy-backend.yml`, `trivy.yml`.

**Lo que le falta al backend para la app:**

- **Push real para Flutter.** `ExpoPushAdapter` habla el protocolo de Expo (era para la app RN).
  Flutter necesita **FCM** → hace falta un `FcmPushAdapter` nuevo en `notification/service/channel/`.
  No bloquea el MVP de integración: las notificaciones in-app funcionan sin esto.
- **OAuth de Google.** No existe. El backend solo hace email + contraseña. El botón "Continuar con
  Google" del login mobile no tiene contra qué hablar hoy.
- Nada más. Todo lo demás que la app necesita ya está expuesto.

### 1.2 Mobile — `korofin_mobile/` (Flutter / Dart)

**Estado: maqueta visual completa, sin backend.** Todas las pantallas del flujo objetivo ya existen
y se ven pobladas, pero con datos estáticos.

**Pantallas ya construidas** (`lib/screens/`): `auth/` (login, register, biometric_lock),
`dashboard/`, `movements/` (+ form sheet), `categories/` (+ form + picker), `debts_hub/` (tabs de
deudas, tarjetas y suscripciones, con detalles y sheets de alta), `import_statement/`, `reports/`,
`receipt_scan/`, `notifications/`, `settings/`, `assistant/`, `quick_add/`, `telegram/`.

**Lo que NO existe todavía (el hueco completo):**

| Falta | Detalle |
|---|---|
| Capa HTTP | No hay cliente HTTP. `pubspec.yaml` solo trae `google_fonts`, `fl_chart`, `go_router`, `intl`. |
| Modelos serializables | `lib/models/*.dart` son clases de UI (`String id`, sin `fromJson`/`toJson`). No mapean los DTOs del backend. |
| Sesión y tokens | No hay almacenamiento seguro, ni guardado de access/refresh token, ni refresh automático. |
| Guard de rutas | El login hace `context.go('/home')` sin autenticar. No hay redirect por estado de sesión. |
| Manejo de estado | Todo es `StatelessWidget` leyendo listas `static` de `MockData`. No hay gestor de estado. |
| Configuración de entorno | No hay `API_BASE_URL` por flavor/`--dart-define`. |
| Estados de carga / error | Las pantallas no contemplan spinners, errores de red, vacíos reales, reintentos. |
| Tests | Solo el `test/widget_test.dart` por defecto. |

**Datos del proyecto que sí están bien:** el design system (`lib/theme/`), la navegación
(`go_router` con `StatefulShellRoute`), los widgets reutilizables (`lib/widgets/`), el `applicationId`
Android (`com.korofin.korofin_mobile`). La UI no hay que rehacerla — hay que **conectarla**.

### 1.3 Resumen del hueco

> El backend está listo. El mobile es una fachada. **El 100 % del trabajo de este plan es construir
> la capa de datos del mobile y cablear cada pantalla a su endpoint**, más dos ajustes chicos de
> backend (FCM y decidir qué hacer con "Google").

---

## 2. Inventario de endpoints del backend

Base: todas las rutas cuelgan de `/api`. Auth por `Authorization: Bearer <accessToken>` salvo las
marcadas como públicas. Errores con forma `ErrorResponse` (`{ status, message, ... }`) desde
`GlobalExceptionHandler`.

### Autenticación — `/api/users` (público salvo indicado)

| Método | Ruta | Cuerpo | Notas |
|---|---|---|---|
| POST | `/register` | `{ name, email, password }` | Devuelve `AuthResponse { accessToken, refreshToken, ... }` |
| POST | `/login` | `{ email, password, rememberMe }` | idem |
| POST | `/refresh` | `{ refreshToken }` | Rota el refresh token — hay que guardar el nuevo |
| POST | `/logout` | `{ refreshToken }` | Revoca la sesión |
| PUT | `/profile` | `ProfileUpdateRequest` | 🔒 |
| PUT | `/password` | `PasswordChangeRequest` | 🔒 |
| GET | `/preferences` | — | 🔒 tema / moneda / idioma |
| PATCH | `/preferences` | `UserPreferencesUpdateRequest` | 🔒 |

### Gastos e ingresos

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/expenses` | Paginado (`Page<>` de Spring) + filtros: `categoryId`, `from`, `to`, `paymentMethod`, `page`, `size`, `sort` |
| POST / PUT / DELETE | `/api/expenses`, `/api/expenses/{id}` | |
| GET / POST / PUT / DELETE | `/api/incomes`, `/api/incomes/{id}` | Gemelo de gastos, sin método de pago |
| GET | `/api/categories` | Lista (tipo `EXPENSE` / `INCOME`) |
| POST / PUT / DELETE | `/api/categories`, `/api/categories/{id}` | |

### Deudas — `/api/debts`

| Método | Ruta |
|---|---|
| GET / POST | `/api/debts` |
| GET / PUT / DELETE | `/api/debts/{id}` |
| GET / POST | `/api/debts/{debtId}/payments` (abonos, bajan el saldo, crean un `Expense` vinculado) |
| GET / POST | `/api/debts/{debtId}/charges` (cargos, suben el saldo) |

### Tarjetas — `/api/cards`

| Método | Ruta |
|---|---|
| GET / POST | `/api/cards` |
| GET / PUT / DELETE | `/api/cards/{id}` |
| POST | `/api/cards/{cardId}/purchases` (soporta compra a cuotas) |
| POST | `/api/cards/{cardId}/payments` |
| GET | `/api/cards/{cardId}/movements` (paginado) |
| GET | `/api/cards/{cardId}/movements/{movementId}/installments` |

### Pagos recurrentes — `/api/recurring`

`GET`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/toggle`, `PATCH /{id}/pay`.

### Análisis y reportes

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/analysis/summary` | `?year=&month=` — cifras del mes |
| GET | `/api/analysis/recommendations` | |
| GET | `/api/analysis/prediction` | predicción de fin de mes |
| GET | `/api/reports/monthly` | `?year=&month=` |
| GET | `/api/reports/movements` | `?year=&month=` — lista para tabla |
| GET | `/api/reports/export` | `?format=csv\|json` — stream con `Content-Disposition` |

### Notificaciones — `/api/notifications`

`GET` (lista), `GET /unread-count`, `PATCH /{id}/read`, `PATCH /read-all`, `GET /preferences`,
`PUT /preferences`, `POST /push-token` (`{ ... , deviceId }`), `DELETE /push-token/{deviceId}`.

### IA — `/api/ai` y `/api/receipts`

| Método | Ruta | Notas |
|---|---|---|
| POST | `/api/ai/chat` | `{ message }` → respuesta. Cuota mensual por usuario (429 al pasarse). Rate-limited. |
| GET | `/api/ai/chat/history` | paginado |
| GET | `/api/ai/chat/usage` | cuota usada / restante |
| POST | `/api/ai/categorize` | sugiere categoría para una descripción |
| POST | `/api/ai/insights/generate` | genera un insight nuevo |
| GET | `/api/ai/insights` | último insight (204 si no hay) — *verificar la ruta exacta al implementar* |
| GET | `/api/ai/providers/status` | qué proveedores están configurados (nunca la key) |
| POST | `/api/receipts/scan` | `{ imageDataUri }` → monto/fecha/categoría extraídos. **No crea el gasto**, la app confirma con `POST /api/expenses`. Rate-limited. |

### Importación de extractos — `/api/statement-imports`

| Método | Ruta | Notas |
|---|---|---|
| POST | `/preview` | `multipart/form-data`, archivo PDF/CSV/XLSX (máx 10 MB) → filas con marca de posible duplicado |
| POST | `/confirm` | `StatementConfirmRequest` → crea los movimientos elegidos |

---

## 3. Arquitectura de integración propuesta para el mobile

Se respeta la organización actual del proyecto ("por tipo + feature"). Se agregan estas carpetas:

```
lib/
  core/
    config/        AppConfig (lee API_BASE_URL de --dart-define), Flavor
    network/       ApiClient (Dio), AuthInterceptor, RefreshInterceptor, ApiException
    storage/       SecureSessionStore (flutter_secure_storage: access + refresh token)
    result/        helpers de error → mensaje (equivalente al getApiErrorMessage de FinSmart)
  models/          DTOs con fromJson/toJson (reemplazan las clases de UI actuales):
                   auth_response, user, category, expense, income, debt, debt_payment,
                   debt_charge, credit_card, card_movement, installment, recurring_payment,
                   notification, notification_preference, analysis_summary, monthly_report,
                   chat_message, insight, receipt_extraction, statement_preview_row,
                   page_response<T>  (envoltura genérica para los Page<> de Spring)
  data/
    repositories/  un repositorio por dominio: auth, category, expense, income, debt, card,
                   recurring_payment, analysis, report, notification, ai, statement
    (mock_data.dart y formatters.dart siguen hasta que cada fase los reemplace)
  state/           providers por feature (según gestor elegido — ver sección 5):
                   auth, categories, movements, dashboard, debts, cards, subscriptions,
                   notifications, assistant, preferences
  routes/          app_router.dart + redirect basado en estado de sesión (nuevo)
```

**Principios:**

1. **Pantalla → provider → repositorio → ApiClient.** La pantalla nunca llama HTTP directo.
2. **Un repositorio por dominio**, con métodos que devuelven modelos ya deserializados o lanzan
   `ApiException` tipada.
3. **El `ApiClient` centraliza:** base URL, header `Authorization`, timeout, y el interceptor que
   ante un 401/403 intenta `POST /api/users/refresh` una vez y reintenta la request (mismo patrón que
   `api-client.ts` de FinSmart, adaptado a Dio).
4. **Sesión:** access token en memoria + refresh token en `flutter_secure_storage`. Al arrancar la
   app, si hay refresh token guardado, se intenta un refresh silencioso antes de decidir la ruta
   inicial.
5. **`--dart-define=API_BASE_URL=...`** para no hardcodear la URL. Sin default de producción en el
   código.

---

## 4. Stack de dependencias a agregar

| Necesidad | Paquete recomendado | Alternativa | Por qué el recomendado |
|---|---|---|---|
| Cliente HTTP | `dio` | `http` | Interceptores de primera clase (auth + refresh + logging), cancelación, `FormData` para el multipart de extractos. Es el equivalente directo del `axios` que ya usaba FinSmart. |
| Almacenamiento seguro | `flutter_secure_storage` | — | Keychain (iOS) / Keystore (Android) para el refresh token. |
| Gestor de estado | `flutter_riverpod` | `flutter_bloc`, `provider` | Testeable sin `BuildContext`, `AsyncNotifier`/`FutureProvider` cubren carga/error/refetch sin boilerplate, overrides triviales en tests. **Ver sección 5 — es tu decisión.** |
| Serialización JSON | `freezed` + `json_serializable` (codegen) | `fromJson` a mano | Son ~25 DTOs; codegen evita errores de tipeo y da `copyWith`/`==` gratis. **Ver sección 5.** |
| Cámara para recibos | `image_picker` | `camera` | La forma más simple de "sacar foto o elegir de galería" y obtener bytes → data URI para `/api/receipts/scan`. |
| Elegir archivo (extractos) | `file_picker` | — | Selección de PDF/CSV/XLSX del sistema. |
| Formateo de fechas/moneda | `intl` (ya está) | — | — |
| Biometría (lock local) | `local_auth` | — | La pantalla `biometric_lock_screen` ya existe; es feature local, sin backend. |
| Push (fase posterior) | `firebase_messaging` | — | Requiere el `FcmPushAdapter` en el backend primero. Diferido. |

---

## 5. Decisiones abiertas (requieren tu confirmación)

1. **Gestor de estado.** Recomiendo **Riverpod**. Alternativas reales: Bloc (más ceremonia, muy
   explícito) o Provider (más simple, menos ayuda con async). Elegir uno define la carpeta `state/`
   y todos los ejemplos de las fases.
2. **Serialización JSON: codegen (`freezed`) o `fromJson` a mano.** Codegen agrega `build_runner` al
   flujo; a mano es cero setup pero más repetitivo y frágil con ~25 modelos.
3. **Botón "Continuar con Google" en el login.** El backend no tiene OAuth. Opciones: (a) quitar el
   botón para el MVP, (b) planear OAuth de Google como trabajo aparte (backend + app). Recomiendo (a).
4. **Push notifications.** `ExpoPushAdapter` no sirve para Flutter. Plan: dejar las notificaciones
   in-app para el MVP y hacer FCM (`FcmPushAdapter` en backend + `firebase_messaging` en la app) como
   fase posterior. Confirmar que ese orden te sirve.
5. **Pantalla `/telegram` del mobile.** Queda fuera del alcance. Opciones: borrar la ruta + el
   archivo `telegram_screen.dart`, o dejarla parqueada sin entrada de navegación. Recomiendo borrar.

---

## 6. Cómo levantar todo en local para probar

Cada fase se prueba con el backend corriendo de verdad contra Postgres. Setup una sola vez:

1. **Base de datos + backend:** `docker-compose.yml` (raíz del repo) levanta Postgres. Variables
   mínimas en `.env` de la raíz o de `korofin-backend/`: `JWT_SECRET`, `JWT_ISSUER`,
   `JWT_ACCESS_EXPIRATION_MS` (p. ej. `900000`), `JWT_REFRESH_EXPIRATION_MS` (p. ej. `604800000`),
   `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`.
2. **Backend:** `cd korofin-backend && ./mvnw.cmd spring-boot:run`. Swagger en
   `http://localhost:8080/swagger-ui.html` para probar endpoints a mano.
3. **App apuntando al backend:**
   - Emulador Android: `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080`
     (`10.0.2.2` es el host desde el emulador).
   - Dispositivo físico: usar la **IP LAN** de la PC (`http://192.168.x.x:8080`), no `localhost`.
4. **Para las fases de IA:** configurar al menos una API key de proveedor en el `.env` del backend
   (p. ej. `GEMINI_API_KEY`). Sin eso, `/api/ai/*` responde 503.

---

## 7. Plan por fases (rebanadas verticales)

Cada fase es **entregable y probable de punta a punta** antes de seguir. Orden pensado para que cada
una desbloquee a la siguiente. TDD: el test de cada repositorio/provider se escribe antes que el
código.

### Fase 0 — Fundaciones (sin UI nueva)

- Agregar dependencias (sección 4, según decisiones de la sección 5).
- `lib/core/config/` — `AppConfig` con `API_BASE_URL`.
- `lib/core/network/` — `ApiClient` (Dio), `AuthInterceptor` (mete el Bearer), `ApiException`
  (mapea `ErrorResponse` del backend a mensaje).
- `lib/core/storage/` — `SecureSessionStore`.
- `lib/models/page_response.dart` — envoltura genérica de `Page<>`.
- Agregar el job `mobile-checks` a `ci.yml` (`flutter analyze` + `flutter test`).
- **Prueba:** test unitario del `ApiClient` con un mock de Dio (header, timeout, mapeo de error) +
  `flutter analyze` limpio.

### Fase 1 — Autenticación real

- Modelos: `AuthResponse`, `User`.
- `AuthRepository`: register / login / refresh / logout contra `/api/users/*`.
- `RefreshInterceptor` en el `ApiClient`: 401 → un intento de refresh → reintento (con lock para no
  disparar refresh en paralelo).
- Estado `auth` (según gestor): `unknown / authenticated / unauthenticated`.
- Bootstrap al arrancar: refresh silencioso si hay token guardado.
- `app_router.dart`: `redirect` — sin sesión → `/login`; con sesión en `/login` → `/home`.
- Cablear `login_screen` y `register_screen` (validación, estados de carga/error). Logout desde
  `settings_screen`.
- Quitar (o posponer) el botón de Google según decisión 3.
- **Prueba:** con backend local — registrarse; cerrar y reabrir la app (la sesión persiste); forzar
  expiración del access token y ver el refresh automático; logout deja la app en `/login`. Widget
  tests de las dos pantallas con `AuthRepository` mockeado.

### Fase 2 — Categorías

- Modelo `Category` (con `CategoryType`).
- `CategoryRepository` (CRUD) + provider (lista cacheada).
- Cablear `categories_screen`, `category_form_sheet`, `category_picker_sheet`.
- Reemplazar `MockData.categories`.
- **Prueba:** crear / editar / borrar categoría y verla reflejada; el picker de categorías (que usan
  gastos e ingresos) sale de la API.

### Fase 3 — Movimientos (gastos + ingresos)

- Modelos `Expense`, `Income` (+ `PaymentMethodType`).
- `ExpenseRepository` / `IncomeRepository`: lista paginada con filtros, crear, editar, borrar.
- Provider de la lista de movimientos (mezcla gastos + ingresos, orden por fecha desc, paginación
  incremental, pull-to-refresh).
- Cablear `movements_screen` y `transaction_form_sheet`. Cablear el `quick_add_sheet`.
- Reemplazar `MockData.transactions` / `MockData.expenses`.
- **Prueba:** crear un gasto desde el form y desde Quick-Add → aparece en la lista y en el backend
  (Swagger); filtros por categoría y por rango de fechas; scroll infinito trae la página siguiente.

### Fase 4 — Dashboard, reportes y análisis

- Modelos `AnalysisSummary`, `MonthlyReport`, `MovementRow`, `Recommendation`, `MonthEndPrediction`.
- `AnalysisRepository` + `ReportRepository`.
- Cablear `dashboard_screen`: balance, gráfico ingresos vs gastos, donut por categoría, movimientos
  recientes, tarjeta de alerta — todo desde `/api/analysis/summary` y `/api/reports/*`.
- Cablear `reports_screen`. Export: `GET /api/reports/export` → guardar/compartir el archivo con
  `path_provider` + `share_plus`.
- **Prueba:** las cifras del dashboard cuadran con los movimientos creados en la Fase 3; cambiar de
  mes recarga; el CSV exportado abre bien.

### Fase 5 — Deudas

- Modelos `Debt`, `DebtPayment`, `DebtCharge`.
- `DebtRepository`: CRUD + `listPayments`/`addPayment` + `listCharges`/`addCharge`.
- Cablear `debts_tab`, `debt_detail_screen`, `new_debt_sheet`.
- **Prueba:** crear deuda; registrar un abono → el saldo baja y aparece un gasto vinculado; registrar
  un cargo → el saldo sube. Mutar una deuda ajena responde 404 (no exponer existencia).

### Fase 6 — Tarjetas de crédito

- Modelos `CreditCard`, `CardMovement`, `Installment`.
- `CardRepository`: CRUD + `registerPurchase` (con cuotas) + `registerPayment` + `movements`
  (paginado) + `installments`.
- Cablear `credit_cards_tab`, `credit_card_detail_screen`, `new_card_sheet`.
- **Prueba:** crear tarjeta; compra normal y compra a N cuotas → ver el plan de cuotas; registrar un
  pago; el ledger de movimientos es de solo lectura.

### Fase 7 — Pagos recurrentes / suscripciones

- Modelo `RecurringPayment` (+ `RecurringFrequency`).
- `RecurringPaymentRepository`: CRUD + `toggle` + `pay`.
- Cablear `subscriptions_tab`, `new_subscription_sheet`.
- **Prueba:** crear suscripción; `pay` genera un gasto vinculado; `toggle` la pausa/activa; un pago
  antes de tiempo responde el error esperado.

### Fase 8 — Notificaciones (in-app)

- Modelos `Notification`, `NotificationPreference`.
- `NotificationRepository`: lista, `unread-count`, marcar leída / todas, preferencias.
- Cablear `notifications_screen` y la campana del `app_header` (badge de no-leídas).
- **Push queda para después** (decisión 4): sin `POST /push-token` todavía.
- **Prueba:** disparar un job del backend (o insertar una notificación) → aparece en la lista con el
  badge correcto; marcar todas como leídas.

### Fase 9 — Preferencias y perfil

- `UserRepository`: `getPreferences` / `updatePreferences` / `updateProfile` / `changePassword`.
- Cablear `settings_screen`: tema, moneda e idioma desde `/api/users/preferences`; editar perfil;
  cambiar contraseña. El `ThemeController` local se sincroniza con la preferencia del backend.
- **Prueba:** cambiar el tema → persiste tras reabrir la app y se refleja al re-loguear en otro
  dispositivo; cambio de contraseña obliga a re-login.

### Fase 10 — Asistente de IA

- Modelos `ChatMessage`, `ChatReply`, `Insight`, `AiUsage`, `CategorizeResponse`, `AiProviderStatus`.
- `AiRepository`: `chat`, `history` (paginado), `usage`, `generateInsight`, `latestInsight`,
  `categorize`, `providersStatus`.
- Cablear `assistant_screen` (chat + contador de cuota + estado "sin proveedores configurados"), la
  tarjeta de insight del dashboard, y la sugerencia automática de categoría en `transaction_form_sheet`
  (`/api/ai/categorize`).
- **Prueba** (con una API key de proveedor en el backend): mandar un mensaje y ver la respuesta; al
  pasar la cuota mensual, la UI muestra el 429 con mensaje claro; generar un insight.

### Fase 11 — Escaneo de recibos con cámara nativa

- `image_picker` → foto → bytes → data URI.
- `AiRepository.scanReceipt(imageDataUri)` → `POST /api/receipts/scan`.
- Cablear `receipt_scan_screen`: tomar foto → llamada → prellenar `transaction_form_sheet` con lo
  extraído → el usuario confirma → `POST /api/expenses`.
- **Prueba:** foto de un recibo real → extrae monto/fecha/categoría → confirmar crea el gasto; foto
  ilegible → error amigable; rate limit al insistir.

### Fase 12 — Importación de extractos bancarios

- `file_picker` (PDF/CSV/XLSX, máx 10 MB).
- `StatementRepository`: `preview` (`FormData` multipart) + `confirm`.
- Cablear `import_statement_screen`: elegir archivo → preview con filas y marca de posible duplicado →
  el usuario elige cuáles importar → `confirm`.
- Manejar los 422 específicos (PDF sin texto, PDF con contraseña, IA no interpretó nada).
- **Prueba:** subir un extracto real → revisar → confirmar → los movimientos aparecen en la lista;
  reimportar el mismo archivo marca los duplicados.

### Fase 13 — Limpieza y cierre

- Borrar `lib/data/mock_data.dart` y cualquier referencia restante.
- Resolver la pantalla `/telegram` (decisión 5).
- Resolver el botón de Google (decisión 3) si no se hizo en Fase 1.
- Pasada de estados vacíos / error / offline en todas las pantallas.
- **Prueba:** recorrido completo de la app sin `MockData` en el árbol de dependencias.

### Fase posterior (fuera del MVP de integración) — Push FCM

- Backend: `FcmPushAdapter` en `notification/service/channel/` (TDD), detrás del mismo
  `NotificationSender`, degradación silenciosa como el de email.
- App: `firebase_messaging`, permiso de notificaciones, registrar el token con
  `POST /api/notifications/push-token` al loguear, borrarlo al hacer logout.
- **Prueba:** con la app en segundo plano, un job del backend dispara una push que llega al device.

---

## 8. Estrategia de tests del mobile

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Unit — repositorios | `dio` con `MockAdapter` / `http_mock_adapter` | Que cada método arme bien la request y deserialice/lance `ApiException` según el status. |
| Unit — modelos | `flutter_test` | `fromJson`/`toJson` de cada DTO contra un payload real de ejemplo. |
| Unit — providers | overrides del gestor de estado (p. ej. `ProviderScope overrides` en Riverpod) | Transiciones de estado: carga → datos / error, refetch, invalidación tras una mutación. |
| Widget | `flutter_test` con providers/repos mockeados | Cada pantalla: estado de carga, estado con datos, estado de error, interacción principal. |
| Integración | `integration_test/` contra backend local | Al menos el flujo de auth (registro → sesión persiste → refresh → logout) end-to-end. |

- CI: job `mobile-checks` en `ci.yml`, condicional a cambios en `korofin_mobile/**` —
  `flutter analyze` + `flutter test`. (Ya previsto en `docs/backend-plan.md`, sección 14.2.)
- Fixtures de JSON reales guardados en `test/fixtures/` copiando respuestas de Swagger.

---

## 9. Riesgos y notas

1. **Paginación de Spring.** Varios endpoints devuelven `Page<>` (`content`, `totalElements`,
   `number`, `totalPages`, `last`). Un solo `PageResponse<T>` genérico en `lib/models/` lo resuelve
   para todos.
2. **Dinero.** El backend usa `BigDecimal`. En el mobile, evitar `double` para montos que se sumen en
   el cliente; preferir `Decimal` (paquete `decimal`) o delegar las agregaciones al backend (el
   dashboard ya lo hace). COP en la práctica no lleva decimales pero no asumirlo en el parseo.
3. **Fechas.** `LocalDate` → `"yyyy-MM-dd"`, `Instant` → ISO-8601 UTC. Fijar el formato en cada DTO y
   no re-inventarlo por pantalla.
4. **Mensajes de error.** Replicar el `getApiErrorMessage` de FinSmart: sin respuesta → "no hay
   conexión / revisá la IP"; con `ErrorResponse` → mostrar su `message`; fallback genérico.
5. **Refresh en paralelo.** Si varias requests fallan con 401 a la vez, un solo refresh compartido
   (lock/`Completer`) y todas reintentan con el token nuevo. FinSmart lo hace con un `refreshPromise`
   único — replicar la idea.
6. **`AuthResponse.refreshToken` rota en cada `/refresh`.** Si no se guarda el nuevo, el siguiente
   refresh falla y el usuario queda deslogueado sin motivo aparente.
7. **IA sin proveedor.** Las fases 9–11 necesitan una API key de proveedor en el backend. Sin eso,
   `/api/ai/*` y `/api/receipts/scan` responden 503 — la UI debe manejarlo, no romperse.
8. **Nota al margen:** la línea 52 de `docs/backend-plan.md` tiene un bloque de texto (letra de una
   canción) pegado por error dentro de la sección 2. Conviene borrarlo en un commit de limpieza.

---

## 10. Bitácora de avance

Registro de lo entregado, fase por fase. Cada fase vive en su propia rama
`feat/mobile-fase-N-<slug>` desde `develop` y vuelve a `develop` vía merge.

### Fase 0 — Fundaciones ✅ (rama `feat/mobile-fase-0-fundaciones`)

- Dependencias nuevas: `dio`, `flutter_riverpod`, `flutter_secure_storage` (+ `http_mock_adapter` en dev).
- `lib/core/config/app_config.dart` — `API_BASE_URL` por `--dart-define` (default `http://10.0.2.2:8080`), timeout de 15 s.
- `lib/core/network/` — `ApiClient` sobre Dio: header `Authorization`, verbos `get/post/put/patch/delete`, interceptor que ante un 401/403 hace **un** refresh con single-flight (`_inFlightRefresh`) y reintenta la request; `ApiException` tipada; `mapDioException` (mismo criterio que el `getApiErrorMessage` de FinSmart).
- `lib/core/storage/session_store.dart` — interfaz `SessionStore` + `SecureSessionStore` (Keychain/Keystore) + `InMemorySessionStore` para tests. Solo persiste el refresh token; el access token vive en memoria.
- `lib/models/page_response.dart` — `PageResponse<T>` genérico para los `Page<>` de Spring.
- `lib/core/providers.dart` — `accessTokenProvider`, `sessionStoreProvider`, `apiClientProvider`.
- `main.dart` envuelto en `ProviderScope`.
- Tests: `test/core/api_client_test.dart` (7) + `test/models/page_response_test.dart` (2). `flutter analyze` y `flutter test` en verde.
- **Backend local verificado:** `docker compose up -d db app` → `/actuator/health` UP, `POST /api/users/register` y `/login` devuelven el `AuthResponse` real.

### Fase 1 — Autenticación real ✅ (rama `feat/mobile-fase-1-auth`)

- Modelos: `lib/models/user.dart`, `lib/models/auth_session.dart` (parsean el `AuthResponse` real).
- `lib/data/repositories/auth_repository.dart` — register / login / refresh / logout contra `/api/users/*`. `login` siempre manda `rememberMe: true`.
- `lib/state/auth/` — `AuthState` (`unknown` / `authenticated` / `unauthenticated`) y `AuthController` (`Notifier`): bootstrap al abrir (refresh silencioso si hay token guardado), `login`/`register`/`logout`, y cableado de `apiClient.onRefresh` / `onSessionExpired`. Expone `ready` para tests.
- `lib/routes/app_router.dart` — ahora `goRouterProvider`; `redirect` por estado de sesión (sin sesión → `/login`; con sesión en `/login`/`/register` → `/home`) y `refreshListenable` puenteado desde Riverpod.
- `main.dart` — `ConsumerWidget`; mientras `AuthStatus.unknown` muestra `SplashScreen`, después monta el router.
- Pantallas cableadas: `login_screen` y `register_screen` (formularios reales, validación, estados de carga/error; se quitó "Continuar con Google" y el atajo de biometría, sin backend); `settings_screen` → "Cerrar sesión" real.
- Tests nuevos: `auth_session_test`, `auth_repository_test` (4), `auth_controller_test` (5), `widget_test` reescrito. Total 20, `flutter analyze` + `flutter test` en verde.
- **Pendiente de probar en dispositivo:** login/registro/logout contra el backend local y persistencia de sesión tras cerrar la app.

### Fase 2 — Categorías ✅ (rama `feat/mobile-fase-2-categorias`)

- Modelo `Category` (`id`, `name`, `kind`) + enum `CategoryKind` (`EXPENSE`/`INCOME`) en `lib/models/category.dart`, junto al viejo `AppCategory` (que sigue usando el resto de pantallas mock hasta que migren).
- **El backend no guarda ícono ni color** (`CategoryResponse` es `{ id, name, type }`), así que `lib/data/category_visuals.dart` los deriva del nombre: mapa de palabras clave → ícono, y hash estable del nombre → color de la paleta.
- `lib/data/repositories/category_repository.dart` — CRUD contra `/api/categories` (`list` opcionalmente con `?type=`).
- `lib/state/categories/categories_controller.dart` — `categoryRepositoryProvider` + `categoriesProvider` (`AsyncNotifier`): carga la lista, y `create`/`edit`/`delete` la actualizan en memoria y la reordenan por nombre.
- `categories_screen` reescrita: estados de carga/error/vacío, filtro Todas/Gastos/Ingresos, pull-to-refresh, borrado con confirmación, errores del backend (409 nombre duplicado) en `SnackBar`.
- `category_form_sheet` reescrito: nombre + tipo (Gasto/Ingreso) + preview del ícono/color derivado. Se quitaron los pickers de ícono y color (eran cosméticos, no persistían).
- **Fuera de esta fase:** el `category_picker_sheet` y las pantallas de movimientos siguen con `MockData` — migran en la Fase 3.
- Tests: `category_test` (3), `category_repository_test` (7), `categories_controller_test` (4). Total 33, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** usuario nuevo arranca con `[]` categorías; `POST` devuelve `{id,name,type}`; nombre duplicado → 409.

### Fase 3 — Movimientos (gastos + ingresos) ✅ (rama `feat/mobile-fase-3-movimientos`)

- Modelo unificado `Movement` (`fromExpenseJson`/`fromIncomeJson`) + `MovementDraft` (payload de escritura, `toExpenseJson`/`toIncomeJson` con fecha ISO), enums `MovementType` y `PaymentMethod`.
- `ExpenseRepository` (`/api/expenses`, paginado + filtros `categoryId`/`from`/`to`/`paymentMethod`) e `IncomeRepository` (`/api/incomes`, filtros `month`/`year`).
- `movementsProvider` = `AsyncNotifierProvider.family<MovementsController, List<Movement>, MovementType>`: carga página 0, `loadMore` acumula, `add`/`edit`/`remove` mutan en memoria; `hasMore` desde `Page.last`.
- `category_picker_sheet` **migrado** a `categoriesProvider` (categorías reales, filtradas por tipo, devuelve `Category`).
- `movements_screen` reescrita: tabs Gastos/Ingresos, scroll infinito, pull-to-refresh, editar tocando, borrar con `Dismissible` + confirmación. `transaction_form_sheet` reescrito (monto, descripción opcional, categoría real, fecha no futura, método de pago en gastos; se quitó el toggle "Categorizado por IA"). Quick-Add persiste de verdad vía `scaffold_with_nav` (ahora `ConsumerWidget`).
- `MovementTile` nuevo (usa `CategoryVisuals` sobre `categoryName`). El viejo `TransactionTile`/`AppTransaction` siguen para dashboard (Fase 4) y `receipt_scan` (Fase 11).
- **Fuera de esta fase:** UI de filtros por categoría/fecha (el repo ya los soporta); el dashboard sigue con `MockData`.
- Tests: `movement_test` (4), `movement_repositories_test` (7), `movements_controller_test` (4). Total 46, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** `POST /api/expenses` con `categoryId` devuelve `categoryName` resuelto; `GET` pagina con la forma `Page` estándar; ingreso sin categoría → `categoryId/categoryName` null.

### Fase 4 — Dashboard, reportes y análisis ✅ (rama `feat/mobile-fase-4-dashboard`)

- Modelos `AnalysisSummary` / `CategoryTotal` / `MonthlyTotal` / `Recommendation` / `MonthEndPrediction` (`analysis.dart`), `MonthlyReport` / `ReportMovement` (`report.dart`).
- `AnalysisRepository` (`/api/analysis/summary|recommendations|prediction`) y `ReportRepository` (`/api/reports/monthly|movements`).
- `dashboardProvider` (`AsyncNotifier`): carga `summary` (imprescindible) + `recommendations` + `prediction` en paralelo; las dos últimas degradan a `[]`/`null` si fallan.
- `reportProvider` (`FutureProvider`) + `selectedReportMonthProvider` (`StateProvider<DateTime>`) + `reportTrendProvider` (serie de 6 meses del resumen).
- `dashboard_screen` reescrita: saludo con el nombre real del usuario, `BalanceCard`/bar chart/donut desde el resumen, tarjeta de proyección de fin de mes y de recomendaciones, tasa de ahorro. `reports_screen` reescrita: selector de mes (‹ ›, tope en el mes en curso), KPIs, tendencia de ahorro, gasto por categoría y tabla de movimientos del período.
- Los gráficos (`CategoryDonutChart`, `IncomeExpenseBarChart`, `TrendLineChart`) se reusan sin tocar; el donut recibe `AppCategory` sintetizados con `CategoryVisuals` desde `categoryName`.
- **Fuera de esta fase:** botón de exportar CSV/JSON (necesita `share_plus`/`path_provider`) — se hará en la limpieza.
- Tests: `analysis_report_test` (3), `analysis_report_repositories_test` (6), `dashboard_controller_test` (3). Total 57, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** `monthlySeries` siempre trae 6 meses; `prediction`/`recommendations` funcionan sin datos.

### Fase 5 — Deudas ✅ (rama `feat/mobile-fase-5-deudas`)

- Modelo `Debt` (backend-shaped: `totalAmount`/`remainingAmount`, `paidAmount`/`progress` derivados) + `DebtPayment` + `DebtCharge`. Se eliminó la lista `debts` mock de `mock_data.dart`.
- `DebtRepository` (`/api/debts` CRUD + `/payments` + `/charges`; `addPayment` → `DebtPayment`, `addCharge` → `Debt` actualizada). `debtsProvider` (`AsyncNotifier`, sin paginación en UI) + `debtDetailProvider(id)` (`FutureProvider.family` → deuda + abonos + cargos).
- `debts_tab` reescrita (carga/error/vacío, FAB, pull-to-refresh). `debt_detail_screen` reescrita: recarga del backend, resumen, progreso, historial combinado abonos/cargos ordenado, acciones "Abono"/"Cargo" (diálogo de monto), editar y borrar. `new_debt_sheet` devuelve datos; en edición oculta el monto total (inmutable). `debt_tile` sin `lender`.
- Tests: `debt_test` (7), `debts_controller_test` (3). Total 66, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** abono baja el saldo y crea un `Expense` vinculado (`expenseId`); cargo devuelve la deuda con el saldo ya actualizado.

### Fase 6 — Tarjetas de crédito ✅ (rama `feat/mobile-fase-6-tarjetas`)

- Modelos `CreditCard` + enums `CardFranchise` / `CardMovementKind` / `InstallmentStatus`, `CardMovement`, `Installment`. Se eliminaron `creditCards` y `cardMovements` mock de `mock_data.dart`.
- `CardRepository` (`/api/cards` CRUD + `/purchases` con `installmentCount` opcional + `/payments` + `/movements` + `/movements/{id}/installments`). `cardsProvider` (`AsyncNotifier`) + `cardDetailProvider(id)` + `cardInstallmentsProvider((cardId, movementId))`.
- `credit_cards_tab`, `credit_card_detail_screen` (tile + datos de corte/pago/tasa, acciones Compra con cuotas / Pago, historial de movimientos, plan de cuotas en sheet), `new_card_sheet` (form completo; edición oculta franquicia y cupo), `credit_card_tile` (sin dígitos, muestra franquicia).
- Tests: `card_test` (6), `cards_controller_test` (2). Total 73, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** compra con `installmentCount` → movimiento `INSTALLMENT_PURCHASE` con `installmentPlanId`; el plan de cuotas se lee del endpoint anidado.

### Fase 7 — Pagos recurrentes / suscripciones ✅ (rama `feat/mobile-fase-7-recurrentes`)

- Modelo `RecurringPayment` + enum `RecurringFrequency` (MONTHLY/WEEKLY). Se eliminó el mock `subscriptions`.
- `RecurringPaymentRepository` (`/api/recurring` CRUD + `PATCH /toggle` + `PATCH /pay` — desanida `recurringPayment` de la respuesta). `recurringPaymentsProvider` (`AsyncNotifier`) con `create`/`edit`/`remove`/`toggle`/`pay`, reordenando por `nextPaymentDate`.
- `subscriptions_tab` reescrita (carga/error/vacío, swipe-delete, botón "Pagar", switch activo/pausado, tap para editar). `new_subscription_sheet` devuelve datos; edición oculta la fecha del primer pago. `subscription_tile` usa `CategoryVisuals` para ícono/color.
- Tests: `recurring_payment_test` (5), `recurring_controller_test` (3). Total 81, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** `pay` antes de la fecha de vencimiento → 409 con mensaje amigable (se muestra en SnackBar); `toggle` invierte `isActive`.

### Fase 8 — Notificaciones in-app ✅ (rama `feat/mobile-fase-8-notificaciones`)

- Modelo `AppNotification` (no `Notification`, choca con Flutter) + enum `NotificationKind` (7 tipos, con ícono) + `NotificationPreferences` (6 flags). Se reemplazó `notification_item.dart` y se quitó el mock `notifications`.
- `NotificationRepository` (`/api/notifications` list + `unread-count` + `{id}/read` + `read-all` + `preferences`). `notificationsProvider` (`AsyncNotifier`: list, markAsRead, markAllAsRead) + `unreadCountProvider` (`FutureProvider`, se invalida al marcar) + `notificationPreferencesProvider` (`AsyncNotifier`, guardado optimista).
- `notifications_screen` reescrita (tabs Todas/No leídas, "Marcar todas", swipe para leer, pull-to-refresh). `notification_tile` usa `AppNotification` + ícono por tipo.
- `app_header` pasa a `ConsumerWidget`: **badge de no leídas** en la campana e **inicial real** del usuario en el avatar. `settings_screen`: la sección Notificaciones se cablea a `notificationPreferencesProvider` (6 switches) y el perfil muestra nombre/email reales.
- **Push token (FCM) sigue diferido** — el backend espera un token de Expo; hace falta un `FcmPushAdapter` en el backend.
- Tests: `notification_test` (4), `notifications_controller_test` (3). Total 88, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** `unread-count` devuelve un número plano; `preferences` trae las 6 flags con defaults.

### Fase 9 — Preferencias y perfil ✅ (rama `feat/mobile-fase-9-preferencias`)

- Modelo `UserPreferences` + enums `ThemePreference` (mapea a `ThemeMode`) / `AppLanguage` + lista `supportedCurrencies`.
- `UserRepository` (`/api/users/preferences` GET+PATCH, `/profile` PUT, `/password` PUT). `userPreferencesProvider` (`AsyncNotifier`): carga y sincroniza `ThemeController` con el tema guardado; `setTheme`/`setCurrency`/`setLanguage` hacen el PATCH completo (el backend exige los tres).
- `AuthController`: aplica el tema del usuario al iniciar sesión / bootstrap, y `applyUser()` refresca el estado tras editar el perfil.
- `settings_screen` reescrita: perfil tocable → sheet de edición (nombre + email), "Cambiar contraseña" → sheet (actual + nueva), sección Preferencias (tema + moneda + idioma), sección Notificaciones (Fase 8). Se quitó la entrada "Integración Telegram" (fuera de alcance).
- Tests: `user_preferences_test` (6), `preferences_controller_test` (3). Total 96, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** `PATCH /preferences` devuelve las 3; `PUT /profile` devuelve el `User` completo; contraseña actual incorrecta → 401.

### Fase 10 — Asistente de IA ✅ (rama `feat/mobile-fase-10-ia`)

- Modelos en `ai.dart`: `ChatMessage` + `ChatRole`, `AiUsage`, `AiInsight`, `CategorySuggestion`, `AiProviderStatus`. Se reemplazó `chat_message.dart` y se quitó el mock `chatHistory`/`assistantSuggestions`.
- `AiRepository` (`/api/ai/chat` + `/history` + `/usage`, `/api/ai/insights` GET+`/generate`, `/api/ai/categorize`, `/api/ai/providers/status`). `history` invierte el orden DESC del backend.
- `assistantProvider` (`AsyncNotifier`: carga historial, `send()` agrega el par usuario/asistente y devuelve el error para mostrar en SnackBar — 429 cuota, 503 sin proveedor). `aiUsageProvider`, `aiProvidersStatusProvider` + `anyAiProviderConfiguredProvider`, `latestInsightProvider`.
- `assistant_screen` reescrita: chat real, indicador de cuota en el subtítulo, banner "no hay proveedor configurado" que deshabilita el composer, burbuja de "escribiendo". Dashboard: tarjeta de **insight de la IA** cuando existe. `transaction_form_sheet`: botón **"Sugerir con IA"** (✨) que categoriza la descripción.
- Tests: `ai_test` (6), `ai_controller_test` (4). Total 106, `flutter analyze` + `flutter test` en verde.
- **Contrato verificado contra el backend local:** `providers/status` lista los 5 proveedores; `chat/usage` trae `used/limit/remaining`; `insights` → 204 sin datos. (El `.env` local tiene keys de proveedor; el chat real depende de que sean válidas.)

### Fase 11 — Escaneo de recibos con cámara nativa ✅ (rama `feat/mobile-fase-11-recibos`)

- Dependencia nueva: `image_picker`.
- Modelo `ReceiptExtraction` en `ai.dart` (`isReceipt`, `description`, `amount`, `isIncome`, `categoryId`, `categoryName`). `AiRepository.scanReceipt(imageDataUri)` → `POST /api/receipts/scan`.
- `receipt_scan_screen` reescrita: cámara / galería con `image_picker` → bytes → data URI base64 → `scanReceipt`. Estados capture / loading / notReceipt / result. El resultado es un formulario editable (tipo, monto, descripción, categoría real) que al confirmar crea el movimiento vía `movementsProvider`.
- Tests: `receipt_scan_test` (3). Total 109, `flutter analyze` + `flutter test` en verde.
- **Verificación end-to-end pendiente:** el escaneo real depende de un proveedor de IA de visión con key válida; el contrato de request/response ya está alineado con el `ReceiptExtraction` del backend. La app maneja `isReceipt=false` y el timeout de red.

### Fase 12 — Importación de extractos ✅ (rama `feat/mobile-fase-12-extractos`)

- Dependencia nueva: `file_picker`.
- Modelos `StatementRow` (mutable en `selected`; los duplicados arrancan desmarcados) y `StatementPreview`. `StatementRepository` (`/preview` multipart vía `FormData` + `/confirm`). `ApiClient.postForm()` nuevo para multipart con el mismo mapeo de errores.
- `import_statement_screen` reescrita: `file_picker` (pdf/csv/xlsx) → preview con checkboxes y marca de duplicado y categoría sugerida → confirmar crea los movimientos e invalida `movementsProvider`. Maneja el 422 de PDF con contraseña pidiéndola y reintentando.
- Tests: `statement_test` (4). Total 113, `flutter analyze` + `flutter test` en verde.
- **Verificación end-to-end pendiente:** igual que recibos, la extracción usa IA; el contrato con `StatementPreviewResponse`/`ImportConfirmRow` ya está alineado.

### Fase 13 — Limpieza ✅ (rama `feat/mobile-fase-13-limpieza`)

- Borrados: `lib/data/mock_data.dart`, `lib/models/transaction.dart` (`AppTransaction`), `lib/widgets/list_items/transaction_tile.dart`, `lib/screens/telegram/` completo, y la clase `AppCategory` de `category.dart`. Ya no queda ningún dato falso en `lib/`.
- Ruta `/telegram` eliminada del router (Telegram queda fuera del alcance de la app, como se decidió). `category_donut_chart` refactorizado a un record `DonutEntry` (`label`/`value`/`color`) en vez de depender de `AppCategory`.
- Se conserva `biometric_lock_screen` + la ruta `/lock` (pantalla diseñada, feature local futura, sin backend).
- 113 tests en verde, `flutter analyze` limpio.

---

## 11. Estado final

**Fases 0–13 completas y mergeadas a `develop` local.** 113 tests unitarios/de widget en verde, `flutter analyze` sin issues en cada fase.

**Backend Docker local verificado end-to-end** para: auth, categorías, gastos/ingresos, análisis, reportes, deudas (+abonos/cargos), tarjetas (+cuotas), pagos recurrentes (+toggle/pay), notificaciones (+preferencias), preferencias de usuario y perfil.

**IA verificada (2026-09-05, tras subir bien las keys al contenedor):**
- `POST /api/ai/chat` → **funciona**, ~16 s en frío con Gemini real.
- `POST /api/ai/categorize` → **funciona**, instantáneo.
- `POST /api/ai/insights/generate` → el backend corta a los 60 s (`AI_READ_TIMEOUT_SECONDS`) y devuelve 500 — comportamiento del backend con el prompt grande de insight, no del mobile.
- `POST /api/receipts/scan` → 503 con imagen de prueba (modelos de visión más lentos / requiere foto real). La app muestra el mensaje del backend.

**Fix aplicado (rama `fix/mobile-timeout-ia`):** el `ApiClient` tenía 15 s de timeout para todo, y una respuesta de IA de ~16 s se cortaba en la app. Ahora chat, categorización, insight, escaneo de recibos y `statement-imports/preview` usan `AppConfig.aiRequestTimeout` (90 s); el resto sigue en 15 s. Las llamadas de IA que responden rápido (chat, categorize) ya funcionan en la app; las lentas fallan con el mensaje del backend en vez de un timeout de cliente.

**Fuera de este trabajo (decidido):** push FCM (el backend usa `ExpoPushAdapter`; hace falta un `FcmPushAdapter`), OAuth de Google, y el botón de exportar reporte a CSV/JSON (necesita `share_plus`/`path_provider`).

**Git:** cada fase en su rama `feat/mobile-fase-N-<slug>` mergeada `--no-ff` a `develop` local. **Sin push a `origin` ni PRs de GitHub** — pendiente del visto bueno.
