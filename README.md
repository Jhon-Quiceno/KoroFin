<div align="center">
  <img src="./assets/branding/logo_korofin.svg" alt="KoroFin Logo" width="96" height="96">

  <h1 align="center" style="font-size: 2.5rem; margin-top: 0.5rem;">KoroFin</h1>

  <p align="center">
    <strong>Plataforma Inteligente de Gestión Financiera Personal</strong>
    <br />
    <em>Tu dinero, bajo control. Tu futuro, mejor planificado.</em>
  </p>

  <br />

  <a href="./korofin_mobile/pubspec.yaml"><img src="https://img.shields.io/badge/Flutter-stable-%2302569B?style=flat&logo=flutter" alt="Flutter" /></a>
  <a href="./korofin_mobile/pubspec.yaml"><img src="https://img.shields.io/badge/Dart-3.x-%230175C2?style=flat&logo=dart" alt="Dart" /></a>
  <a href="./korofin-backend/pom.xml"><img src="https://img.shields.io/badge/Java-21-%23ED8B00?style=flat&logo=openjdk" alt="Java 21" /></a>
  <a href="./korofin-backend/pom.xml"><img src="https://img.shields.io/badge/Spring_Boot-4.0-%236DB33F?style=flat&logo=springboot" alt="Spring Boot 4.0" /></a>
  <a href="./docker-compose.yml"><img src="https://img.shields.io/badge/PostgreSQL-16-%234169E1?style=flat&logo=postgresql" alt="PostgreSQL 16" /></a>
  <br />
  <img src="https://img.shields.io/badge/IA-5_Providers-%234A90E2?style=flat" alt="Multi-Provider AI" />
  <img src="https://img.shields.io/badge/JWT-Bearer_Auth-%23000000?style=flat&logo=jsonwebtokens" alt="JWT Bearer Auth" />
  <img src="https://img.shields.io/badge/status-active_development-%2322c55e?style=flat" alt="Status" />
</div>

<br />

---

## 🚀 ¿Qué es KoroFin?

**KoroFin** es una app móvil de finanzas personales con un **asistente financiero inteligente 24/7** que analiza tus hábitos, anticipa problemas y te da recomendaciones personalizadas para mejorar tu salud económica.

> Registra ingresos y gastos, controla deudas y tarjetas de crédito, gestiona servicios recurrentes, importá extractos bancarios, escaneá recibos, recibí alertas predictivas y consultá a un asistente IA que conoce TUS finanzas reales — todo desde el celular.

Este proyecto reemplaza a `FinSmart`, su predecesor (que incluía un frontend web en Next.js). **KoroFin es solo móvil**, con un backend rediseñado desde cero sobre la base de lo que ya funcionaba.

<br />

## ✨ De un vistazo (18 pantallas)

<table>
  <tr>
    <td align="center"><strong>📊 Dashboard</strong><br />Balance, gráficos, alertas contextuales</td>
    <td align="center"><strong>💰 Movimientos</strong><br />Gastos e ingresos, categorización IA</td>
    <td align="center"><strong>📋 Hub de Deudas</strong><br />Deudas, tarjetas y servicios en un solo lugar</td>
  </tr>
  <tr>
    <td align="center"><strong>🧾 Escaneo de recibos</strong><br />Foto → gasto extraído por IA</td>
    <td align="center"><strong>📥 Importar extractos</strong><br />PDF/CSV/XLSX con detección de duplicados</td>
    <td align="center"><strong>🤖 Asistente IA</strong><br />Chat contextual con tus datos reales</td>
  </tr>
  <tr>
    <td align="center"><strong>📈 Reportes</strong><br />Análisis, comparativas, exportación CSV</td>
    <td align="center"><strong>🔔 Notificaciones</strong><br />Vencimientos, alertas de sobregasto, predicciones</td>
    <td align="center"><strong>⚙️ Configuración</strong><br />Perfil, categorías, Telegram, preferencias</td>
  </tr>
</table>

<br />

## 🏗️ Arquitectura

