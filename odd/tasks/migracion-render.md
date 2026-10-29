# Migración de despliegue: self-hosted (CachyOS + Cloudflare Tunnel) -> Render + Neon

Objetivo: retirar el self-hosting en la PC CachyOS (ver [[despliegue-self-hosted]], ya
cerrado) y mover el backend a Render, con la base de datos en Neon (Postgres-compatible),
para poder probar la app sin depender de tener esa PC encendida. Rama: no aplica código de
la app todavía (trabajo de infraestructura); si se agregan archivos (p.ej. `render.yaml`)
van a `develop`. Test runner: N/A (infra) — verificación por `curl /actuator/health`, estado
de deploy en Render, y chequeos SSH en la PC.

## Specs

- S1. Backend en Render: "vamos a subir la aplicacion a render, el backend" — plan **Free**
  para el servicio web (elegido por el usuario: "1" en la pregunta de plan Render).
  Se construye desde el `Dockerfile` ya existente en `korofin-backend/`.
- S2. Base de datos en Neon, no en Render Postgres: "peor si no se puede teenr la base de
  datos en render, va a tocar pasar el proyecto a usar supabase, pero si no estoy mal nos
  toca cambiar muchas cosas en el proyecto, entonces usemos neon" — motivo: el Postgres
  free de Render se borra a los 30 días; Neon es Postgres real (driver JDBC estándar, sin
  cambios de código) y no tiene ese borrado automático.
- S3. Alcance de cuenta: "ojo, render va a queddar en mi cuenta de render, nada más" — usar
  únicamente el workspace existente del usuario (`tea-dbhceq88v5cc7383m7t0`,
  `quicenojale@gmail.com`), no crear workspaces/teams nuevos.
- S4. Limpieza total en la PC: "y el cloudflare quitar todo eso de la pc para dejarla
  normal" — remover por completo el Cloudflare Tunnel y el `docker compose` de KoroFin
  (`korofin-app`, `korofin-db`, `korofin-tunnel`) de la PC CachyOS (`192.168.1.15`, usuario
  `jhon`, SSH puerto 2222), dejándola "normal". No tocar el otro proyecto que corre ahí
  (n8n, visto en las variables de entorno al listar contenedores).
- S5. Motivación/aceptación: "no siempre puedo amntener esa pc ensendida todo el dia... asi
  puedo probar la app de manera seguida sin necesidad de etenr encendida esa pc" — el
  backend en Render debe quedar accesible para probar la app sin depender de la PC.
- S6. Memoria: "al final guarda todo lo que se hace en memoria de engram" — registrar en
  Engram las decisiones y el resultado final de esta migración.
- S7. Secuencia: el MCP de Neon todavía no está conectado en esta sesión; el usuario la va
  a reiniciar para conectarlo: "esta sesion me toca volver a abrila para que los puedas
  leer... dejame la reinicio y le das de una" — retomar sin volver a preguntar en cuanto el
  MCP de Neon esté disponible.

## Tasks

- [x] T1 (S1, S3) — Servicio web creado en Render (plan Free): `korofin-backend`
  (`srv-dbhcps8m7kps73ei7nv0`), workspace `tea-dbhceq88v5cc7383m7t0`, build Docker desde
  `korofin-backend/Dockerfile` (contexto `korofin-backend`), rama `main`, región `ohio`.
  URL: `https://korofin-backend.onrender.com`. Ruta: inline.
- [x] T2 (S2, S7) — Proyecto Neon `korofin-backend` (`dawn-block-76529689`, región
  `aws-us-east-2`), base `korofin`, rol `korofin`. Ruta: inline.
- [x] T3 (S1) — Env vars migradas del `.env` de la CachyOS al servicio de Render (JWT,
  prioridad/keys de IA, Resend/mail), con `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
  apuntando a Neon (endpoint pooled, `sslmode=require&channel_binding=require`). Ruta:
  inline.
- [x] T4 (S1, S5) — Deploy verificado: `GET /actuator/health` → `{"status":"UP"}`
  (groups liveness+readiness); `get_database_tables` en Neon confirma las 21 tablas +
  `flyway_schema_history`, o sea que Flyway corrió las migraciones V1-V7 limpio contra la
  base nueva. Ruta: inline.
- [x] T5 (S5) — Resuelto por el propio pedido del usuario ("dame el apk... la cual apunte
  a ese backend"): el APK apunta directo a `https://korofin-backend.onrender.com`
  (`--dart-define=API_BASE_URL=...`), sin dominio propio. No hace falta repuntar DNS.
- [x] T6 (S4) — Teardown en la PC CachyOS: `korofin-app`/`korofin-db`/`korofin-tunnel`
  parados y removidos; `docker-compose.yml` reescrito dejando solo los servicios de n8n
  (que no estaban corriendo, intactos). `docker ps -a` da vacío — máquina limpia. Se dejó
  a propósito el volumen `postgres_data` sin borrar (dato viejo, ya no referenciado por el
  compose) como red de seguridad barata; borrar si se quiere con
  `docker volume rm korofin_postgres_data`. Pendiente del lado del usuario: el objeto
  Cloudflare Tunnel y el registro DNS de `korofin.jhonqui.dev` siguen existiendo en su
  cuenta de Cloudflare — no hay MCP/API de Cloudflare en esta sesión para borrarlos desde
  acá. Ruta: inline.
