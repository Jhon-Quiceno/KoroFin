import '../../core/network/api_client.dart';
import '../../models/auth_session.dart';

/// Acceso a los endpoints de sesión (`/api/users/*`).
///
/// No guarda nada: solo habla con el backend y devuelve modelos. La
/// persistencia (access token en memoria, refresh token en almacenamiento
/// seguro) la maneja el `AuthController`.
class AuthRepository {
  AuthRepository(this._client);

  final ApiClient _client;

  Future<AuthSession> register({
    required String name,
    required String email,
    required String password,
  }) async {
    final response = await _client.post(
      '/api/users/register',
      body: <String, dynamic>{
        'name': name,
        'email': email,
        'password': password,
      },
    );
    return AuthSession.fromJson(response.data as Map<String, dynamic>);
  }

  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    // `rememberMe` siempre va en `true` al backend para que la rotación del
    // refresh token propague el flag; en el cliente, el refresh token siempre
    // se persiste en almacenamiento seguro.
    final response = await _client.post(
      '/api/users/login',
      body: <String, dynamic>{
        'email': email,
        'password': password,
        'rememberMe': true,
      },
    );
    return AuthSession.fromJson(response.data as Map<String, dynamic>);
  }

  Future<AuthSession> refresh(String refreshToken) async {
    final response = await _client.post(
      '/api/users/refresh',
      body: <String, dynamic>{'refreshToken': refreshToken},
    );
    return AuthSession.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> logout(String refreshToken) async {
    await _client.post(
      '/api/users/logout',
      body: <String, dynamic>{'refreshToken': refreshToken},
    );
  }
}
