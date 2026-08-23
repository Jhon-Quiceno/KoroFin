import 'package:flutter/material.dart';

/// A spending/income category. Colors and icons mirror the category grid
/// from the v0 "Categorías" screen.
class AppCategory {
  const AppCategory({
    required this.id,
    required this.name,
    required this.icon,
    required this.color,
  });

  final String id;
  final String name;
  final IconData icon;
  final Color color;
}
