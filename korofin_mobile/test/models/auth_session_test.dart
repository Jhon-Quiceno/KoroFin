import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/models/auth_session.dart';

void main() {
  test('parsea el AuthResponse real del backend', () {
    // Respuesta literal de POST /api/users/login contra el backend local.
    final json = <String, dynamic>{
      'accessToken': 'access.jwt.token',
      'tokenType': 'Bearer',
      'expiresIn': 900,
      'user': <String, dynamic>{
        'id': 1,
        'name': 'Smoke Test',
        'email': 'smoke@korofin.local',
        'theme': 'SYSTEM',
        'currency': 'COP',
        'language': 'ES',
      },
      'refreshToken': 'refresh.jwt.token',
    };

    final session = AuthSession.fromJson(json);

    expect(session.accessToken, 'access.jwt.token');
    expect(session.refreshToken, 'refresh.jwt.token');
    expect(session.expiresIn, 900);
    expect(session.user.id, 1);
    expect(session.user.name, 'Smoke Test');
    expect(session.user.currency, 'COP');
  });
}
