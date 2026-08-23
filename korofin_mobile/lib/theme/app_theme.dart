import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_spacing.dart';
import 'app_text_styles.dart';

/// Centralized light/dark ThemeData for KoroFin, replicating the v0
/// "Premium Oscuro" design system (Mercury/Ramp/Linear-inspired fintech
/// look: clean surfaces, bold high-contrast headers, a single red accent).
class AppTheme {
  AppTheme._();

  static ThemeData light() => _build(brightness: Brightness.light);

  static ThemeData dark() => _build(brightness: Brightness.dark);

  static ThemeData _build({required Brightness brightness}) {
    final bool isDark = brightness == Brightness.dark;

    final Color background = isDark ? AppColors.darkBackground : AppColors.lightBackground;
    final Color surface = isDark ? AppColors.darkSurface : AppColors.lightSurface;
    final Color surfaceElevated = isDark ? AppColors.darkSurfaceElevated : AppColors.lightMuted;
    final Color foreground = isDark ? AppColors.darkForeground : AppColors.lightForeground;
    final Color mutedForeground = isDark ? AppColors.darkMutedForeground : AppColors.lightMutedForeground;
    final Color border = isDark ? AppColors.darkBorder : AppColors.lightBorder;
    final Color primary = isDark ? AppColors.darkForeground : AppColors.lightPrimary;
    final Color onPrimary = isDark ? AppColors.darkBackground : AppColors.lightOnPrimary;

    final ColorScheme colorScheme = ColorScheme(
      brightness: brightness,
      primary: primary,
      onPrimary: onPrimary,
      secondary: AppColors.accent,
      onSecondary: Colors.white,
      error: AppColors.destructive,
      onError: Colors.white,
      surface: surface,
      onSurface: foreground,
      surfaceContainerHighest: surfaceElevated,
      outline: border,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: background,
      canvasColor: background,
      splashFactory: InkRipple.splashFactory,
      dividerColor: border,
      fontFamily: AppTextStyles.bodyMedium(foreground).fontFamily,
      textTheme: TextTheme(
        displayLarge: AppTextStyles.displayLarge(foreground),
        headlineLarge: AppTextStyles.headlineLarge(foreground),
        headlineMedium: AppTextStyles.headlineMedium(foreground),
        titleLarge: AppTextStyles.titleLarge(foreground),
        titleMedium: AppTextStyles.titleMedium(foreground),
        bodyLarge: AppTextStyles.bodyLarge(foreground),
        bodyMedium: AppTextStyles.bodyMedium(mutedForeground),
        labelSmall: AppTextStyles.labelSmall(mutedForeground),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: background,
        foregroundColor: foreground,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: AppTextStyles.headlineMedium(foreground),
        iconTheme: IconThemeData(color: foreground),
      ),
      cardTheme: CardThemeData(
        color: surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadii.lg),
          side: BorderSide(color: border),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceElevated,
        labelStyle: AppTextStyles.labelSmall(foreground),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: AppSpacing.xs),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.pill)),
        side: BorderSide.none,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.accent,
          foregroundColor: Colors.white,
          disabledBackgroundColor: AppColors.accent.withValues(alpha: 0.4),
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.md)),
          textStyle: AppTextStyles.titleMedium(Colors.white),
          elevation: 0,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: foreground,
          minimumSize: const Size.fromHeight(52),
          side: BorderSide(color: border),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.md)),
          textStyle: AppTextStyles.titleMedium(foreground),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.accent,
          textStyle: AppTextStyles.bodyMediumMedium(AppColors.accent),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceElevated,
        contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
        hintStyle: AppTextStyles.bodyLarge(mutedForeground),
        labelStyle: AppTextStyles.bodyMedium(mutedForeground),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: const BorderSide(color: AppColors.accent, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: const BorderSide(color: AppColors.destructive),
        ),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected) ? Colors.white : mutedForeground,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected) ? AppColors.accent : surfaceElevated,
        ),
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.accent,
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: surface,
        selectedItemColor: AppColors.accent,
        unselectedItemColor: mutedForeground,
        showUnselectedLabels: true,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: AppColors.accent,
        foregroundColor: Colors.white,
        shape: CircleBorder(),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.lg)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: surface,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadii.xl)),
        ),
      ),
      dividerTheme: DividerThemeData(color: border, thickness: 1, space: 1),
      iconTheme: IconThemeData(color: foreground),
      extensions: <ThemeExtension<dynamic>>[
        KoroFinColors(
          background: background,
          surface: surface,
          surfaceElevated: surfaceElevated,
          foreground: foreground,
          mutedForeground: mutedForeground,
          border: border,
          accent: AppColors.accent,
          success: AppColors.success,
          warning: AppColors.warning,
          info: AppColors.info,
        ),
      ],
    );
  }
}

/// Theme extension exposing the raw KoroFin semantic tokens so widgets can
/// read `Theme.of(context).extension<KoroFinColors>()` instead of branching
/// on `Theme.of(context).brightness` everywhere.
class KoroFinColors extends ThemeExtension<KoroFinColors> {
  const KoroFinColors({
    required this.background,
    required this.surface,
    required this.surfaceElevated,
    required this.foreground,
    required this.mutedForeground,
    required this.border,
    required this.accent,
    required this.success,
    required this.warning,
    required this.info,
  });

  final Color background;
  final Color surface;
  final Color surfaceElevated;
  final Color foreground;
  final Color mutedForeground;
  final Color border;
  final Color accent;
  final Color success;
  final Color warning;
  final Color info;

  @override
  KoroFinColors copyWith({
    Color? background,
    Color? surface,
    Color? surfaceElevated,
    Color? foreground,
    Color? mutedForeground,
    Color? border,
    Color? accent,
    Color? success,
    Color? warning,
    Color? info,
  }) {
    return KoroFinColors(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      surfaceElevated: surfaceElevated ?? this.surfaceElevated,
      foreground: foreground ?? this.foreground,
      mutedForeground: mutedForeground ?? this.mutedForeground,
      border: border ?? this.border,
      accent: accent ?? this.accent,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      info: info ?? this.info,
    );
  }

  @override
  KoroFinColors lerp(ThemeExtension<KoroFinColors>? other, double t) {
    if (other is! KoroFinColors) return this;
    return KoroFinColors(
      background: Color.lerp(background, other.background, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      surfaceElevated: Color.lerp(surfaceElevated, other.surfaceElevated, t)!,
      foreground: Color.lerp(foreground, other.foreground, t)!,
      mutedForeground: Color.lerp(mutedForeground, other.mutedForeground, t)!,
      border: Color.lerp(border, other.border, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      info: Color.lerp(info, other.info, t)!,
    );
  }
}

extension KoroFinColorsX on BuildContext {
  KoroFinColors get koroColors => Theme.of(this).extension<KoroFinColors>()!;
}
