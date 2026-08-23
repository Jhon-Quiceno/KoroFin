# KoroFin

Aplicación de finanzas personales, **solo móvil** (sin frontend web). Monorepo con dos proyectos:

- `korofin_mobile/` — App Flutter (Dart), organizada por tipo + feature: `lib/screens/<feature>/`, `lib/widgets/<tipo>/`, `lib/theme/`, `lib/models/`, `lib/routes/`, `lib/data/`.
- `korofin-backend/` — API REST en Java 21 + Spring Boot 4 (Maven). Paquete base: `com.korofin.backend`, organizado **por capa técnica primero** (`controller/`, `service/`, `repository/`, `dto/`, `entity/`, `mapper/`), con el dominio de negocio anidado adentro de cada capa (`controller/user/`, `service/expense/`, etc. — ver `docs/backend-plan.md` para el detalle completo y el porqué de esta decisión).

Reemplaza al proyecto anterior `FinSmart` (que sí tenía frontend web en Next.js). Ver `docs/backend-plan.md` para el mapeo completo de decisiones de arquitectura del backend.

## Convenciones obligatorias

Leer y aplicar `docs/convenciones.md`. Resumen no negociable:

- **Commits, PRs y comentarios de código: SIEMPRE en español** (conventional commits con tipo en inglés y descripción en español; sin atribución de IA).
- Identificadores de código en inglés.
- Rama base de trabajo y de PRs: `develop`. `main` solo para releases.

## TDD estricto

Este proyecto usa TDD estricto en el backend: test que falla primero, código mínimo para pasarlo, después refactor. No se escribe código de producción sin su test.

## Comandos

- Backend: `cd korofin-backend && ./mvnw.cmd compile` / `./mvnw.cmd test`
- Mobile: `cd korofin_mobile && flutter analyze` / `flutter test` / `flutter run`

## Seguridad

Auth mobile-only: bearer access token + refresh token en el body JSON (sin cookies, sin CSRF, sin CORS de aplicación). Ver sección 3 de `docs/backend-plan.md` para el detalle completo del flujo.
