import 'package:flutter/material.dart';

import '../models/category.dart';
import '../theme/app_colors.dart';

/// Ícono y color de una categoría, derivados de su nombre.
///
/// El backend solo guarda `{ id, name, type }`, así que la parte visual se
/// resuelve en el cliente: se busca una palabra clave conocida en el nombre
/// para el ícono y, si no hay match, se cae a uno genérico; el color sale de un
/// hash estable del nombre contra la paleta de la app (mismo nombre → mismo
/// color siempre).
class CategoryVisuals {
  const CategoryVisuals._();

  static IconData iconFor(Category category) => _iconForName(category.name);

  static Color colorFor(Category category) {
    if (category.kind == CategoryKind.income) return AppColors.categoryPalette[2];
    final int index =
        category.name.toLowerCase().trim().hashCode.abs() %
            AppColors.categoryPalette.length;
    return AppColors.categoryPalette[index];
  }

  static IconData _iconForName(String rawName) {
    final String name = rawName.toLowerCase();
    for (final entry in _keywordIcons.entries) {
      if (entry.key.any(name.contains)) return entry.value;
    }
    return Icons.category_outlined;
  }

  /// Palabras clave → ícono. El orden no importa: la primera coincidencia gana.
  static const Map<List<String>, IconData> _keywordIcons = <List<String>, IconData>{
    <String>['comida', 'restaur', 'mercado', 'super', 'aliment']:
        Icons.restaurant_outlined,
    <String>['transp', 'uber', 'taxi', 'gasolina', 'combustible', 'bus']:
        Icons.directions_car_outlined,
    <String>['hogar', 'casa', 'arriendo', 'renta', 'servicios public']:
        Icons.home_outlined,
    <String>['entreten', 'cine', 'juego', 'streaming', 'ocio']:
        Icons.movie_outlined,
    <String>['salud', 'medic', 'farmac', 'doctor', 'eps']:
        Icons.medical_services_outlined,
    <String>['educ', 'colegio', 'universidad', 'curso', 'libro']:
        Icons.school_outlined,
    <String>['compra', 'ropa', 'tienda', 'shopping']:
        Icons.shopping_bag_outlined,
    <String>['mascota', 'perro', 'gato', 'veterinar']: Icons.pets_outlined,
    <String>['viaje', 'vuelo', 'hotel', 'turismo']: Icons.flight_outlined,
    <String>['salario', 'sueldo', 'nomina', 'pago']: Icons.payments_outlined,
    <String>['freelance', 'proyecto', 'honorario']: Icons.laptop_mac_outlined,
    <String>['ahorro', 'inversion', 'renta fija']: Icons.savings_outlined,
    <String>['regalo', 'bono', 'premio']: Icons.card_giftcard_outlined,
  };
}