```
┌──────────────────────────────────────────────────────────┐
│                  MOBILE (Flutter / Dart)                  │
│      go_router · fl_chart · tema oscuro premium           │
│      flutter_secure_storage (tokens en Keychain/Keystore) │
└────────────────────────┬─────────────────────────────────┘
                         │  HTTP REST (Bearer JWT, sin cookies)
┌────────────────────────▼─────────────────────────────────┐
│              BACKEND (Spring Boot 4, Java 21)              │
│   Paquetes por dominio de negocio, capa técnica adentro     │
│    ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│    │Controller│→ │ Service  │→ │Repository│  + Mappers    │
│    └──────────┘  └──────────┘  └──────────┘              │
└──────────┬──────────────────────────────┬─────────────────┘
           │                              │
┌──────────▼──────┐             ┌─────────▼──────────────┐
│   PostgreSQL 16  │             │    Multi-Provider AI    │
│  + Flyway Migs   │             │ NVIDIA·Gemini·OpenCode  │
└─────────────────┘             │  ·OpenRouter·Groq       │
           │                    └────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────────┐
│         Telegram (opcional, servidor-a-servidor vía n8n)  │
│         Registro de gastos por chat, consultas rápidas    │
└────────────────────────────────────────────────────────────┘
```

<br />

## 💎 Stack Tecnológico

