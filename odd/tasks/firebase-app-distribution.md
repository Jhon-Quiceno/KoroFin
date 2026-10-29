# Distribución automática del APK (Firebase App Distribution)

Objetivo: que cada cambio relevante del mobile termine en un APK instalable sin build
manual de mi parte — GitHub Actions compila y sube a Firebase App Distribution, el
usuario recibe un link/notificación de instalación. Repo: `Jhon-Quiceno/KoroFin`, rama
por defecto `main`. No aplica test runner propio (es infraestructura de CI); verificación
= correr el workflow y confirmar que el APK llega a Firebase y se puede instalar.

## Specs

- S1. "donde podemos desplegar la apk de flutter para dejar todo automatizado y se mas
  facil probar los cambios?" — decisión tomada: Firebase App Distribution (sobre GitHub
  Releases, que era la alternativa más simple sin cuentas nuevas).
- S2. El flujo debe integrarse con lo ya existente: Render hace auto-deploy del backend
  en cada push a `main` (ver `odd/tasks/migracion-render.md`); esto agrega el lado mobile
  al mismo tipo de automatización.

## Decisiones de arquitectura

- Proyecto Firebase nuevo, app Android registrada con el `applicationId` real
  (`com.korofin.korofin_mobile`, confirmado en `android/app/build.gradle.kts`).
- Solo App Distribution — no se agrega el SDK de Firebase a la app (no hace falta
  Analytics/Crashlytics para esto), así que no toca `pubspec.yaml` ni
  `AndroidManifest.xml`, ni compite con lo que está armando el agente de la feature de
  Nequi en paralelo.
- Service account de Google Cloud con el rol de Firebase App Distribution Admin, su JSON
  key va como secret de GitHub Actions (`FIREBASE_SERVICE_ACCOUNT`), más
  `FIREBASE_APP_ID` como secret/var.
- Workflow nuevo `.github/workflows/mobile-distribute.yml`: build release APK con
  `--dart-define=API_BASE_URL=https://korofin-backend.onrender.com` (mismo valor que el
  build manual de hoy) y lo sube con la action `wzieba/Firebase-Distribution-Github-Action`
  a un grupo de testers que incluye el email del usuario.
- Trigger: push a `main` que toque `korofin_mobile/**` (corregido — el usuario no quiere
  un build por cada push a `develop`, `main` es solo para releases, igual que el resto del
  proyecto).

## Tasks

- [x] T1 (S1) — Proyecto Firebase `korofin-1850c` creado, app Android registrada
  (`com.korofin.korofin_mobile`), App ID `1:774529546096:android:99c86ebd0f946442e181b5`.
  Ruta: inline (Claude in Chrome).
- [x] T2 (S1) — Service account `github-actions-distribution@korofin-1850c.iam.gserviceaccount.com`
  creado en Google Cloud IAM con el rol "Administrador de Firebase App Distribution". Ruta:
  inline (Claude in Chrome).
- [x] T3 (S1) — Secrets de GitHub cargados y confirmados en `gh secret list`:
  `FIREBASE_APP_ID` y `FIREBASE_SERVICE_ACCOUNT`. La primera clave generada vía Claude in
  Chrome quedó huérfana (la extensión bloquea la descarga que dispara la propia página,
  nunca llegó a la carpeta de Descargas real) y se borró en GCP por seguridad. El usuario
  generó una segunda clave con un clic real en su navegador
  (`korofin-1850c-23de19f20162.json`, en `Documentos/`), confirmé que era del service
  account correcto (`client_email` verificado) sin imprimir su contenido, y la cargué con
  `gh secret set FIREBASE_SERVICE_ACCOUNT < archivo.json`.
- [x] T4 (S1, S2) — `.github/workflows/mobile-distribute.yml` creado: build release APK
  (mismo `API_BASE_URL` que el build manual) + `wzieba/Firebase-Distribution-Github-Action@v1`
  al grupo `testers`, trigger en push a `develop` sobre `korofin_mobile/**`. Sigue el
  estilo de `ci.yml` (nombres de step en español, `subosito/flutter-action@v2`). Ruta:
  inline.
- [x] T5 (S1) — Grupo `testers` creado en App Distribution (alias `testers`, coincide con
  el workflow), `quicenojale@gmail.com` agregado como verificador. App Distribution
  habilitado para el proyecto. Falta solo disparar el primer build (push a `develop` que
  toque `korofin_mobile/**`, o un trigger manual) para verificar que llegue el link de
  instalación — pendiente del usuario/próximo push real.

## Pendiente antes de que quede activo

- El workflow `.github/workflows/mobile-distribute.yml` todavía no está commiteado ni
  pusheado — sin eso GitHub Actions no lo ve. Falta decisión del usuario sobre cuándo
  commitear (no se commitea nada sin pedido explícito).
- El archivo local `Documentos/korofin-1850c-23de19f20162.json` (la clave privada) sigue
  en el disco del usuario — ya está cargado como secret, recomendable borrarlo del disco
  una vez confirmado, pero no se borra sin que el usuario lo pida.

## Log

- L1 (verbatim): "donde podemos desplegar la apk de flutter para dejar todo automatizado
  y se mas facil probar los cambios?"
- L2: decisión entre Firebase App Distribution vs GitHub Releases — eligió Firebase App
  Distribution.
- L3: verificado `gh auth status` — autenticado como `Jhon-Quiceno`, scopes `repo` +
  `workflow`, repo `Jhon-Quiceno/KoroFin`, rama por defecto `main`. Sin MCP de Firebase
  disponible en la sesión — se hace vía Claude in Chrome (consola web) + `gh` CLI.
