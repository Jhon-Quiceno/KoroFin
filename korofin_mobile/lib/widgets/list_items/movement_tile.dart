import 'package:flutter/material.dart';

import '../../data/category_visuals.dart';
import '../../data/formatters.dart';
import '../../models/movement.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Fila de un movimiento (gasto o ingreso): ícono + color derivados de la
/// categoría, título, fecha relativa y monto (verde con `+` para ingresos).
class MovementTile extends StatelessWidget {
  const MovementTile({super.key, required this.movement, this.onTap});

  final Movement movement;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final Color color = CategoryVisuals.colorForName(
      movement.categoryName,
      income: movement.isIncome,
    );
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.md),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(AppRadii.md),
              ),
              child: Icon(
                CategoryVisuals.iconForName(movement.categoryName),
                size: 18,
                color: color,
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    movement.title,
                    style: Theme.of(context).textTheme.titleMedium,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${movement.categoryName ?? 'Sin categoría'} · '
                    '${AppFormatters.relative(movement.date)}',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
            Text(
              '${movement.isIncome ? '+' : '-'}'
              '${AppFormatters.currency(movement.amount)}',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: movement.isIncome ? koro.success : koro.foreground,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
