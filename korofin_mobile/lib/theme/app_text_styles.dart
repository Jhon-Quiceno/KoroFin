import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Inter type scale (weights 400/500/600/700) matching the "Premium Oscuro"
/// prototype: large, bold headers with high contrast against body text.
class AppTextStyles {
  AppTextStyles._();

  static TextStyle _inter(double size, FontWeight weight, {double? height, double? letterSpacing}) {
    return GoogleFonts.inter(
      fontSize: size,
      fontWeight: weight,
      height: height,
      letterSpacing: letterSpacing,
    );
  }

  static TextStyle displayLarge(Color color) =>
      _inter(32, FontWeight.w700, height: 1.15).copyWith(color: color);

  static TextStyle headlineLarge(Color color) =>
      _inter(24, FontWeight.w700, height: 1.2).copyWith(color: color);

  static TextStyle headlineMedium(Color color) =>
      _inter(20, FontWeight.w700, height: 1.25).copyWith(color: color);

  static TextStyle titleLarge(Color color) =>
      _inter(18, FontWeight.w600, height: 1.3).copyWith(color: color);

  static TextStyle titleMedium(Color color) =>
      _inter(16, FontWeight.w600, height: 1.3).copyWith(color: color);

  static TextStyle bodyLarge(Color color) =>
      _inter(16, FontWeight.w400, height: 1.45).copyWith(color: color);

  static TextStyle bodyMedium(Color color) =>
      _inter(14, FontWeight.w400, height: 1.45).copyWith(color: color);

  static TextStyle bodyMediumMedium(Color color) =>
      _inter(14, FontWeight.w500, height: 1.4).copyWith(color: color);

  static TextStyle labelSmall(Color color) =>
      _inter(12, FontWeight.w500, height: 1.3, letterSpacing: 0.2).copyWith(color: color);

  static TextStyle caption(Color color) =>
      _inter(11, FontWeight.w500, height: 1.3, letterSpacing: 0.3).copyWith(color: color);

  static TextStyle numericLarge(Color color) =>
      _inter(30, FontWeight.w700, height: 1.1, letterSpacing: -0.5).copyWith(color: color);
}
