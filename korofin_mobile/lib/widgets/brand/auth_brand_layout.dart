import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';
import 'koro_mark.dart';

/// Marco compartido de login y registro: el símbolo de KoroFin como marca de
/// agua en la esquina, el logo con el nombre arriba, y debajo el contenido de
/// cada pantalla.
class AuthBrandLayout extends StatelessWidget {
  const AuthBrandLayout({super.key, required this.child, this.leading});

  final Widget child;

  /// Acción opcional a la izquierda del logo (por ejemplo, volver).
  final Widget? leading;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color watermark = theme.colorScheme.onSurface.withValues(alpha: 0.06);
    return Scaffold(
      body: Stack(
        children: [
          Positioned(
            top: -70,
            right: -110,
            child: KoroMark(
              size: 360,
              color: watermark,
              accentColor: watermark,
              semanticLabel: null,
            ),
          ),
          SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.xxl),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: AppSpacing.lg),
                  Row(
                    children: [
                      if (leading != null) ...[
                        leading!,
                        const SizedBox(width: AppSpacing.xs),
                      ],
                      // Decorativo: el nombre va escrito al lado.
                      const KoroMark(semanticLabel: null),
                      const SizedBox(width: AppSpacing.md),
                      Text(
                        'KoroFin',
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontSize: 22,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.4,
                        ),
                      ),
                    ],
                  ),
                  child,
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
