import 'user.dart';

/// Sesión devuelta por `POST /api/users/{register,login,refresh}`.
///
/// Forma real del backend:
/// `{ accessToken, tokenType: "Bearer", expiresIn, user, refreshToken }`.
/// El backend **rota** el refresh token en cada `/refresh`, así que el nuevo
/// valor siempre hay que guardarlo.
class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;

  /// Vida del access token en segundos (informativo).
  final int expiresIn;

  final User user;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
      user: User.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}
