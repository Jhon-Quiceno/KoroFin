import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/data/repositories/user_repository.dart';
import 'package:korofin_mobile/models/user_preferences.dart';
import 'package:korofin_mobile/state/preferences/preferences_controller.dart';
import 'package:korofin_mobile/theme/theme_controller.dart';

class _FakeUserRepository implements UserRepository {
  UserPreferences stored = const UserPreferences(
    theme: ThemePreference.system,
    currency: 'COP',
    language: AppLanguage.es,
  );

  @override
  Future<UserPreferences> getPreferences() async => stored;

  @override
  Future<UserPreferences> updatePreferences(UserPreferences prefs) async {
    stored = prefs;
    return prefs;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

ProviderContainer _container(UserRepository repo) {
  final c = ProviderContainer(overrides: <Override>[
    userRepositoryProvider.overrideWithValue(repo),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  test('build sincroniza el ThemeController con el tema guardado', () async {
    final repo = _FakeUserRepository()
      ..stored = const UserPreferences(
        theme: ThemePreference.dark,
        currency: 'COP',
        language: AppLanguage.es,
      );
    final c = _container(repo);

    await c.read(userPreferencesProvider.future);

    expect(ThemeController.mode.value, ThemeMode.dark);
  });

  test('setTheme persiste y actualiza el ThemeController', () async {
    final repo = _FakeUserRepository();
    final c = _container(repo);
    await c.read(userPreferencesProvider.future);

    await c.read(userPreferencesProvider.notifier).setTheme(ThemePreference.light);

    expect(repo.stored.theme, ThemePreference.light);
    expect(ThemeController.mode.value, ThemeMode.light);
  });

  test('setCurrency manda un PATCH con los tres campos (tema/idioma intactos)',
      () async {
    final repo = _FakeUserRepository();
    final c = _container(repo);
    await c.read(userPreferencesProvider.future);

    await c.read(userPreferencesProvider.notifier).setCurrency('EUR');

    expect(repo.stored.currency, 'EUR');
    expect(repo.stored.language, AppLanguage.es);
  });
}
