# Plan del backend de KoroFin — investigación y diseño

> **Estado:** documento de planeación. No contiene código de implementación. Es la fuente de verdad
> para los agentes que implementen el backend nuevo de KoroFin (Java 21 + Spring Boot).
>
> **Fuente:** mapeo completo del backend viejo `smart-finance-backend` (proyecto FinSmart, package
> base `com.smartfinance.backend`), hecho con CodeGraph sobre el índice en `FinSmart/.codegraph/`,
> más lectura directa de `pom.xml`, `application*.properties`, migraciones Flyway, workflows de
> GitHub Actions y `docs/convenciones.md`. FinSmart **no fue modificado** en este proceso.
>
> KoroFin reemplaza completamente a FinSmart. Decisión explícita del dueño del proyecto: **solo
> habrá app móvil (Flutter, `korofin_mobile/`, paquete Android `com.korofin`), no habrá frontend
> web**. Esto tiene implicaciones directas de seguridad y de simplificación que se detallan abajo.

---

## Índice

1. [Resumen de decisiones clave](#1-resumen-de-decisiones-clave)
2. [Mapeo del backend viejo por dominio](#2-mapeo-del-backend-viejo-por-dominio)
3. [Seguridad: flujo actual y flujo nuevo mobile-only](#3-seguridad-flujo-actual-y-flujo-nuevo-mobile-only)
4. [Integraciones de IA](#4-integraciones-de-ia)
5. [Integración de Telegram](#5-integración-de-telegram)
6. [Escaneo de recibos y rate limiting](#6-escaneo-de-recibos-y-rate-limiting)
7. [Reportes](#7-reportes)
8. [Testing actual](#8-testing-actual)
9. [Migraciones Flyway](#9-migraciones-flyway)
10. [Variables de entorno completas (FinSmart, tal como existen hoy)](#10-variables-de-entorno-completas-finsmart-tal-como-existen-hoy)
11. [Deploy actual (Docker + CI/CD)](#11-deploy-actual-docker--cicd)
12. [Nueva estructura de paquetes de KoroFin](#12-nueva-estructura-de-paquetes-de-korofin)
13. [Qué se elimina / simplifica en el refactor](#13-qué-se-elimina--simplifica-en-el-refactor)
14. [Plan Docker / CI-CD para KoroFin](#14-plan-docker--cicd-para-korofin)
15. [Plan de tests para KoroFin](#15-plan-de-tests-para-korofin)

---

## 1. Resumen de decisiones clave

| Decisión | Elegido | Por qué |
|---|---|---|
| Estructura de paquetes | Capa técnica primero, dominio adentro (`controller/user`, `service/expense`, ...) | Pedido explícito del dueño. Ver nota de tensión con las prácticas recomendadas en la [sección 12](#nota-tensión-con-las-prácticas-recomendadas-de-spring-boot). |
| Nombres de paquetes de dominio | En **inglés** (`user`, `expense`, `debt`, `card`, `income`, `statement`, `ai`, `report`, `notification`, `recurringpayment`, `integration`, `analysis`) | FinSmart mezcla paquetes en español (`usuario`, `gastos`) con clases en inglés (`UserController`). `docs/convenciones.md` exige identificadores en inglés; los nombres de paquete son identificadores. Se corrige la inconsistencia en KoroFin. |
| Auth mobile-only | Bearer access token + refresh token en el body JSON (secure storage del device), **sin cookies, sin CSRF** | FinSmart ya tiene esta rama implementada para clientes `X-Client: mobile` — se convierte en el único camino en vez de una rama condicional. |
| CORS | Se elimina o se deja opcional/vacío | Un cliente móvil nativo no está sujeto a CORS de navegador; la config actual existía solo para el origin del frontend Next.js. |
| Proveedores de IA | Se preserva el patrón Strategy/Registry tal cual (5 proveedores: NVIDIA, Gemini, OpenCode, OpenRouter, Groq) | Ya es limpio, extensible y con fallback en cascada. No hace falta rediseñarlo. |
| Testcontainers para Postgres | Se agrega (FinSmart no lo tenía) | Las migraciones Flyway usan sintaxis específica de Postgres; H2 en modo compatibilidad es frágil. Mejora real de calidad de tests. |
| TDD estricto | Aplica a la implementación (fase siguiente) | El usuario tiene "Strict TDD Mode: enabled" en su configuración global — los agentes de implementación deben escribir el test antes que el código de producción. |
| Telegram | Se conserva como integración opcional | No es "frontend web" — es un canal servidor-a-servidor (n8n) independiente de la app. Se mantiene, marcado como opcional/desactivable (ya lo es: `TELEGRAM_WEBHOOK_SECRET` vacío = integración apagada). |

---

## 2. Mapeo del backend viejo por dominio

FinSmart organiza `com.smartfinance.backend` en 12 paquetes de dominio, cada uno con sus propias
subcapas (`controller/`, `service/`, `repository/`, `model/{dto,entity}`, `mapper/`, `exception/`).
Es Spring Boot **4.0.7** sobre Java 21, con Jackson 3 (`tools.jackson.*`, no el clásico
`com.fasterxml.jackson` salvo en `jjwt-jackson`, fijado aparte por CVEs), MapStruct 1.6.3, Lombok,
Flyway, PDFBox 3 + Apache POI (`poi-ooxml`) para extractos bancarios, y `jjwt` 0.12.6 para JWT.

### 2.1 `usuario` (→ `user`)

- **Responsabilidad:** registro, login, sesión (access+refresh JWT), perfil, preferencias
  (tema/moneda/idioma).
- **Entidad:** `User` (email único, `passwordHash` con BCrypt fuerza 12, `active`, `lastLoginAt`,
  contador `aiChatUsed`/`aiChatPeriod` para la cuota mensual de IA, `theme`/`currency`/`language`).
  También `RefreshToken` (rotación por `jti` UUID, campo `rememberMe`).
- **Controller:** `UserController` — `/api/users`: `POST /register`, `POST /login`,
  `POST /refresh`, `GET /csrf`, `POST /logout`, `PUT /profile`, `PUT /password`,
  `GET /preferences`, `PATCH /preferences`.
- **Services:** `UserService` (orquesta registro/login/refresh/logout/perfil/contraseña/preferencias),
  `RefreshTokenService` (rotación de refresh tokens).
- **Repository:** `UserRepository`, `RefreshTokenRepository`.
- **Lógica no obvia:**
  - `login` borra el historial de chat de IA de tipo `CHAT` (no `INSIGHT`) en cada login exitoso —
    cada sesión nueva arranca el asistente con conversación en blanco, pero conserva los insights
    generados.
  - Ya existe una rama **mobile-ready**: si el header `X-Client: mobile` está presente, el refresh
    token viaja en el body JSON de `AuthResponse` en vez de una cookie `HttpOnly`, y esas requests
    quedan exentas de CSRF. Esta rama es exactamente el camino que KoroFin necesita como único flujo
    (ver [sección 3](#3-seguridad-flujo-actual-y-flujo-nuevo-mobile-only)).
  - `resolveRefreshToken`: cookie primero, body después — así ambos tipos de cliente conviven hoy.

### 2.2 `common`

- **Responsabilidad:** infraestructura transversal — seguridad, configuración, manejo global de
  errores, proyecciones JPA compartidas entre dominios.
- **`config/`:** `SecurityConfig` (filter chain, CORS, CSRF), `JwtProperties`
  (`@ConfigurationProperties` para `app.jwt.*`), `ClockConfig` (bean `Clock` inyectable — todos los
  servicios con lógica de fecha lo usan en vez de `LocalDate.now()`/`Instant.now()` directo, lo que
  hace testeable el tiempo), `SchedulingConfig` (`@EnableScheduling` condicional a
  `app.jobs.enabled`), `OpenApiConfig`, `RestClientConfig` (timeouts para llamadas a proveedores de
  IA), `AsyncConfig` (executors nombrados para email/push async).
- **`security/`:** `JwtAuthenticationFilter` (valida Bearer, puebla `SecurityContextHolder` con el
  `userId` como principal), `JwtService` (genera/valida JWT con `jjwt`, HS256), `SecurityUtils`
  (`getCurrentUserId()` estático, usado en *todos* los servicios de dominio), `RateLimitFilter`,
  `InMemoryRateLimiter`, `TelegramWebhookFilter`.
- **`exception/`:** `GlobalExceptionHandler` (`@RestControllerAdvice` centralizado — mapea ~25
  excepciones de dominio distintas a códigos HTTP específicos), `ErrorResponse`,
  `ResourceNotFoundException`.
- **`repository/`:** `MonthlyTotalProjection`, `UserLastActivityProjection` — proyecciones JPA
  compartidas por más de un dominio (p. ej. usadas tanto por `gastos` como por `ingresos`).

### 2.3 `deudas` (→ `debt`)

- **Responsabilidad:** deudas del usuario, sus pagos (abonos que reducen el saldo) y sus cargos
  (movimientos que lo incrementan, p. ej. intereses o nuevos consumos sobre la deuda).
- **Entidades:** `Debt`, `DebtPayment`, `DebtCharge`.
- **Controllers:** `DebtController` (`/api/debts`, CRUD), `DebtPaymentController`
  (`/api/debts/{debtId}/payments`), `DebtChargeController` (`/api/debts/{debtId}/charges`).
- **Services:** `DebtService`, `DebtPaymentService`, `DebtChargeService`.
- **Lógica no obvia:**
  - `DebtUpdateRequest` deliberadamente **no puede** cambiar `totalAmount` ni `remainingAmount` —
    el único lugar que cambia `remainingAmount` tras la creación es un pago o un cargo.
  - `DebtChargeService#createCharge` incrementa `remainingAmount` con un `UPDATE` atómico
    (`debtRepository.incrementRemainingAmount`), no con un patrón read-then-write, para evitar
    condiciones de carrera. Vuelve a leer la entidad después del `UPDATE` masivo porque este
    invalida el contexto de persistencia.
  - Un cargo (`DebtCharge`) **no** crea un `Expense` vinculado (no es un gasto en efectivo); un pago
    (`DebtPayment`) sí crea un `Expense` vinculado vía `debtPayment` (FK `ON DELETE SET NULL`).
  - Todas las mutaciones sobre una deuda ajena devuelven 404 (`ResourceNotFoundException`), nunca
    403 — para no filtrar la existencia de la deuda a quien no es su dueño.

### 2.4 `extractos` (→ `statement`)

- **Responsabilidad:** importación de extractos bancarios (PDF/CSV/XLSX) con extracción de
  movimientos asistida por IA, y detección de duplicados contra movimientos ya existentes.
- **Controller:** `StatementImportController` — `POST /api/statement-imports/preview`
  (`multipart/form-data`), `POST /api/statement-imports/confirm`.
- **Services:** `StatementImportService` (orquesta), `StatementAiExtractionService` (llama al
  `AiChatOrchestrator` para interpretar el texto extraído y devolver movimientos estructurados),
  `extraction/` (`PdfStatementTextExtractor` vía PDFBox, `XlsxStatementTextExtractor` vía Apache POI,
  `CsvStatementTextExtractor`, todos detrás de `StatementTextExtractor`/
  `StatementTextExtractionService`), `dedup/` (`DescriptionSimilarity`, `DuplicateDetector` —
  heurística de texto para avisar de un posible duplicado antes de confirmar la importación).
- **Excepciones específicas:** `UnsupportedStatementFileException`, `EmptyStatementTextException`
  (PDF sin texto legible, p. ej. un escaneo de imagen), `StatementPasswordException` (PDF protegido
  con contraseña incorrecta), `StatementExtractionException` (la IA no devolvió movimientos
  interpretables) — las tres últimas mapean a `422 Unprocessable Entity`: el request es válido, su
  contenido no se pudo procesar.
- **Límites:** `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=12MB`.

### 2.5 `gastos` (→ `expense`)

- **Responsabilidad:** gastos y categorías (compartidas entre gastos e ingresos).
- **Entidades:** `Expense`, `Category`, `CategoryType` (enum `EXPENSE`/`INCOME` — una tabla de
  categorías sirve a ambos dominios), `PaymentMethodType`.
- **Controllers:** `ExpenseController` (`/api/expenses`, CRUD con filtros vía
  `ExpenseSpecifications`), `CategoryController` (`/api/categories`, CRUD).
- **Services:** `ExpenseService`, `CategoryService`.
- **Repository:** `ExpenseRepository` (+ `CategoryTotalProjection` para totales agrupados por
  categoría), `CategoryRepository`.
- **Evento:** `ExpenseCreatedEvent` — publicado al crear un gasto, consumido por
  `OverspendAlertListener` (dominio `servicios`) para disparar la notificación de sobregasto sin
  acoplar `ExpenseService` a la lógica de notificaciones.
- **`Expense` tiene 3 FKs opcionales** hacia otros dominios (`recurringPayment`, `debtPayment`,
  `cardMovement`), todas `ON DELETE SET NULL` — un gasto generado automáticamente (pago de un
  servicio recurrente, abono a una deuda, compra con tarjeta) queda vinculado a su origen sin que
  borrar ese origen borre el historial del gasto.

### 2.6 `ia` (→ `ai`)

Ver [sección 4](#4-integraciones-de-ia) para el detalle del patrón de proveedores. Resumen de
superficie:

- **Controllers:** `AiChatController` (`/api/ai/chat` — chat, `GET /history`, `GET /usage`),
  `AiCategorizationController` (`/api/ai/categorize`), `AiInsightController`
  (`POST /api/ai/insights/generate`), `AiProviderStatusController`
  (`GET /api/ai/providers/status` — expone qué proveedores están configurados, nunca la API key),
  `AiUsageEventController` (`/api/ai/usage`), `ReceiptScanController` (`POST /api/receipts/scan`).
- **Services de dominio:** `AiChatService`, `AiCategorizationService`, `AiInsightService`,
  `AiUsageEventService`, `FinancialSummaryQueryService` (responde preguntas en lenguaje natural
  sobre las finanzas del usuario — usado también por Telegram), `ReceiptExtractionService`.
- **`ai/` (subpaquete de infraestructura de proveedores):** ver sección 4.

### 2.7 `ingresos` (→ `income`)

- **Responsabilidad:** ingresos del usuario. Estructuralmente el gemelo de `gastos` pero sin
  método de pago ni vínculos a tarjeta/deuda/servicio recurrente.
- **Entidad:** `Income`.
- **Controller:** `IncomeController` (`/api/incomes`, CRUD).
- **Service:** `IncomeService`. **Repository:** `IncomeRepository` (+ `IncomeSpecifications`,
  `IncomeCategoryTotalProjection`).

### 2.8 `integraciones` (→ `integration`)

Ver [sección 5](#5-integración-de-telegram). Hoy es Telegram exclusivamente; el nombre en plural
en inglés dejaría espacio a futuras integraciones sin reorganizar paquetes.

### 2.9 `reportes` (→ `report`)

Ver [sección 7](#7-reportes).

### 2.10 `servicios` (→ se separa en `notification` + `recurringpayment`, más un paquete de scheduling)

- **Responsabilidad actual (un solo paquete que mezcla dos conceptos):** pagos de servicios
  recurrentes (suscripciones, arriendo, etc.) **y** notificaciones (in-app, email, push) **y** los
  jobs programados que disparan ambas cosas.
- **Entidades:** `RecurringPayment`, `RecurringFrequency`; `Notification`, `NotificationPreference`,
  `PushToken`.
- **Controllers:** `RecurringPaymentController` (`/api/recurring` — CRUD + `PATCH /{id}/toggle` +
  `PATCH /{id}/pay`), `NotificationController` (`/api/notifications` — listar, `unread-count`,
  marcar leída/todas, preferencias, registrar/borrar `push-token`).
- **`service/notification/` (patrón adapter, uno por canal):** `NotificationDispatcher` (punto de
  entrada único — resuelve preferencias del usuario y abanica a los 3 canales), `NotificationSender`
  (interfaz) → `EmailNotificationSender` (Resend SMTP, se degrada en silencio si no hay
  `RESEND_API_KEY`/`MAIL_FROM`, corre en executor `@Async` dedicado), `PushNotificationSender`
  (interfaz) → `ExpoPushAdapter` (Expo Push API, también `@Async`, también degrada en silencio ante
  cualquier error — un canal caído nunca debe romper la notificación in-app ya creada).
- **`service/job/` (jobs `@Scheduled`, reemplazo nativo de workflows de n8n previos):**
  `PaymentReminderJob`, `WeeklySummaryJob`, `InactivityReminderJob`, `MonthEndPredictionJob`,
  `OverspendAlertListener` (listener de evento, no cron), `NotificationMessageFormatter`.
- **Decisión para KoroFin:** separar en dos dominios de negocio reales —
  `notification` (Notification, NotificationPreference, PushToken, NotificationController,
  NotificationService, NotificationDispatcher + senders) y `recurringpayment` (RecurringPayment,
  RecurringPaymentController, RecurringPaymentService) — y mover los jobs `@Scheduled` a un
  subpaquete de infraestructura de programación (p. ej. `service/scheduling/`), ya que cada job
  orquesta *varios* dominios a la vez (p. ej. `PaymentReminderJob` lee `RecurringPayment` y `Debt`,
  y despacha a través de `NotificationDispatcher`) y no pertenece limpiamente a uno solo.

### 2.11 `tarjetas` (→ `card`)

- **Responsabilidad:** tarjetas de crédito, sus movimientos (compras/pagos/interés), compras en
  cuotas y cierre de ciclo de facturación.
- **Entidades:** `CreditCard`, `CardMovement` (+ `CardMovementType`, `CardFranchise`),
  `InstallmentPlan`, `Installment` (+ `InstallmentStatus`).
- **Controllers:** `CreditCardController` (`/api/cards`, CRUD), `CardMovementController`
  (`/api/cards/{cardId}/purchases`, `/payments`, `GET /movements`,
  `GET /movements/{movementId}/installments`).
- **Services:** `CreditCardService`, `CardMovementService`, `AmortizationService` (cálculo de
  interés/cuotas), `CycleCloseService` (cierre de ciclo, ver abajo).
- **Job:** `CardCycleCloseJob` (cron diario, `app.jobs.card-cycle-close.cron`).
- **Lógica no obvia — cierre de ciclo (`CycleCloseService#closeCycle`):**
  1. Guard atómico de idempotencia (`creditCardRepository.markCutoffClosed` — un `UPDATE`
     condicional que devuelve `0` filas si el ciclo de hoy ya se cerró).
  2. Si pasa el guard: busca cuotas `PENDING` con `dueDate <= hoy` (esta misma consulta resuelve el
     "catch-up" si el job estuvo caído el día exacto del corte — sigue viendo esas cuotas vencidas
     en la próxima corrida).
  3. Si hay cuotas vencidas: crea **un solo** `CardMovement` agregado tipo `INTEREST` con la suma de
     sus intereses, incrementa el saldo de la tarjeta, marca esas cuotas como `BILLED`.
  4. Notifica al dueño de la tarjeta vía `NotificationDispatcher` con `dedupeKey` que incluye
     `cardId` + fecha de cierre.
  - `CardMovement` es un **ledger inmutable** — nunca se expone un endpoint de actualización, mismo
    principio que `DebtCharge`/`DebtPayment`. `amount` siempre se guarda positivo; el efecto sobre
    el saldo lo determina `type`, no el signo.

### 2.12 `analisis` (→ `analysis`)

- **Responsabilidad:** resumen financiero mensual (ingresos/gastos/ahorro/ratios), top categorías,
  serie mensual, recomendaciones, predicción de fin de mes.
- **Entidad:** `FinancialAnalysis` (snapshot persistido).
- **Controller:** `AnalysisController` — `/api/analysis`: `GET /summary`, `GET /recommendations`,
  `GET /prediction`.
- **Services:** `FinancialAnalysisService` (fuente de verdad de las cifras — `ReportService` del
  dominio `reportes` delega en él en vez de recalcular), `MonthEndPredictionService`.
- **Job:** `MonthEndPredictionJob` (dispara notificación con la predicción).
- Usa `MonthlyTotalProjection` (común a `gastos`/`ingresos`) y `CategoryTotalProjection`
  (`gastos`) para agregaciones SQL agrupadas por mes/categoría en una sola consulta por
  repositorio, en vez de una consulta por mes.

---

## 3. Seguridad: flujo actual y flujo nuevo mobile-only

### 3.1 Flujo actual (pensado para web + mobile híbrido)

- **Access token:** JWT HS256 (`jjwt`), claim `token_type=access`, vida `JWT_ACCESS_EXPIRATION_MS`
  (900000 ms / 15 min en el workflow de deploy). Viaja en `Authorization: Bearer`.
  `JwtAuthenticationFilter` lo valida y puebla `SecurityContextHolder` con el `userId` (como
  `Long`) como principal, sin roles/autoridades (`AuthorityUtils.NO_AUTHORITIES`).
- **Refresh token:** JWT HS256 separado, claim `token_type=refresh` + `jti` (UUID) para poder
  rotarlo/revocarlo por fila en `refresh_tokens`, vida `JWT_REFRESH_EXPIRATION_MS` (604800000 ms /
  7 días).
  - **Cliente web (por defecto, sin header `X-Client`):** el refresh token se entrega en una cookie
    `HttpOnly` llamada `financeai_refresh_token` (`JWT_REFRESH_COOKIE_NAME`), `Secure=true` en
    producción, `SameSite=Lax`. Esto **solo funciona** porque el frontend Next.js hace *rewrite* de
    `/api/*` a través de su propio origen (`next.config.mjs`), volviendo la cookie first-party. El
    comentario en `SecurityConfig` es explícito: sin ese proxy, CSRF por cookie no puede funcionar
    en absoluto, porque JavaScript del dominio del frontend nunca puede leer una cookie puesta por
    el dominio del backend.
  - **Cliente con header `X-Client: mobile`:** el refresh token viaja en el body JSON de
    `AuthResponse` (nunca en cookie), y esas requests quedan exentas de CSRF vía
    `RequestHeaderRequestMatcher("X-Client", "mobile")` — la defensa OWASP de "custom header"
    (dispara *preflight* CORS, y un atacante cross-site no puede fabricar un header custom sin que
    el navegador lo bloquee).
- **CSRF:** `CookieCsrfTokenRepository` de doble cookie (`XSRF-TOKEN` legible por JS +
  header `X-XSRF-TOKEN`), obligatorio para mutaciones autenticadas por cookie. Endpoint dedicado
  `GET /api/users/csrf` para que el frontend obtenga el token inicial.
- **CORS:** `app.cors.allowed-origins` (`APP_CORS_ALLOWED_ORIGINS`), pensado para el origin del
  frontend Next.js.
- **Server-to-server (n8n/Telegram):** `TelegramWebhookFilter`, secreto compartido en header
  `X-Telegram-Webhook-Secret`, comparación en tiempo constante (`MessageDigest.isEqual`). No usa
  JWT — no hay sesión de usuario en ese contexto.
- **Rate limiting:** `RateLimitFilter`, ventana fija en memoria, corre *después* de
  `JwtAuthenticationFilter` para poder combinar IP+userId en `/api/ai/chat` y
  `/api/receipts/scan` (dos usuarios detrás del mismo NAT/oficina no comparten balde).

### 3.2 Flujo nuevo propuesto para KoroFin (mobile-only)

**Decisión: el camino "mobile" que ya existe en FinSmart se convierte en el único camino.** No es
un rediseño desde cero — es eliminar la rama condicional y todo lo que solo existía para
sostenerla.

- **Sin cookies.** Login/registro/refresh devuelven *siempre* `{ accessToken, refreshToken, ... }`
  en el body JSON. El cliente Flutter guarda el refresh token en almacenamiento seguro del device
  (`flutter_secure_storage`, respaldado por Keychain en iOS / Keystore en Android) y el access
  token en memoria (o también en secure storage si se prefiere sobrevivir un cierre en frío de la
  app sin volver a loguear).
- **Sin CSRF.** CSRF protege contra un navegador enviando credenciales *ambientales* (cookies) a un
  sitio que no pidió esa request. Un cliente Bearer-only no tiene credencial ambiente que un
  atacante pueda hacer viajar sin su cooperación — no hay superficie que proteger. Se elimina
  `CookieCsrfTokenRepository`, el bean `csrfTokenRepository`, el `.csrf(...)` del filter chain, y el
  endpoint `GET /api/users/csrf`.
- **Sin CORS de aplicación.** Un cliente HTTP nativo (Dio/http de Flutter) no corre en un origen de
  navegador — CORS no lo restringe ni lo protege. Se elimina `CorsConfigurationSource` y
  `APP_CORS_ALLOWED_ORIGINS` del path de producción. Si Swagger UI se sigue exponiendo en `dev` para
  pruebas manuales desde un navegador, no necesita CORS propio porque se sirve desde el mismo origen
  del backend.
- **`UserController` se simplifica:** un solo método `buildAuthResponseEntity` (sin la rama
  `mobile`/no-mobile), sin `resolveRefreshTokenFromCookie`, sin `buildRefreshCookie`/
  `clearRefreshCookie`, sin el parámetro `@RequestHeader("X-Client")`.
- **Se mantiene sin cambios:** `JwtService` (HS256 vía `jjwt`), la rotación de refresh tokens
  (`RefreshTokenService`), `JwtAuthenticationFilter`, `RateLimitFilter`, `TelegramWebhookFilter`
  (Telegram sigue siendo servidor-a-servidor, ajeno a este cambio).
- **A verificar/reforzar durante la implementación** (no confirmado en el código que ya existe, pero
  vale la pena que el equipo de implementación lo revise dado que es una app financiera): detección
  de reuso de refresh token (si un token rotado se vuelve a presentar, invalidar toda la familia de
  tokens de esa sesión) y, opcionalmente, listar/revocar sesiones activas por dispositivo desde
  `PUT /profile` o una pantalla de "dispositivos conectados". Esto es una mejora de seguridad
  razonable para una app de finanzas personales, no un requisito estricto para el MVP de KoroFin.

---

## 4. Integraciones de IA

**Corrección sobre el brief:** la lista de proveedores no son 3 (NVIDIA/OpenCode/OpenRouter) sino
**5**: NVIDIA, **Gemini**, OpenCode, OpenRouter y **Groq** (Groq está en el catálogo pero "listo pero
inerte" — sin `GROQ_API_KEY` configurada todavía). Verificado leyendo
`SupportedAiProvider.java` y `application.properties` directamente.

### 4.1 Patrón: catálogo fijo + registro + orquestador con fallback en cascada

- **`SupportedAiProvider` (enum, el catálogo):** cada entrada fija `baseUrl`, `defaultModel` y
  `defaultVisionModel` (o `null` si el proveedor no tiene modelo de visión conocido — hoy solo
  NVIDIA, Gemini y OpenRouter lo tienen). El operador solo necesita configurar una API key (y
  opcionalmente un modelo distinto) por variable de entorno para activar un proveedor.
- **`AiProviderProperties` (`@ConfigurationProperties`):** mapea `app.ai.providers.<key>.*` por
  proveedor (api-key, model, vision-model).
- **`AiProviderRegistry`:** resuelve la lista de proveedores *habilitados* (con API key no vacía),
  ordenados por prioridad. La prioridad puede fijarse:
  - **Global:** `AI_PROVIDER_PRIORITY` (lista separada por comas).
  - **Por tarea:** `AI_TASK_PRIORITY_CHAT` / `_CATEGORIZE` / `_INSIGHT` / `_STATEMENT_EXTRACT` —
    sobreescribe la global solo para esa operación (p. ej. forzar que los insights financieros
    siempre prueben Gemini primero).
  - Si nada está configurado, cae al orden por defecto:
    `GEMINI → NVIDIA → OPENCODE → OPENROUTER → GROQ`.
- **`AiChatClient`:** hace la llamada HTTP cruda al contrato OpenAI-compatible
  `POST /chat/completions` de cada proveedor, vía `RestClient` de Spring (un cliente nuevo por
  llamada, con el `baseUrl` del proveedor resuelto). Mapea errores HTTP/conexión a una jerarquía
  tipada de excepciones (`AiProviderAuthException`, `AiProviderRateLimitException`,
  `AiProviderModelNotFoundException`, `AiProviderTimeoutException`, `AiProviderUnavailableException`)
  — todas extienden `AiProviderException`, que **nunca** expone el detalle real del proveedor al
  cliente HTTP (mensaje genérico fijo), solo lo loguea internamente.
- **`AiChatOrchestrator`:** itera los proveedores habilitados en orden y hace *failover* automático
  — si un proveedor lanza `AiProviderException`, prueba el siguiente. Si ninguno responde, lanza
  `AiProvidersExhaustedException` (mapeada a `503` con el mensaje genérico
  `AiChatOrchestrator.GENERIC_MESSAGE`). Registra telemetría de uso/costo vía `AiUsageEventService`
  + `AiProviderPricing`.
- **Visión (fotos de recibos):** `completeVision` itera los proveedores habilitados que sí tienen
  `defaultVisionModel` no nulo (o un `*_VISION_MODEL` explícito).

### 4.2 Cuota mensual por usuario

`AI_MONTHLY_MESSAGE_LIMIT` (default `5`) limita mensajes de rol `USER` por mes calendario UTC,
contra un contador propio en `User` (`aiChatUsed`/`aiChatPeriod`), reservado atómicamente antes de
llamar al proveedor — protege la cuota gratuita del proveedor, no es configuración por usuario.
Se mapea a `AiMessageQuotaExceededException` → `429`.

### 4.3 Decisión para KoroFin

**Preservar el patrón tal cual.** Es limpio, ya implementa Strategy + fallback en cascada + cuota +
telemetría de costo, y está bien testeado (`AiProviderRegistryTest`, `AiChatClientTest`,
`AiChatOrchestrestratorTest`, `AiProviderPricingTest`). No hay razón de diseño para rehacerlo — solo
migrarlo tal cual a la nueva ubicación de paquetes (`service/ai/*`, con el catálogo en
`service/ai/provider/` o similar).

---

## 5. Integración de Telegram

- **Flujo de vínculo (dos pasos, dos actores distintos):**
  1. La app (con JWT) llama `POST /api/integrations/telegram/link-code` → genera un código de un
     solo uso, TTL corto.
  2. El usuario le manda ese código al bot de Telegram; n8n reenvía la confirmación a
     `POST /api/integrations/telegram/confirm-link` (protegido por `TelegramWebhookFilter`, sin
     JWT — n8n no tiene sesión de usuario) para completar el vínculo `chatId ↔ userId`
     (`TelegramLink`, `telegram_chat_id` único — si el chat ya estaba vinculado a otro usuario, se
     re-asigna).
- **Registro de movimientos por chat:** n8n reenvía el texto/foto del mensaje a
  `POST /api/integrations/telegram/expenses` o `/receipts` (protegidos por el mismo webhook
  filter). `TelegramExpenseService.registerFromMessage`:
  1. Rate limit propio por `chatId` (`InMemoryRateLimiter` interno, no el `RateLimitFilter` global).
  2. `TelegramIntentDetector.looksLikeSummaryQuery` — heurística de palabras clave sin IA (evita
     pagar una llamada de IA extra en cada mensaje) que decide si es una *pregunta* ("¿cuánto gasté
     en comida?") o un intento de *registro* ("Uber 15000").
  3. Si es registro: `TelegramMessageParser.parse` extrae monto/descripción con regex y valida
     plausibilidad (rechaza montos absurdamente bajos/altos con `TelegramImplausibleMovementException`,
     mapeada a `422` con mensaje amigable que n8n reenvía tal cual al chat).
  4. Clasifica el movimiento con IA (`AiCategorizationService`) — si falla, degrada a `EXPENSE` sin
     categoría en vez de romper el flujo completo.
- **Autenticación server-to-server:** ver `TelegramWebhookFilter` en la sección 3.1. Vacío
  (`TELEGRAM_WEBHOOK_SECRET` no configurado) = las tres rutas devuelven 401 siempre, integración
  apagada por defecto.
- **Decisión para KoroFin:** se conserva como integración *opcional* y desactivable por defecto. No
  entra en conflicto con "solo app móvil, no frontend web" — es un canal servidor-a-servidor
  independiente del frontend.

---

## 6. Escaneo de recibos y rate limiting

- **`ReceiptScanController`** (`POST /api/receipts/scan`, protegido por JWT estándar): recibe la
  imagen como *data URI* en el body JSON (`ReceiptScanRequest.imageDataUri`), no multipart. Llama a
  `ReceiptExtractionService.extractFromImage`, que usa `AiChatOrchestrator` con un mensaje que
  incluye la imagen (`ChatMessage.imageUrl`), y resuelve la categoría contra las categorías del
  usuario. El endpoint **no crea** el gasto/ingreso — la app confirma llamando después a
  `POST /api/expenses` o `POST /api/incomes` con los datos ya extraídos.
- **Rate limiting (`RateLimitFilter` + `InMemoryRateLimiter`):** ventana fija en memoria, por
  proceso (no distribuido — decisión de arquitectura documentada en
  `docs/sprints/sprint1.md`, decisión 4: "en memoria, bucket por IP+usuario, no distribuido"). Cuatro
  reglas hoy: `login` (IP), `register` (IP), `ai-chat` (IP+userId), `receipt-scan` (IP+userId).
  Clave de env: `RATE_LIMIT_<REGLA>_MAX_REQUESTS` / `_WINDOW_SECONDS`, todas con default razonable
  si no se configuran.
- **Hallazgo verificado sobre el historial reciente (commit `1ca2b63`):** `RATE_LIMIT_RECEIPT_SCAN_*`
  ya tenía el `@Value` con default hardcodeado en `RateLimitFilter` desde que se agregó el rate
  limit de `/api/receipts/scan` (M1, PR #98), pero **nunca se agregó la línea correspondiente en
  `application.properties`** — a diferencia de login/register/ai-chat, no era configurable por
  entorno hasta ese fix. Ya está corregido en `application.properties`, **pero el workflow
  `deploy-backend.yml` nunca fue actualizado para pasar `RATE_LIMIT_RECEIPT_SCAN_MAX_REQUESTS`/
  `_WINDOW_SECONDS` a Cloud Run** — hoy en producción esos dos valores siempre corren con su
  default (`10`/`60`), nunca los configurados. **Esto no debe repetirse en KoroFin**: el workflow de
  deploy nuevo debe pasar *todas* las variables que `application.properties` declara como
  configurables, no un subconjunto desactualizado (ver sección 14 y el hallazgo ampliado en la
  sección 11.3).

---

## 7. Reportes

- **`ReportController`** (`/api/reports`): `GET /monthly` (cifras del mes), `GET /movements`
  (lista de movimientos del período, para tabla en pantalla), `GET /export` (`?format=csv|json`,
  streaming directo a la respuesta con `Content-Disposition: attachment`).
- **`ReportService`:** delega el resumen mensual íntegramente en
  `FinancialAnalysisService#getSummary` (dominio `analisis`) — **no recalcula nada**, solo
  reordena la forma de la respuesta. `getMovements` combina `Expense`+`Income` del período,
  ordenados por fecha descendente.
- **CSV manual (sin librería de terceros):** `ReportController#writeCsv` escribe el CSV a mano,
  con dos protecciones de seguridad que vale la pena conservar tal cual en KoroFin:
  - Escapado RFC4180 estándar (comillas dobladas cuando el valor tiene coma/comilla/salto de línea).
  - **Neutralización de CSV injection (CWE-1236):** si un campo empieza con `=`, `+`, `-` o `@`, se
    le antepone una comilla simple — de lo contrario Excel/Sheets podría interpretar el campo como
    una fórmula ejecutable al abrir el CSV exportado.
- No genera PDF/Excel binario pese a que `pdfbox`/`poi-ooxml` están en el `pom.xml` — esas
  librerías las usa exclusivamente el dominio `extractos` para *leer* extractos bancarios subidos
  por el usuario, no para *generar* reportes. El nombre de archivo exportado usa el prefijo
  `korofin-` (`buildContentDisposition`), no `finsmart-` — ya renombrado en el código actual.

---

## 8. Testing actual

FinSmart tiene una base de tests amplia (más de 60 archivos de test bajo
`src/test/java/.../backend/`), con un patrón consistente por capa:

- **Servicios:** `*ServiceTest` con Mockito puro (mocks de repositorios/colaboradores),
  sin contexto Spring. Ejemplos: `UserServiceTest`, `ExpenseServiceTest`, `DebtServiceTest`,
  `DebtChargeServiceTest`, `CardMovementServiceTest`, `AiChatServiceTest`, etc.
- **Controllers:** `*ControllerTest` con `@WebMvcTest` + `MockMvc`, mockeando la capa de servicio.
  Ejemplos: `UserControllerTest`, `ExpenseControllerTest`, `DebtControllerTest`,
  `ReceiptScanControllerTest`.
- **Filtros de seguridad:** tests unitarios standalone, sin `@WebMvcTest` completo —
  `RateLimitFilterTest`, `TelegramWebhookFilterTest`, `InMemoryRateLimiterTest` — posible porque
  esos filtros se construyen solo con primitivos `@Value` inyectados (decisión deliberada
  documentada en el Javadoc de `RateLimitFilter`, para no forzar a cada `@WebMvcTest` existente a
  proveer beans adicionales).
- **Jobs programados:** `*JobTest` con `Clock` fijo inyectado (vía `ClockConfig`) para hacer
  determinista la lógica de fecha — `CardCycleCloseJobTest`, `PaymentReminderJobTest`,
  `WeeklySummaryJobTest`, `MonthEndPredictionJobTest`, `InactivityReminderJobTest`.
- **Cliente HTTP de IA:** `AiChatClientTest` testea el mapeo de errores
  (`mapResponseException`/`mapAccessException`) llamando esos métodos *directamente* (son
  package-private a propósito, según su propio Javadoc) en vez de montar un `RestClient` real
  contra un servidor de prueba — evita depender de infraestructura HTTP para cubrir casos de borde.
- **No hay Testcontainers** en el `pom.xml` (`spring-boot-starter-data-jpa-test` está, pero no
  `org.testcontainers:postgresql`) — los tests de repositorio, si existen, corren contra lo que
  Spring Boot resuelva por defecto en el classpath de test, no contra un Postgres real. Esto es una
  brecha real de calidad que KoroFin debe cerrar (ver sección 15).
- **Cobertura:** no hay plugin JaCoCo configurado en `pom.xml` — no hay forma rápida de ver el
  porcentaje actual sin ejecutar una herramienta externa. KoroFin debe agregar JaCoCo desde el
  primer commit para tener visibilidad continua.

---

## 9. Migraciones Flyway

27 migraciones en `smart-finance-backend/src/main/resources/db/migration/`, `V1` a `V27`,
convención estándar de Flyway: `V<número>__<descripción_en_snake_case>.sql` (doble guion bajo tras
el número). Progresión cronológica que refleja la evolución del dominio:

`V1` usuarios → `V2` refresh tokens → `V3` categorías/ingresos/gastos → `V4` deudas/pagos/servicios
recurrentes → `V5` FK de servicio recurrente en gastos → `V6` análisis financiero → `V7`
notificaciones/preferencias → `V8` mensajes de IA → `V10`–`V12` índices de performance + FK de pago
de deuda en gastos (nota: no hay `V9` visible en el listado — a confirmar si es un salto
intencional o un archivo que se movió/renombró alguna vez) → `V13` remember-me en refresh tokens →
`V14` cuota de IA en usuarios → `V15` cargos de deuda → `V16` eventos de uso de IA → `V17`/`V18`
tarjetas de crédito y sus movimientos → `V19`/`V20` planes de cuotas y cuotas → `V21` FK de
movimiento de tarjeta en gastos → `V22` tipo de notificación de cierre de ciclo → `V23` tipo de
evento de extracción de extracto → `V24` vínculos de Telegram → `V25` telemetría de eventos de uso
de IA → `V26` tokens push → `V27` preferencias de usuario.

`spring.flyway.baseline-on-migrate=true`, `locations=classpath:db/migration`,
`spring.jpa.hibernate.ddl-auto=validate` (Hibernate nunca genera DDL — Flyway es la única fuente de
verdad del esquema, Hibernate solo valida que las entidades coincidan). Además hay
`db/dev-seed/seed_jhon_quiceno.sql`, fuera de `db/migration/` (no versionado por Flyway, un seed
manual para desarrollo local).

**Para KoroFin:** mantener la misma convención (`V<n>__snake_case.sql`, un archivo por cambio de
esquema, nunca editar una migración ya aplicada) y arrancar limpio desde `V1` con el esquema nuevo
ya reorganizado, en vez de portar las 27 migraciones tal cual — el paquete nuevo por capa no cambia
el esquema de base de datos en sí (los nombres de tabla no tienen por qué cambiar), pero es la
oportunidad de consolidar cualquier ajuste pendiente en un `V1` limpio dado que KoroFin arranca sin
datos de producción que migrar.

---

## 10. Variables de entorno completas (FinSmart, tal como existen hoy)

Confirmado leyendo `application.properties`, `application-dev.properties`,
`application-prod.properties` y `.github/workflows/deploy-backend.yml` línea por línea — el set es
más grande que el que trae el brief original (que ya traía la mayoría correctamente).

| Variable | Dónde se usa | Default | Notas |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | datasource | `jdbc:postgresql://localhost:${DB_PORT_EXTERNAL}/${DB_NAME}` | El default con `DB_PORT_EXTERNAL`/`DB_NAME` es solo para docker-compose local. |
| `SPRING_DATASOURCE_USERNAME` | datasource | `${DB_USER}` | ídem, local-only como fallback |
| `SPRING_DATASOURCE_PASSWORD` | datasource | `${DB_PASSWORD}` | ídem, local-only como fallback |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | JPA | `validate` | no tocar en prod |
| `JWT_SECRET` | firma HS256 | — (requerido) | secreto |
| `JWT_ISSUER` | claim `iss` | — (requerido) | |
| `JWT_ACCESS_EXPIRATION_MS` | vida access token | — (requerido) | `900000` en deploy actual (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | vida refresh token | — (requerido) | `604800000` en deploy actual (7 días) |
| `JWT_REFRESH_COOKIE_NAME` | nombre de cookie | — (requerido) | **se elimina en KoroFin** (mobile-only, sin cookie) |
| `JWT_REFRESH_COOKIE_SECURE` | flag `Secure` de la cookie | — (requerido) | **se elimina en KoroFin** |
| `JWT_REFRESH_COOKIE_SAME_SITE` | `SameSite` de la cookie | — (requerido) | **se elimina en KoroFin** |
| `APP_CORS_ALLOWED_ORIGINS` | CORS | `http://localhost:3000` | **se elimina/opcional en KoroFin** (sin frontend web) |
| `RESEND_API_KEY` | SMTP notificaciones email | vacío (degrada) | password SMTP de Resend |
| `MAIL_FROM` | remitente de email | vacío (degrada) | |
| `AI_PROVIDER_PRIORITY` | orden global de proveedores IA | vacío → orden por catálogo | opcional |
| `NVIDIA_API_KEY` / `NVIDIA_MODEL` / `NVIDIA_VISION_MODEL` | proveedor NVIDIA | vacío / catálogo / catálogo | |
| `GEMINI_API_KEY` / `GEMINI_MODEL` / `GEMINI_VISION_MODEL` | proveedor Gemini | vacío / catálogo / catálogo | **faltaba en el brief original** |
| `OPENCODE_API_KEY` / `OPENCODE_MODEL` | proveedor OpenCode | vacío / catálogo | |
| `OPENROUTER_API_KEY` / `OPENROUTER_MODEL` / `OPENROUTER_VISION_MODEL` | proveedor OpenRouter | vacío / catálogo / catálogo | |
| `GROQ_API_KEY` / `GROQ_MODEL` | proveedor Groq | vacío / catálogo | **faltaba en el brief original**; catálogo "listo pero inerte" |
| `AI_TASK_PRIORITY_CHAT` / `_CATEGORIZE` / `_INSIGHT` / `_STATEMENT_EXTRACT` | prioridad de proveedor por tarea | vacío → usa la global | opcional, todas |
| `AI_MONTHLY_MESSAGE_LIMIT` | cuota mensual por usuario | `5` | |
| `AI_READ_TIMEOUT_SECONDS` | timeout HTTP a proveedores IA | `60` | no bajar sin revisar `StatementAiExtractionService` primero |
| `RATE_LIMIT_LOGIN_MAX_REQUESTS` / `_WINDOW_SECONDS` | rate limit login | `5` / `60` | |
| `RATE_LIMIT_REGISTER_MAX_REQUESTS` / `_WINDOW_SECONDS` | rate limit registro | `3` / `300` | |
| `RATE_LIMIT_AI_CHAT_MAX_REQUESTS` / `_WINDOW_SECONDS` | rate limit chat IA | `10` / `60` | |
| `RATE_LIMIT_RECEIPT_SCAN_MAX_REQUESTS` / `_WINDOW_SECONDS` | rate limit escaneo de recibos | `10` / `60` | **ausente del workflow de deploy actual — corregir en KoroFin, ver sección 6** |
| `APP_JOBS_ENABLED` | interruptor maestro de `@EnableScheduling` | `true` | poner `false` en tests que no necesiten jobs |
| `APP_JOBS_PAYMENT_REMINDER_CRON` | cron | `0 0 8 * * *` | |
| `APP_JOBS_WEEKLY_SUMMARY_CRON` | cron | `0 0 7 * * MON` | |
| `APP_JOBS_INACTIVITY_REMINDER_CRON` | cron | `0 0 9 * * *` | |
| `APP_JOBS_MONTH_END_PREDICTION_CRON` | cron | `0 30 8 * * *` | |
| `APP_JOBS_CARD_CYCLE_CLOSE_CRON` | cron | `0 0 6 * * *` | |
| `TELEGRAM_WEBHOOK_SECRET` | autenticación server-to-server de Telegram | vacío (integración apagada) | opcional |
| `PORT` | puerto HTTP | `8080` | Cloud Run lo inyecta solo, no configurar a mano |
| `DB_PORT_EXTERNAL` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | solo dentro del default local de `SPRING_DATASOURCE_URL` | — | **local/docker-compose únicamente**, irrelevantes en Cloud Run porque ahí `SPRING_DATASOURCE_*` siempre se fija directo |

---

## 11. Deploy actual (Docker + CI/CD)

### 11.1 `Dockerfile`

Build multi-stage: `eclipse-temurin:21-jdk-alpine` (compila con `./mvnw package -DskipTests`) →
runtime `eclipse-temurin:21-jre-alpine`, usuario no-root (`spring`), `HEALTHCHECK` contra
`/actuator/health` (Cloud Run no lo usa — hace su propio *probing* TCP/HTTP — pero sirve para
`docker ps`/compose local).

### 11.2 `.github/workflows/ci.yml`

Un job `changes` (detecta si el diff toca `smart-finance-mobile/**` con `dorny/paths-filter`),
`backend-tests` (Java 21 + `./mvnw test`), `frontend-tests` (pnpm + test + build — **este job
desaparece en KoroFin**, no hay frontend web), `mobile-checks` (condicional a que el diff toque
`smart-finance-mobile/**` — tipos de Expo Router, `tsc --noEmit`, lint, test). Corre en PRs a
`main` y `develop`.

### 11.3 `.github/workflows/deploy-backend.yml`

Trigger: push/PR a `main` con cambios en `smart-finance-backend/**`. Auth vía Workload Identity
Federation (`google-github-actions/auth@v3`, sin llave JSON de service account estática) —
secrets `GCP_PROJECT_ID`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`. Escribe un
archivo `cloud-run-env-vars.yaml` temporal y despliega con `gcloud run deploy --source` (build
remoto en Cloud Build, sin build local en el runner). En PR: `--no-traffic --tag pr-<n>` (valida
que build+auth+deploy funcionan sin exponer tráfico real). En push a `main`: deploy normal +
`gcloud run services update-traffic --to-latest` (necesario porque, una vez que cualquier deploy
usó `--no-traffic`, Cloud Run deja de seguir `LATEST` automáticamente).

**Hallazgo verificado — el archivo de env vars del deploy actual está desactualizado respecto a
`application.properties`.** Comparando línea por línea, `deploy-backend.yml` **nunca pasa**:
`GEMINI_API_KEY`/`GEMINI_MODEL`/`GEMINI_VISION_MODEL`, `GROQ_API_KEY`/`GROQ_MODEL`,
`NVIDIA_VISION_MODEL`, `OPENROUTER_VISION_MODEL`, ninguna `AI_TASK_PRIORITY_*`,
`AI_READ_TIMEOUT_SECONDS`, ninguna `APP_JOBS_*`, `TELEGRAM_WEBHOOK_SECRET`, ni
`RATE_LIMIT_RECEIPT_SCAN_MAX_REQUESTS`/`_WINDOW_SECONDS` (ver sección 6). En producción hoy, todas
esas variables corren con su valor por defecto de `application.properties` porque Cloud Run nunca
las recibe explícitas — no es necesariamente un bug funcional (los defaults son razonables), pero
sí significa que hoy es **imposible** ajustar esos valores en producción sin tocar código. **El
workflow nuevo de KoroFin debe declarar el set completo** (sección 10), aunque algunas queden con
valor vacío/no seteado a propósito (p. ej. `TELEGRAM_WEBHOOK_SECRET` si Telegram no se activa
todavía, `GROQ_API_KEY` mientras el catálogo siga inerte).

### 11.4 `.github/workflows/trivy.yml`

Build del jar (`mvnw clean package -DskipTests`) + build de imagen Docker + escaneo Trivy
(`aquasecurity/trivy-action`, severidad `CRITICAL,HIGH`, `ignore-unfixed: true`) + sube SARIF a
GitHub Security. Corre en PR a `main`/`develop`, push a `main`, y cron semanal (lunes 03:00 UTC) —
el cron atrapa CVEs nuevas descubiertas en dependencias ya mergeadas, sin esperar al próximo PR.

---

## 12. Nueva estructura de paquetes de KoroFin

### Decisión

Capa técnica primero, dominio de negocio anidado adentro de cada capa — tal como lo pidió el
dueño del proyecto:

```
com.korofin.backend/
  controller/
    user/           UserController
    expense/        ExpenseController, CategoryController
    income/         IncomeController
    debt/           DebtController, DebtPaymentController, DebtChargeController
    card/           CreditCardController, CardMovementController
    statement/      StatementImportController
    ai/             AiChatController, AiCategorizationController, AiInsightController,
                     AiProviderStatusController, AiUsageEventController, ReceiptScanController
    integration/    TelegramIntegrationController
    report/         ReportController
    notification/   NotificationController
    recurringpayment/  RecurringPaymentController
    analysis/       AnalysisController
  service/
    user/           UserService, RefreshTokenService, AuthSession
    expense/        ExpenseService, CategoryService
    income/         IncomeService
    debt/           DebtService, DebtPaymentService, DebtChargeService
    card/           CreditCardService, CardMovementService, AmortizationService, CycleCloseService
    statement/      StatementImportService
      ai/             StatementAiExtractionService
      extraction/     StatementTextExtractor, PdfStatementTextExtractor, XlsxStatementTextExtractor,
                       CsvStatementTextExtractor, StatementTextExtractionService
      dedup/          DescriptionSimilarity, DuplicateDetector
    ai/             AiChatService, AiCategorizationService, AiInsightService, AiUsageEventService,
                     FinancialSummaryQueryService, ReceiptExtractionService
      provider/       AiChatClient, AiChatOrchestrator, AiProviderRegistry, AiProviderProperties,
                       SupportedAiProvider, ResolvedAiProvider, ChatMessage, ChatCompletionResult,
                       AiCallContext, AiProviderPricing, FinancialContextBuilder
    integration/
      telegram/       TelegramLinkService, TelegramLinkCodeStore, TelegramExpenseService,
                       TelegramMessageParser, TelegramIntentDetector
    report/         ReportService
    notification/   NotificationService
      channel/        NotificationDispatcher, NotificationSender, EmailNotificationSender,
                       PushNotificationSender, ExpoPushAdapter
    recurringpayment/  RecurringPaymentService
    analysis/       FinancialAnalysisService, MonthEndPredictionService
    scheduling/     PaymentReminderJob, WeeklySummaryJob, InactivityReminderJob,
                     MonthEndPredictionJob, CardCycleCloseJob, OverspendAlertListener,
                     NotificationMessageFormatter
  repository/
    user/ expense/ income/ debt/ card/ statement/(n/a, sin entidad propia) ai/ integration/
    notification/ recurringpayment/ analysis/
    common/         MonthlyTotalProjection, UserLastActivityProjection (proyecciones compartidas
                    entre más de un dominio se quedan en un paquete común, no duplicadas)
  dto/              (o `model/dto` si se prefiere mantener el subnivel `model` — ver nota abajo)
    user/ expense/ income/ debt/ card/ statement/ ai/ integration/ report/ notification/
    recurringpayment/ analysis/
  entity/           (o `model/entity`)
    user/ expense/ income/ debt/ card/ ai/ integration/ notification/ recurringpayment/ analysis/
  mapper/
    user/ expense/ income/ debt/ card/ notification/ recurringpayment/
  security/         JwtAuthenticationFilter, JwtService, SecurityUtils, RateLimitFilter,
                    InMemoryRateLimiter, TelegramWebhookFilter
  config/           SecurityConfig, JwtProperties, ClockConfig, SchedulingConfig, OpenApiConfig,
                    RestClientConfig, AsyncConfig
  exception/        GlobalExceptionHandler, ErrorResponse, ResourceNotFoundException,
                    (excepciones específicas de dominio quedan junto a su dominio si son propias
                    de un solo controller/service, o acá si son realmente transversales)
```

**Nota sobre `dto`/`entity` vs. `model/dto`+`model/entity`:** FinSmart usa `model/dto` y
`model/entity` como subcarpetas dentro de cada dominio. Para KoroFin, con capa-primero, se recomienda
aplanar a `dto/` y `entity/` como capas de primer nivel (sin el nivel intermedio `model/`) — es
coherente con que `controller/`, `service/`, `repository/` también son de primer nivel, y evita un
nivel de anidación que ya no aporta nada una vez que la capa es el criterio de organización externo.

### Convención de nombres de clases (obligatoria, para todos los dominios)

| Elemento | Convención | Ejemplo |
|---|---|---|
| Controller | `{Domain}Controller` | `UserController`, `ExpenseController` |
| Service | `{Domain}Service` (clase concreta, sin interfaz salvo que exista más de una implementación real) | `UserService`, `ExpenseService` |
| Service con múltiples implementaciones (Strategy) | `{Concepto}` interfaz + `{ProviderConcreto}{Concepto}` implementación | `NotificationSender` → `EmailNotificationSender`; `PushNotificationSender` → `ExpoPushAdapter` |
| Repository | `{Domain}Repository extends JpaRepository<{Entity}, Long>` | `UserRepository`, `ExpenseRepository` |
| Entity | `{Domain}` (sustantivo singular) | `User`, `Expense`, `Debt` |
| DTO de creación | `{Domain}Request` | `ExpenseRequest`, `RegisterRequest` |
| DTO de actualización (cuando difiere semánticamente de creación) | `{Domain}UpdateRequest` | `DebtUpdateRequest` |
| DTO de lectura | `{Domain}Response` | `ExpenseResponse`, `DebtResponse` |
| Mapper | `{Domain}Mapper` (interfaz MapStruct, `@Mapper(componentModel = "spring")`) | `ExpenseMapper`, `DebtMapper` |
| Excepción de dominio | `{RazónEspecífica}Exception extends RuntimeException`, mapeada en `GlobalExceptionHandler` | `EmailAlreadyExistsException`, `InvalidCredentialsException` |

Este es exactamente el patrón que FinSmart ya usa hoy dentro de cada dominio (DTOs de
request/response explícitos en vez de un `Dto` genérico, mappers MapStruct dedicados, excepciones
específicas por caso) — se conserva sin cambios, solo se reubica por capa.

### Nota: tensión con las prácticas recomendadas de Spring Boot

La skill `java-springboot` cargada para este análisis recomienda explícitamente **organizar por
feature/dominio, no por capa** ("Package Structure: Organize code by feature/domain... rather than
by layer"), y es la guía predominante en la comunidad Spring moderna (mejor encapsulamiento por
dominio, menos acoplamiento cruzado entre paquetes, más fácil de extraer un dominio a un servicio
separado si hiciera falta más adelante). La skill `java-coding-standards`, en cambio, muestra un
ejemplo de estructura por capa (`config/ controller/ service/ repository/ domain/ dto/ util/`) sin
anidar dominio adentro — más parecido a lo pedido, aunque sin el nivel de dominio explícito.

Es una decisión real con trade-offs, no una elección neutra:

- **A favor de capa-primero (lo pedido):** consistente con cómo ya estaba organizado FinSmart antes
  del refactor a dominio-primero (según el propio historial del proyecto), más fácil de navegar
  para alguien que piensa "quiero ver todos los controllers" de un vistazo, y con dominio anidado
  adentro de cada capa se recupera buena parte de la cohesión por dominio que se perdería con
  capa-primero "plano" (sin el nivel de dominio).
- **En contra (costo real a mediano plazo):** a medida que la app crezca, cambiar una funcionalidad
  de un dominio obliga a tocar archivos en 5-6 carpetas de primer nivel distintas (`controller/x`,
  `service/x`, `repository/x`, `dto/x`, `entity/x`, `mapper/x`) en vez de una sola; es más fácil que
  aparezca un import cruzado entre dominios "por comodidad" cuando todos los `service/*` conviven en
  el mismo nivel; y es la organización que Spring Boot y la mayoría de guías actuales desaconsejan
  para proyectos que esperan crecer.

**Se sigue la instrucción explícita del dueño del proyecto** (capa-primero, dominio anidado
adentro), documentada acá para que quede registro consciente de la decisión y su costo, no como una
elección por defecto sin evaluar. Mitigación aplicada: los subpaquetes de dominio se mantienen
**estructuralmente espejados** en cada capa (si existe `service/card/`, existe también
`repository/card/`, `dto/card/`, `entity/card/` con los mismos nombres) para que la ubicación de
cualquier clase sea predecible sin tener que buscarla.

---

## 13. Qué se elimina / simplifica en el refactor

| Elemento actual | Acción | Por qué |
|---|---|---|
| `CookieCsrfTokenRepository`, bean `csrfTokenRepository`, `.csrf(...)` en `SecurityConfig`, endpoint `GET /api/users/csrf` | **Eliminar** | CSRF protege auth por cookie; mobile-only usa solo Bearer. |
| `ResponseCookie` de refresh token, `buildRefreshCookie`/`clearRefreshCookie`, `resolveRefreshTokenFromCookie` en `UserController` | **Eliminar** | El refresh token siempre viaja en el body JSON; no hay cookie que gestionar. |
| Rama condicional `X-Client: mobile` / `isMobileClient` / `buildAuthResponseEntity` con dos caminos | **Colapsar a un solo camino** | Ya no hay cliente web que necesite el camino alternativo. |
| `CorsConfigurationSource`, bean `corsConfigurationSource`, `APP_CORS_ALLOWED_ORIGINS` en el path de producción | **Eliminar o dejar opcional/no usado** | Un cliente HTTP nativo no está sujeto a CORS de navegador. |
| `JWT_REFRESH_COOKIE_NAME`/`_SECURE`/`_SAME_SITE` (env vars) | **Eliminar** | Consecuencia directa de eliminar la cookie. |
| Comentario/asunción de "frontend Next.js hace rewrite de `/api/*`" en `SecurityConfig` | **Eliminar** | Ya no existe ese frontend. |
| `smart-finance-frontend/` completo (proyecto Next.js) | **No se porta** | Decisión explícita: solo app móvil. |
| Todo lo demás (dominios de negocio, patrón de proveedores de IA, Telegram, rate limiting, reportes, jobs programados) | **Se conserva**, solo se reubica en la nueva estructura de paquetes | No hay razón de negocio ni técnica para tocarlo — es lógica que sirve igual a un cliente móvil. |

No se encontró lógica muerta o duplicada evidente durante este mapeo más allá de lo listado arriba
— el código que se leyó está consistentemente bien documentado con Javadoc explicando el "por qué",
lo cual redujo bastante el riesgo de pasar por alto una decisión no obvia.

---

## 14. Plan Docker / CI-CD para KoroFin

### 14.1 `Dockerfile`

Prácticamente idéntico al actual, cambiando solo lo que referencia al nombre del proyecto:

- Build stage: `eclipse-temurin:21-jdk-alpine`, mismo flujo (`dependency:go-offline` cacheado antes
  de copiar `src/`, luego `package -DskipTests`).
- Runtime stage: `eclipse-temurin:21-jre-alpine`, usuario no-root `spring`, `HEALTHCHECK` contra
  `/actuator/health`.
- Sin cambios estructurales — el Dockerfile actual ya es una buena práctica (multi-stage, no-root,
  Alpine, healthcheck). Solo renombrar donde haga falta (p. ej. si el `WORKDIR`/artefacto incluye el
  nombre del módulo).

### 14.2 `ci.yml`

Mismo patrón de `changes` (paths-filter) + `backend-tests` (Java 21, `./mvnw test`) +
`mobile-checks` (condicional a cambios en `korofin_mobile/**`: tipos, `flutter analyze`/lint, test).
**Sin el job `frontend-tests`** (no hay frontend web). Trigger en PR a `main` y `develop`, igual que
hoy.

### 14.3 `trivy.yml`

Mismo patrón: build del jar + build de imagen Docker + escaneo Trivy (`CRITICAL,HIGH`,
`ignore-unfixed: true`) + subida de SARIF a GitHub Security. Mismo trigger (PR a `main`/`develop`,
push a `main`, cron semanal).

### 14.4 `deploy-backend.yml`

Mismo patrón de Workload Identity Federation + Cloud Run `--source` deploy, servicio renombrado a
**`korofin-backend`**, misma región `us-central1` (razonable mantener salvo que el usuario prefiera
otra). Mismo comportamiento PR (`--no-traffic --tag pr-<n>`) vs. push a `main`
(deploy + `update-traffic --to-latest`).

**Corrección explícita respecto al workflow actual (ver hallazgo de la sección 11.3):** el archivo
de env vars de Cloud Run debe declarar el **set completo** de variables de la sección 10 (menos las
tres de cookie, eliminadas), no el subconjunto desactualizado que tiene FinSmart hoy.

### 14.5 Lista final de GitHub Secrets a configurar en el repo KoroFin

**Infraestructura / despliegue:**

- `GCP_PROJECT_ID` — proyecto de Google Cloud.
- `GCP_WORKLOAD_IDENTITY_PROVIDER` — proveedor de identidad federada para auth sin llave estática.
- `GCP_SERVICE_ACCOUNT` — cuenta de servicio que Cloud Run/Cloud Build usa para desplegar.

**Base de datos:**

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

**JWT:**

- `JWT_SECRET` (secreto de firma), `JWT_ISSUER`, `JWT_ACCESS_EXPIRATION_MS`,
  `JWT_REFRESH_EXPIRATION_MS`.
- *(no se configuran `JWT_REFRESH_COOKIE_*` — eliminados, ver sección 13)*.

**Email (opcional, degrada si falta):**

- `RESEND_API_KEY`, `MAIL_FROM`.

**Proveedores de IA (todos opcionales — un proveedor solo se activa si su API key no está vacía):**

- `AI_PROVIDER_PRIORITY` (orden global, opcional).
- `NVIDIA_API_KEY`, `NVIDIA_MODEL`, `NVIDIA_VISION_MODEL`.
- `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_VISION_MODEL`.
- `OPENCODE_API_KEY`, `OPENCODE_MODEL`.
- `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`, `OPENROUTER_VISION_MODEL`.
- `GROQ_API_KEY`, `GROQ_MODEL` (opcional, catálogo inerte hasta que se configure).
- `AI_TASK_PRIORITY_CHAT`, `AI_TASK_PRIORITY_CATEGORIZE`, `AI_TASK_PRIORITY_INSIGHT`,
  `AI_TASK_PRIORITY_STATEMENT_EXTRACT` (opcionales, override por tarea).
- `AI_MONTHLY_MESSAGE_LIMIT`, `AI_READ_TIMEOUT_SECONDS`.

**Rate limiting (todas opcionales, tienen default):**

- `RATE_LIMIT_LOGIN_MAX_REQUESTS`, `RATE_LIMIT_LOGIN_WINDOW_SECONDS`.
- `RATE_LIMIT_REGISTER_MAX_REQUESTS`, `RATE_LIMIT_REGISTER_WINDOW_SECONDS`.
- `RATE_LIMIT_AI_CHAT_MAX_REQUESTS`, `RATE_LIMIT_AI_CHAT_WINDOW_SECONDS`.
- `RATE_LIMIT_RECEIPT_SCAN_MAX_REQUESTS`, `RATE_LIMIT_RECEIPT_SCAN_WINDOW_SECONDS`.

**Jobs programados (todas opcionales, tienen default):**

- `APP_JOBS_ENABLED`, `APP_JOBS_PAYMENT_REMINDER_CRON`, `APP_JOBS_WEEKLY_SUMMARY_CRON`,
  `APP_JOBS_INACTIVITY_REMINDER_CRON`, `APP_JOBS_MONTH_END_PREDICTION_CRON`,
  `APP_JOBS_CARD_CYCLE_CLOSE_CRON`.

**Telegram (opcional):**

- `TELEGRAM_WEBHOOK_SECRET`.

**Eliminadas respecto a FinSmart (no aplican a KoroFin mobile-only):**

- `APP_CORS_ALLOWED_ORIGINS`, `JWT_REFRESH_COOKIE_NAME`, `JWT_REFRESH_COOKIE_SECURE`,
  `JWT_REFRESH_COOKIE_SAME_SITE`.

**No son secrets de GitHub (solo relevantes en local/docker-compose):**

- `DB_PORT_EXTERNAL`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` — usadas únicamente como fallback dentro
  del valor por defecto de `SPRING_DATASOURCE_URL` para desarrollo local; en Cloud Run
  `SPRING_DATASOURCE_URL` siempre se fija directo.

---

## 15. Plan de tests para KoroFin

**Contexto obligatorio para la fase de implementación:** el usuario tiene **TDD estricto activado**
(`Strict TDD Mode: enabled` en su configuración global) — cada unidad de trabajo debe escribir el
test que falla primero, luego el código mínimo para pasarlo, luego refactorizar. Esto aplica a
*todo* el código nuevo del backend de KoroFin, no solo a lo listado abajo como "obligatorio".

### 15.1 Por capa

| Capa | Herramienta | Qué cubre | Patrón a seguir |
|---|---|---|---|
| Repository (queries custom, `@Query`, Specifications, proyecciones, constraints únicos) | `@DataJpaTest` + **Testcontainers Postgres** | Que la query devuelva lo esperado contra un Postgres real, no una aproximación de H2 | Nuevo respecto a FinSmart — las migraciones Flyway usan sintaxis específica de Postgres (tipos, índices parciales, etc.); H2 en modo compatibilidad no es fiable para validarlas. |
| Service | JUnit 5 + Mockito + AssertJ | Lógica de negocio pura, con repositorios/colaboradores mockeados | Igual al patrón actual de FinSmart (`*ServiceTest`) — ya es sólido, se mantiene. |
| Controller | `@WebMvcTest` + `MockMvc` | Serialización, validación de `@RequestBody`, códigos de estado, mapeo de excepciones vía `GlobalExceptionHandler` | Igual al patrón actual (`*ControllerTest`). |
| Filtros de seguridad (`JwtAuthenticationFilter`, `RateLimitFilter`, `TelegramWebhookFilter`) | Unit test standalone, sin contexto Spring completo | Comportamiento del filtro aislado | Igual al patrón actual — construir el filtro directo con sus dependencias `@Value` primitivas. |
| Jobs `@Scheduled` | Unit test con `Clock` fijo inyectado | Determinismo de fecha/hora, idempotencia, catch-up tras downtime | Igual al patrón actual (`*JobTest`). |
| Cliente HTTP de proveedores de IA | Mapeo de errores probado directo (métodos package-private) + al menos un test de happy-path con `MockRestServiceServer` o WireMock contra el contrato real | Que el mapeo de status HTTP → excepción tipada sea correcto, y que el request/response real contra el contrato OpenAI-compatible funcione | El mapeo de errores ya está bien cubierto en FinSmart; agregar el happy-path con `MockRestServiceServer` es una mejora, no existía antes explícitamente confirmado. |
| Integración de punta a punta | `@SpringBootTest` + Testcontainers Postgres, 1-2 tests por flujo crítico | Que el wiring completo (controller → service → repository → DB) funcione para: registro+login+refresh, crear gasto, chat de IA con un proveedor stub | Nuevo — FinSmart no parece tener este nivel (no se encontró un test así en el listado), y es la única capa que detecta errores de *wiring* que las capas aisladas no ven. |

### 15.2 Cobertura mínima razonable por dominio

- **Dominios con lógica de negocio no trivial** (`user`/auth, `ai` — orquestador + registry, `card`
  — cierre de ciclo y amortización, `debt` — cargos/pagos atómicos, `statement` — extracción y
  dedup, `notification` — dispatcher multi-canal): **80% de cobertura de línea en `service/`**,
  medido con JaCoCo.
- **Dominios CRUD simples** (`income`, `expense` sin contar `ExpenseSpecifications`,
  `recurringpayment`): **70%** es razonable — el valor marginal de perseguir 90%+ en un CRUD plano
  es bajo comparado con el costo de mantenimiento del test.
- **`controller/`:** cobertura de comportamiento (no de línea) — cada endpoint necesita al menos un
  test de camino feliz + un test por cada código de error propio que devuelve (400/401/403/404/409/
  422/429), no una meta de porcentaje.
- **Filtros de seguridad y jobs:** 100% de las ramas de decisión (no es negociable — son la
  superficie de seguridad y de idempotencia contra dinero real).
- La cobertura es una señal, no el objetivo — coherente con TDD estricto: el test se escribe porque
  describe el comportamiento requerido, no para inflar un número. JaCoCo se agrega desde el
  `pom.xml` inicial con un *gate* de build en CI (fallar el build si un módulo cae por debajo de su
  mínimo), para que la meta sea visible y no se erosione con el tiempo.
