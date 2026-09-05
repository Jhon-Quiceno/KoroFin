import 'package:flutter/material.dart';

/// Clasifica si una categoría agrupa gastos o ingresos. En el backend es el
/// enum `CategoryType` (`EXPENSE` / `INCOME`).
enum CategoryKind {
  expense('EXPENSE', 'Gasto'),
  income('INCOME', 'Ingreso');

  const CategoryKind(this.wire, this.label);

  /// Valor tal como viaja en el JSON del backend.
  final String wire;

  /// Etiqueta para mostrar en la UI.
  final String label;

  static CategoryKind fromWire(String value) => CategoryKind.values.firstWhere(
        (k) => k.wire == value,
        orElse: () => CategoryKind.expense,
      );
}

/// Categoría del usuario, tal como la devuelve `/api/categories`
/// (`{ id, name, type }`). El backend **no** guarda ícono ni color: se derivan
/// del nombre en el cliente (ver `category_visuals.dart`).
class Category {
  const Category({
    required this.id,
    required this.name,
    required this.kind,
  });

  final int id;
  final String name;
  final CategoryKind kind;

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      kind: CategoryKind.fromWire(json['type'] as String? ?? 'EXPENSE'),
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'name': name,
        'type': kind.wire,
      };

  Category copyWith({String? name, CategoryKind? kind}) => Category(
        id: id,
        name: name ?? this.name,
        kind: kind ?? this.kind,
      );
}

/// Modelo de UI del prototipo (con ícono y color embebidos). Todavía lo usan las
/// pantallas que siguen con datos falsos (movimientos, dashboard, picker de
/// categorías); se retira cuando esas fases migren al modelo [Category].
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
