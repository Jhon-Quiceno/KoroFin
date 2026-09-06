import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/data/repositories/user_repository.dart';
import 'package:korofin_mobile/models/user_preferences.dart';

import '../support/capturing_adapter.dart';

UserRepository _repo(CapturingAdapter a) => UserRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

void main() {
  test('ThemePreference mapea a ThemeMode y de vuelta', () {
    expect(ThemePreference.fromWire('DARK').mode, ThemeMode.dark);
    expect(ThemePreference.fromMode(ThemeMode.light), ThemePreference.light);
  });

  test('UserPreferences toJson manda theme/currency/language', () {
    const p = UserPreferences(
      theme: ThemePreference.dark,
      currency: 'USD',
      language: AppLanguage.en,
    );
    expect(p.toJson(), <String, dynamic>{
      'theme': 'DARK',
      'currency': 'USD',
      'language': 'EN',
    });
  });

  test('updatePreferences hace PATCH con los tres campos', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'theme': 'LIGHT',
      'currency': 'COP',
      'language': 'ES',
    });
    await _repo(a).updatePreferences(const UserPreferences(
      theme: ThemePreference.light,
      currency: 'COP',
      language: AppLanguage.es,
    ));
    expect(a.lastRequest.method, 'PATCH');
    expect(a.lastRequest.path, '/api/users/preferences');
    expect((a.lastRequest.data as Map).keys,
        containsAll(<String>['theme', 'currency', 'language']));
  });

  test('updateProfile hace PUT y parsea el User', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'id': 1,
      'name': 'Nuevo Nombre',
      'email': 'nuevo@korofin.local',
      'theme': 'SYSTEM',
      'currency': 'COP',
      'language': 'ES',
    });
    final u = await _repo(a).updateProfile(
        name: 'Nuevo Nombre', email: 'nuevo@korofin.local');
    expect(a.lastRequest.method, 'PUT');
    expect(a.lastRequest.path, '/api/users/profile');
    expect(u.name, 'Nuevo Nombre');
  });

  test('changePassword hace PUT a /password', () async {
    final a = CapturingAdapter(status: 204);
    await _repo(a)
        .changePassword(currentPassword: 'vieja', newPassword: 'nuevaseg');
    expect(a.lastRequest.path, '/api/users/password');
    expect((a.lastRequest.data as Map)['currentPassword'], 'vieja');
  });
}