### Mobile
| Tecnología | Para qué |
|---|---|
| [Flutter](https://flutter.dev/) | UI multiplataforma, un solo código Dart |
| [go_router](https://pub.dev/packages/go_router) | Navegación declarativa, shell de 4 tabs |
| [fl_chart](https://pub.dev/packages/fl_chart) | Gráficos financieros |
| [google_fonts](https://pub.dev/packages/google_fonts) | Tipografía Inter |
| [flutter_secure_storage](https://pub.dev/packages/flutter_secure_storage) | Refresh token en Keychain (iOS) / Keystore (Android) |

### Backend
| Tecnología | Versión | Para qué |
|---|---|---|
| [Java](https://openjdk.org/) | 21 | LTS moderno con records, pattern matching |
| [Spring Boot](https://spring.io/) | 4.0.7 | Web MVC, Security, Data JPA, Validation, Mail |
| [SpringDoc OpenAPI](https://springdoc.org/) | 3.0.2 | Documentación interactiva de API (solo en `dev`) |
| [Flyway](https://flywaydb.org/) | latest | Migraciones de base de datos versionadas |
| [MapStruct](https://mapstruct.org/) | 1.6.3 | Mapeo DTO ↔ Entidad en compile-time |
| [Lombok](https://projectlombok.org/) | latest | Reducción de boilerplate |
| [JJWT](https://github.com/jwtk/jjwt) | 0.12.6 | Autenticación con tokens JWT (Bearer, sin cookies) |
| [Testcontainers](https://testcontainers.com/) | latest | Tests de repositorio contra Postgres real |
| [JaCoCo](https://www.jacoco.org/jacoco/) | latest | Cobertura de tests con gate en build |

### Base de Datos
| Tecnología | Versión |
|---|---|
| [PostgreSQL](https://www.postgresql.org/) | 16 (Alpine) |

### IA & Automatización
| Tecnología | Uso |
|---|---|
| NVIDIA NIM | Proveedor de IA |
| Google Gemini | Proveedor de IA (con visión, para escaneo de recibos) |
| OpenCode API | Proveedor de IA |
| OpenRouter | Proveedor de IA (con visión) |
| Groq | Proveedor de IA |
| n8n + Telegram | Registro de gastos por chat (opcional) |
| Resend SMTP | Envío de correos transaccionales (opcional, degrada si no está configurado) |

Los 5 proveedores de IA están orquestados con **failover automático en cascada**: si uno falla, se prueba el siguiente, con prioridad configurable global o por tipo de tarea (chat, categorización, insights, extracción de extractos).

### Infraestructura
| Tecnología | Uso |
|---|---|
| Docker | Imagen del backend (multi-stage, no-root) |
| Google Cloud Run | Hosting del backend en producción |
| GitHub Actions | CI (tests + Trivy) y CD (deploy a Cloud Run vía Workload Identity Federation) |

<br />

## 📦 Módulos del backend (12 dominios de negocio)

| Dominio | Responsabilidad |
|---|---|
| `user` | Registro, login, sesión (JWT access+refresh), perfil, preferencias |
| `expense` / `income` | Gastos e ingresos, categorías compartidas |
| `debt` | Deudas, pagos (abonos) y cargos, con actualizaciones atómicas del saldo |
| `card` | Tarjetas de crédito, movimientos, cuotas, cierre de ciclo automático |
| `statement` | Importación de extractos bancarios (PDF/CSV/XLSX) con extracción asistida por IA y detección de duplicados |
| `ai` | Chat contextual, categorización automática, insights, escaneo de recibos, orquestación multi-proveedor |
| `notification` | Notificaciones in-app, email y push, con preferencias por canal |
| `recurringpayment` | Servicios y pagos recurrentes |
| `integration` | Telegram (vínculo de cuenta, registro de gastos por chat) |
| `report` | Reportes exportables (CSV/JSON) |
| `analysis` | Resumen financiero mensual, predicción de fin de mes, recomendaciones |

Ver `docs/backend-plan.md` para el detalle completo de cada dominio, sus endpoints y decisiones de diseño.

<br />

## 🔧 Cómo empezar

### Prerrequisitos

- **Flutter SDK** (canal stable)
- **JDK 21** + Maven (o usar el wrapper `./mvnw`/`./mvnw.cmd` incluido)
- **Docker Desktop** (para PostgreSQL local y para los tests con Testcontainers)
- Opcional: claves de API para los proveedores de IA (el backend funciona sin ninguna, solo sin funciones de IA)

### 1. Backend

```bash
cp .env.example .env    # completar valores locales (raíz del repo, lo lee docker-compose.yml)
docker compose up db -d
cd korofin-backend
./mvnw.cmd clean install -DskipTests
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

La API queda disponible en `http://localhost:8080`. Documentación interactiva (solo en `dev`): `http://localhost:8080/swagger-ui.html`.

### 2. Mobile

```bash
cd korofin_mobile
flutter pub get
flutter run
```

Configurá la URL del backend (IP LAN de tu máquina, no `localhost`, si corrés en un emulador/dispositivo físico) en la configuración de entorno de la app.

<br />

## 🧪 Testing

```bash
# Backend (TDD estricto — test primero, siempre)
cd korofin-backend && ./mvnw.cmd test

# Mobile
cd korofin_mobile && flutter analyze && flutter test
```

<br />

## 📁 Estructura del Proyecto

```
KoroFin/
├── korofin_mobile/              ← App Flutter
│   ├── lib/
│   │   ├── screens/              ← Pantallas por feature (dashboard, movements, debts_hub...)
│   │   ├── widgets/              ← Componentes reutilizables (cards, charts, list_items, nav)
│   │   ├── theme/                ← Tema oscuro premium centralizado
│   │   ├── models/                ← Modelos de datos
│   │   ├── routes/                ← go_router
│   │   └── data/                  ← Formatters, mocks (temporal, hasta integrar con la API)
│   └── test/
│
├── korofin-backend/              ← Spring Boot 4 + Java 21
│   ├── src/main/java/com/korofin/backend/
│   │   ├── user/                  ← Por dominio de negocio, capa técnica anidada adentro
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   └── mapper/
│   │   ├── expense/ debt/ card/ income/ statement/ ai/ report/
│   │   │   notification/ recurringpayment/ integration/ analysis/  ← mismo patrón por dominio
│   │   ├── scheduling/            ← Jobs que cruzan dominios (no anidados en ninguno)
│   │   └── common/
│   │       ├── security/          ← JWT, rate limiting, filtro de Telegram
│   │       ├── config/
│   │       └── exception/
│   ├── src/main/resources/
│   │   ├── db/migration/          ← Flyway migrations (V1 en adelante, esquema limpio)
│   │   └── application*.properties
│   └── Dockerfile
│
├── assets/branding/               ← Logo (heredado de FinSmart, pendiente de actualizar)
└── docs/                          ← Convenciones, plan de arquitectura del backend
```

<br />

## 💰 Modelo de Negocio

**Freemium** escalable:

| Plan Gratuito | Plan Premium |
|---|---|
| Hasta 50 movimientos/mes | Movimientos ilimitados |
| Alertas básicas | IA financiera avanzada |
| Dashboard mensual | Reportes detallados + exportación |
| Resumen semanal | Notificaciones push |
| | Metas de ahorro automáticas |
| | Análisis profundo de hábitos |

<br />

---

<div align="center">
  <sub>
    <strong>KoroFin</strong> — <em>No es solo una app de finanzas. Es tu coach financiero personal.</em>
  </sub>
</div>
