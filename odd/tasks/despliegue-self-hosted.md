# Migración de despliegue: Cloud Run -> servidor propio (CachyOS)

Objetivo: mover backend + DB fuera de Cloud Run hacia un PC propio (CachyOS) usando
Docker Compose + Cloudflare Tunnel, dejando toda la config de Cloud Run intacta y
reactivable. Subdominio elegido: `korofin.jhonqui.dev`.

Decisión de notificaciones (ya resuelta, no requiere cambios de código): push (Expo) como
canal principal, email (Resend, vía `EmailNotificationSender`) solo para cuentas/seguridad.
No se autohospeda SMTP. Pendiente fuera de alcance: flujo de "recuperar contraseña por
email" (hoy no existe, solo cambio de contraseña autenticado).

## Checklist

- [x] Liberar `korofin.jhonqui.dev` del proyecto Vercel `fin-smart` (hecho por el usuario)
- [x] Crear cuenta en Cloudflare (hecho por el usuario)
- [x] Pausar el deploy automático a Cloud Run (`deploy-backend.yml`: push -> workflow_dispatch)
- [x] Agregar servicio `cloudflared` a `docker-compose.yml`
- [ ] Agregar `jhonqui.dev` como sitio en Cloudflare y mover nameservers desde name.com
- [ ] Borrar en Cloudflare el registro DNS viejo que usaba FinSmart para ese subdominio (si sigue existiendo tras la importación)
- [ ] Crear el Cloudflare Tunnel (`korofin-tunnel`), hostname público `korofin.jhonqui.dev` -> `http://app:8080`
- [ ] Preparar el PC CachyOS: Docker + docker-compose-plugin, clonar el repo
- [ ] Crear `.env` en el PC con secrets propios (DB, JWT) + `TUNNEL_TOKEN` del túnel
- [ ] Levantar `docker compose up -d db app cloudflared` y verificar `https://korofin.jhonqui.dev/actuator/health`
- [ ] Build del APK: `flutter build apk --dart-define=API_BASE_URL=https://korofin.jhonqui.dev`
- [x] Verificación end-to-end: `https://korofin.jhonqui.dev/actuator/health` responde `{"status":"UP"}` a través del túnel
- [x] Commit `16a9055` en develop, push a origin/develop, merge `5c83a4e` a main + push, servidor alineado al commit real (`git checkout -- docker-compose.yml && git pull`)
- [x] APK instalado y probado por el usuario — funciona de punta a punta, backend self-hosted + túnel + app móvil

## Bugs encontrados y arreglados durante la verificación

- `AndroidManifest.xml` sin permiso `INTERNET` (commit `3795d1b`): Flutter lo inyecta solo en debug, nunca se notó hasta el primer APK de release real.
- `docker-compose.yml` con `restart: on-failure` (o sin declarar en `db`) en vez de `unless-stopped` (commit `5e1c852`): no sobrevivía a un reboot del PC.
- PC del usuario (CachyOS/KDE): `DOCKER_HOST` quedaba forzado al socket de Podman por algo fuera de systemd --user y de todos los dotfiles de shell conocidos (origen nunca identificado con certeza) — se resolvió forzando el valor correcto en `~/.config/fish/config.fish`. Aparte, el grupo `docker` del usuario no tomaba efecto hasta reiniciar sesión/PC.

## Cierre

Self-hosting en CachyOS + Cloudflare Tunnel (`korofin.jhonqui.dev`) verificado end-to-end por el usuario: backend, DB y túnel arriba, sobreviven un reboot, y el APK conecta bien desde datos móviles. Cloud Run queda pausado (trigger manual) pero intacto para reactivar cuando se necesite.

## Notas

- `PINNED_CERT_SHA256` queda vacío: el TLS lo termina Cloudflare, pinearlo es frágil.
- Secrets del entorno self-hosted son propios, no se reutilizan los de Cloud Run.
