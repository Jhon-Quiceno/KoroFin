import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';

/// Símbolo de KoroFin: una moneda repartida por una K. Las tres piezas de la
/// moneda van en [color] y la cuña de la derecha en [accentColor].
///
/// Se dibuja con un [CustomPainter] a partir de la misma geometría del SVG
/// maestro (`design/identidad/korofin-symbol.svg`, lienzo de 256), así que
/// escala nítido a cualquier tamaño sin depender de un paquete de SVG.
class KoroMark extends StatelessWidget {
  const KoroMark({
    super.key,
    this.size = 44,
    this.color,
    this.accentColor,
    this.semanticLabel = 'KoroFin',
  });

  final double size;

  /// Por defecto, el color de texto principal del tema.
  final Color? color;

  /// Por defecto, el verde de marca que corresponde al brillo del tema.
  final Color? accentColor;

  /// `null` para un uso decorativo (no se anuncia a lectores de pantalla).
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    final Widget mark = CustomPaint(
      size: Size.square(size),
      painter: _KoroMarkPainter(
        color: color ?? theme.colorScheme.onSurface,
        accentColor: accentColor ??
            (isDark ? AppColors.brandMint : AppColors.brandEmerald),
      ),
    );
    if (semanticLabel == null) return ExcludeSemantics(child: mark);
    return Semantics(label: semanticLabel, image: true, child: mark);
  }
}

class _KoroMarkPainter extends CustomPainter {
  const _KoroMarkPainter({required this.color, required this.accentColor});

  final Color color;
  final Color accentColor;

  static const Radius _r = Radius.circular(112);

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 256, size.height / 256);

    final Path coin = Path()
      // Pieza izquierda.
      ..moveTo(83, 25.44)
      ..arcToPoint(const Offset(83, 230.56), radius: _r, clockwise: false)
      ..close()
      // Pieza superior derecha.
      ..moveTo(101, 19.3)
      ..arcToPoint(const Offset(184.8, 31.47), radius: _r)
      ..lineTo(101, 115.27)
      ..close()
      // Pieza inferior derecha.
      ..moveTo(101, 236.7)
      ..arcToPoint(const Offset(184.8, 224.53), radius: _r, clockwise: false)
      ..lineTo(101, 140.73)
      ..close();

    final Path wedge = Path()
      ..moveTo(113.73, 128)
      ..lineTo(199.74, 41.99)
      ..arcToPoint(const Offset(199.74, 214.01), radius: _r)
      ..close();

    canvas.drawPath(coin, Paint()..color = color);
    canvas.drawPath(wedge, Paint()..color = accentColor);
  }

  @override
  bool shouldRepaint(_KoroMarkPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.accentColor != accentColor;
}
