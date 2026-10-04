/// Estado observable del bloqueo de la app.
class AppLockState {
  const AppLockState({required this.enabled, required this.locked});

  /// Estado inicial mientras se carga la preferencia persistida: sin bloqueo
  /// activo hasta confirmar lo contrario.
  const AppLockState.initial() : this(enabled: false, locked: false);

  /// True si el usuario activó el bloqueo (PIN/biometría) en Configuración.
  final bool enabled;

  /// True si, estando habilitado, la app requiere autenticarse de nuevo antes
  /// de mostrar cualquier pantalla protegida.
  final bool locked;

  AppLockState copyWith({bool? enabled, bool? locked}) => AppLockState(
        enabled: enabled ?? this.enabled,
        locked: locked ?? this.locked,
      );
}
