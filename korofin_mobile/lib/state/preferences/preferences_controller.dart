import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/user_repository.dart';
import '../../models/user_preferences.dart';
import '../../theme/theme_controller.dart';

final userRepositoryProvider = Provider<UserRepository>(
  (ref) => UserRepository(ref.read(apiClientProvider)),
);

/// Preferencias del usuario (tema, moneda, idioma). Cada cambio hace un PATCH
/// completo (el backend exige los tres campos) y, si tocó el tema, sincroniza
/// el [ThemeController] local.
final userPreferencesProvider =
    AsyncNotifierProvider<UserPreferencesController, UserPreferences>(
  UserPreferencesController.new,
);

class UserPreferencesController extends AsyncNotifier<UserPreferences> {
  UserRepository get _repo => ref.read(userRepositoryProvider);

  @override
  Future<UserPreferences> build() async {
    final UserPreferences prefs = await _repo.getPreferences();
    ThemeController.mode.value = prefs.theme.mode;
    return prefs;
  }

  Future<void> _save(UserPreferences next) async {
    state = AsyncValue<UserPreferences>.data(next); // optimista
    ThemeController.mode.value = next.theme.mode;
    state = await AsyncValue.guard(() => _repo.updatePreferences(next));
    final UserPreferences? applied = state.valueOrNull;
    if (applied != null) ThemeController.mode.value = applied.theme.mode;
  }

  Future<void> setTheme(ThemePreference theme) async {
    final UserPreferences? current = state.valueOrNull;
    if (current == null) return;
    await _save(current.copyWith(theme: theme));
  }

  Future<void> setCurrency(String currency) async {
    final UserPreferences? current = state.valueOrNull;
    if (current == null) return;
    await _save(current.copyWith(currency: currency));
  }

  Future<void> setLanguage(AppLanguage language) async {
    final UserPreferences? current = state.valueOrNull;
    if (current == null) return;
    await _save(current.copyWith(language: language));
  }
}
