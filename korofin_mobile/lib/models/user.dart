/// Usuario autenticado, tal como lo devuelve el backend en `AuthResponse.user`
/// y en los endpoints de perfil. `theme`, `currency` y `language` llegan como
/// enums en mayúscula (`SYSTEM`/`LIGHT`/`DARK`, `COP`, `ES`/`EN`).
class User {
  const User({
    required this.id,
    required this.name,
    required this.email,
    required this.theme,
    required this.currency,
    required this.language,
  });

  final int id;
  final String name;
  final String email;
  final String theme;
  final String currency;
  final String language;

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String? ?? '',
      email: json['email'] as String? ?? '',
      theme: json['theme'] as String? ?? 'SYSTEM',
      currency: json['currency'] as String? ?? 'COP',
      language: json['language'] as String? ?? 'ES',
    );
  }
}
