import 'package:flutter/material.dart';

/// Preferencia de tema. En el backend es `ThemePreference`.
enum ThemePreference {
  system('SYSTEM', 'Sistema', ThemeMode.system),
  light('LIGHT', 'Claro', ThemeMode.light),
  dark('DARK', 'Oscuro', ThemeMode.dark);

  const ThemePreference(this.wire, this.label, this.mode);
  final String wire;
  final String label;
  final ThemeMode mode;

  static ThemePreference fromWire(String? v) => ThemePreference.values
      .firstWhere((t) => t.wire == v, orElse: () => ThemePreference.system);

  static ThemePreference fromMode(ThemeMode m) => ThemePreference.values
      .firstWhere((t) => t.mode == m, orElse: () => ThemePreference.system);
}

/// Idioma de la app. En el backend es `AppLanguage`.
enum AppLanguage {
  es('ES', 'Español'),
  en('EN', 'English');

  const AppLanguage(this.wire, this.label);
  final String wire;
  final String label;

  static AppLanguage fromWire(String? v) => AppLanguage.values
      .firstWhere((l) => l.wire == v, orElse: () => AppLanguage.es);
}

/// Monedas soportadas por el backend (regex `COP|USD|MXN|ARS|EUR`).
const List<String> supportedCurrencies = <String>['COP', 'USD', 'MXN', 'ARS', 'EUR'];

/// Preferencias del usuario: `GET/PATCH /api/users/preferences`.
class UserPreferences {
  const UserPreferences({
    required this.theme,
    required this.currency,
    required this.language,
  });

  final ThemePreference theme;
  final String currency;
  final AppLanguage language;

  UserPreferences copyWith({
    ThemePreference? theme,
    String? currency,
    AppLanguage? language,
  }) =>
      UserPreferences(
        theme: theme ?? this.theme,
        currency: currency ?? this.currency,
        language: language ?? this.language,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'theme': theme.wire,
        'currency': currency,
        'language': language.wire,
      };

  factory UserPreferences.fromJson(Map<String, dynamic> json) => UserPreferences(
        theme: ThemePreference.fromWire(json['theme'] as String?),
        currency: json['currency'] as String? ?? 'COP',
        language: AppLanguage.fromWire(json['language'] as String?),
      );
}
