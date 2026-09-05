import '../../core/network/api_client.dart';
import '../../models/user.dart';
import '../../models/user_preferences.dart';

/// Acceso a `/api/users/*` para perfil, contraseña y preferencias (el login /
/// registro / refresh viven en `AuthRepository`).
class UserRepository {
  UserRepository(this._client);

  final ApiClient _client;

  Future<UserPreferences> getPreferences() async {
    final response = await _client.get('/api/users/preferences');
    return UserPreferences.fromJson(response.data as Map<String, dynamic>);
  }

  Future<UserPreferences> updatePreferences(UserPreferences prefs) async {
    final response =
        await _client.patch('/api/users/preferences', body: prefs.toJson());
    return UserPreferences.fromJson(response.data as Map<String, dynamic>);
  }

  Future<User> updateProfile({
    required String name,
    required String email,
  }) async {
    final response = await _client.put(
      '/api/users/profile',
      body: <String, dynamic>{'name': name, 'email': email},
    );
    return User.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _client.put(
      '/api/users/password',
      body: <String, dynamic>{
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      },
    );
  }
}
