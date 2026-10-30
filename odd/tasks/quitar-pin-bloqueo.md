# Quitar PIN del bloqueo de la app

Rama: `develop`. Test runner: `cd korofin_mobile && flutter test` / `flutter analyze`.

## Specs

- S1: "mejor vamos a quitar el pin" — eliminar por completo el PIN (UI, repositorio, storage, lockout) como mecanismo de bloqueo.
- S2: "se deja en configuracion la activacion de la huella" — en Configuración queda un switch para activar/desactivar el bloqueo por biometría (Face ID/huella), sin PIN de por medio.
- S3: "si no esta activada, se deja el inicio de sesion con correo y contraseña, más nada" — con el bloqueo desactivado, no hay pantalla de bloqueo local al volver del background; la única autenticación es el login normal (email+contraseña) que ya existe.
- S4 (consecuencia de S1): sin PIN como fallback, la pantalla de bloqueo necesita una salida cuando la biometría falla/no responde — se usa "Cerrar sesión" (ya existe `AuthController.logout()`) en vez de un fallback débil.

## Tasks

- [x] T1: `LockStore`/`SecureLockStore`/`InMemoryLockStore` — quitar todo lo de PIN, dejar solo isEnabled/setEnabled. — inline
- [x] T2: `LockRepository` — quitar PBKDF2/hash/salt/lockout/verifyPin, dejar isEnabled/isBiometricAvailable/enable/disable/authenticateWithBiometrics. — inline
- [x] T3: `LockController` — quitar enableWithPin/unlockWithPin/currentPinLockout; agregar `enable()` (biometría). — inline
- [x] T4: `BiometricLockScreen` — quitar UI de PIN/teclado numérico/lockout; agregar botón "Cerrar sesión". — inline
- [x] T5: `settings_screen.dart` `_SecuritySection` — el switch llama directo a `enable()`/`disable()`, sin bottom sheet de PIN; quitar `_SetPinSheet`. Si no hay biometría disponible al activar, avisar en vez de activar. — inline
- [x] T6: Reescribir `test/data/lock_repository_test.dart` y `test/state/lock_controller_test.dart` para el nuevo alcance (sin PIN). — inline
- [x] T7: `flutter analyze` (limpio) + `flutter test` (166 tests, todos en verde). — inline

## Log

- L1 (2026-10-30): Pedido del usuario verbatim: "hay otro porblema, y es que cuanod voy a abrir la app, me pide la huella, pero la coloco y se queda buguado pidiendola, hasta que l quito y me sale para oclocar el pin, mejor vamso a quitar el pin, y se deja en configuracion la activacion de la huella, y si no esta activada, se deja el inicio de sesion con correo y contraseña, más nada"
