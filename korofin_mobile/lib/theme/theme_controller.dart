import 'package:flutter/material.dart';

/// App-wide light/dark/system theme selector, read by [MaterialApp.router]
/// and written to by the "Configuración/Perfil" screen's theme picker.
class ThemeController {
  ThemeController._();

  static final ValueNotifier<ThemeMode> mode = ValueNotifier(ThemeMode.dark);
}
