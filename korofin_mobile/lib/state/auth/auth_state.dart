import '../../models/user.dart';

enum AuthStatus {
  /// Todavía no se sabe si hay sesión (bootstrap en curso). La UI muestra un
  /// splash mientras dura.
  unknown,

  /// Hay sesión válida.
  authenticated,

  /// No hay sesión: se va al login.
  unauthenticated,
}

/// Estado observable de la sesión.
class AuthState {
  const AuthState({required this.status, this.user});

  const AuthState.unknown() : this(status: AuthStatus.unknown);

  const AuthState.unauthenticated()
      : this(status: AuthStatus.unauthenticated);

  AuthState.authenticated(User user)
      : this(status: AuthStatus.authenticated, user: user);

  final AuthStatus status;
  final User? user;

  bool get isAuthenticated => status == AuthStatus.authenticated;
}
