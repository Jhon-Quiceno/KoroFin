import 'package:flutter/material.dart';

/// Design tokens extracted from the v0 prototype "KoroFin — Premium Oscuro".
///
/// Palette is Tailwind-slate based with a single red accent used sparingly
/// for primary CTAs, destructive actions and alerts.
class AppColors {
  AppColors._();

  // Shared accent — identical in both light and dark mode.
  static const Color accent = Color(0xFFDC2626); // red-600
  static const Color accentHover = Color(0xFFB91C1C); // red-700
  static const Color destructive = accent;

  // Semantic status colors (used across charts, badges, progress bars).
  static const Color success = Color(0xFF16A34A); // green-600
  static const Color warning = Color(0xFFF59E0B); // amber-500
  static const Color info = Color(0xFF2563EB); // blue-600

  // ---- Light mode ----
  static const Color lightPrimary = Color(0xFF1E293B); // slate-800
  static const Color lightOnPrimary = Color(0xFFFFFFFF);
  static const Color lightSecondary = Color(0xFF334155); // slate-700
  static const Color lightBackground = Color(0xFFF8FAFC); // slate-50
  static const Color lightForeground = Color(0xFF0F172A); // slate-900
  static const Color lightMuted = Color(0xFFE9EDF1);
  static const Color lightMutedForeground = Color(0xFF64748B); // slate-500
  static const Color lightBorder = Color(0xFFE2E8F0); // slate-200
  static const Color lightSurface = Color(0xFFFFFFFF);

  // ---- Dark mode ----
  static const Color darkBackground = Color(0xFF0F172A); // slate-900
  static const Color darkForeground = Color(0xFFF8FAFC); // slate-50
  static const Color darkSurface = Color(0xFF1E293B); // slate-800
  static const Color darkSurfaceElevated = Color(0xFF334155); // slate-700
  static const Color darkMuted = Color(0xFF1E293B);
  static const Color darkMutedForeground = Color(0xFF94A3B8); // slate-400
  static const Color darkBorder = Color(0xFF334155); // slate-700

  // Category palette used across expense category chips/icons/charts.
  static const List<Color> categoryPalette = <Color>[
    Color(0xFFDC2626), // rojo
    Color(0xFFF59E0B), // ambar
    Color(0xFF16A34A), // verde
    Color(0xFF2563EB), // azul
    Color(0xFF9333EA), // violeta
    Color(0xFF0D9488), // teal
    Color(0xFFDB2777), // rosa
    Color(0xFF64748B), // slate
  ];
}