- [~] T7 (S1, S2, S4) — Evaluado y recortado: no se encontró contenido en
  `docs/plan-integracion-mobile.md` / `docs/backend-plan.md` que describa el self-host (esa
  bitácora vivía en memoria + en el `odd/tasks/despliegue-self-hosted.md` ya cerrado), así
  que no había nada que reescribir ahí. Este documento es el registro de la migración.
- [x] T8 (S6) — Guardado en Engram (ver Log).

## APK

- Build: `flutter build apk --release --dart-define=API_BASE_URL=https://korofin-backend.onrender.com`
- Archivo: `korofin_mobile/build/app/outputs/flutter-apk/app-release.apk` (~59.7 MB,
  generado 2026-10-28 23:44 hora local de la PC Windows de esta sesión).

## Log

- L1 (verbatim, primer pedido): "Quiero que te conectes a mi pc, donde esta corriendo el
  tunel de cloudflare, lo vamso a quitar, ya que no siempre puedo amntener esa pc ensendida
  todo el dia, en cambio vamso a quitar eso y vamos asubir la aplicacion a render, el
  backend, entonces quitas todo allá y me ayudas o lo haces tu mismo ya que teines el mcp
  de render. asi puedo probar la app de manera seguida sin necesidad de etenr encendida esa
  pc."
- L2: usuario confirmó IP de la PC CachyOS: `192.168.1.15` (SSH puerto 2222, llave
  `~/.ssh/korofin_cachyos`, usuario `jhon` — ya documentado en memoria
  `korofin-self-hosted-deploy`).
- L3: verificado por SSH — PC alcanzable, docker compose instalado, contenedores
  `korofin-app`/`korofin-db`/`korofin-tunnel` corriendo hace 3 semanas, rama `main`, además
  de un proyecto n8n no relacionado en la misma máquina.
- L4: Render MCP confirmado disponible (el usuario tenía razón, no estaba cargado al
  principio de la sesión — corrección reconocida). Un solo workspace
  (`tea-dbhceq88v5cc7383m7t0`), sin servicios creados todavía.
- L5: decisión de plan — backend Free en Render; base de datos en Neon en vez de Render
  Postgres (ver S2) para evitar el borrado a los 30 días del free tier de Render Postgres.
- L6: MCP de Neon aún no conectado en esta sesión; el usuario la reiniciará para
  conectarlo. Hasta entonces, T2 queda bloqueada; T1/T3 parcial (sin connection string de
  DB) puede avanzar.
- L7: aclaraciones del usuario — S3 (solo su cuenta de Render existente) y S4 (limpieza
  total de Cloudflare/docker en la PC, dejarla "normal").
- L8: MCP de Neon reconectado por el usuario; "epmieza con todo lo que te pedi" — luz
  verde para ejecutar T1-T6 sin más preguntas.
- L9: ejecución T1-T2-T3-T4 (Render + Neon arriba, health UP, 21 tablas + Flyway
  verificadas) y T6 (teardown en la CachyOS, `docker-compose.yml` reescrito, contenedores
  de KoroFin removidos, n8n intacto).
- L10: pedido nuevo del usuario a mitad de turno: "dame el apk de mi aplicacion apenas
  este todo listo, la cual apunte a ese backend, ya que borre la que tenia" → T5 se
  resuelve solo (APK directo a la URL de Render). Primer intento de build falló por un
  `cd` erróneo de este asistente (no del proyecto); reintento exitoso.
- L11 (cierre): migración completa y verificada. Backend self-hosted retirado, Render +
  Neon activos, APK nuevo listo en
  `korofin_mobile/build/app/outputs/flutter-apk/app-release.apk`. Pendiente del lado del
  usuario: borrar el Tunnel/DNS en el dashboard de Cloudflare si quiere sacarlo del todo
  (no bloqueante), y decidir si borra el volumen `postgres_data` viejo de la CachyOS.
- L12: el usuario reportó "se me daño todo lo relacionado con el dominio" al intentar
  borrar el CNAME viejo manualmente en Cloudflare. Diagnóstico (MCP de Cloudflare
  conectado solo trae Workers Bindings, sin DNS/Zonas — se usó Claude in Chrome para
  navegar el dashboard real): la zona `jhonqui.dev` ya no existe en la única cuenta de
  Cloudflare del usuario (`c4fb49321c0f1a85d5ad3f27dd5f5e9f`, confirmado decodificando el
  `TUNNEL_TOKEN` viejo — el `"a"` del JWT coincide con ese account id) ni el Tunnel
  (lista vacía en Zero Trust → Networks → Tunnels). Los nameservers en name.com seguían
  apuntando a Cloudflare (`fiona`/`albert.ns.cloudflare.com`) sin zona activa del otro
  lado → dominio "colgado". Usuario eligió volver a los nameservers por defecto de
  name.com. Ejecutado vía Claude in Chrome: name.com → jhonqui.dev → Manage Nameservers →
  "Use default nameservers" (`ns1-ns4.name.com`) → Save. Confirmado:
  "This domain is using name.com nameservers, so DNS records you manage here are active."
  Puede tardar hasta 48h en propagar (aviso del propio name.com). No se tocó el registro A
  del root `jhonqui.dev` (apunta a Vercel, 76.76.21.21 — ajeno a KoroFin) ni se configuró
  ningún registro nuevo en name.com; quedó sin registros DNS propios, a decisión del
  usuario si quiere apuntar algo ahí más adelante.
