import 'dart:async';

import 'package:flutter/material.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Pantalla puente mientras el bootstrap de sesión decide si hay que ir al
/// login o al home.
///
/// Esa espera no siempre es corta: en el plan Free de Render el backend se
/// suspende tras 15 min sin tráfico y volver a arrancarlo tarda entre 188 s y
/// 225 s de proceso (7 arranques medidos; ver `AppConfig.coldStartTimeout`).
/// Un splash callado durante casi cuatro minutos es indistinguible de una app
/// colgada, así que a medida que la espera se alarga la pantalla va diciendo
/// qué está pasando.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  /// A partir de acá se explica que el servidor está despertando. Por debajo de
  /// este umbral la espera es normal y un mensaje solo haría ruido.
  static const Duration hintDelay = Duration(seconds: 6);

  /// A partir de acá se avisa que puede tardar varios minutos, para que nadie
  /// cierre la app creyendo que se trabó.
  static const Duration longWaitDelay = Duration(seconds: 25);

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  Timer? _hintTimer;
  Timer? _longWaitTimer;

  bool _showHint = false;
  bool _showLongWait = false;

  @override
  void initState() {
    super.initState();
    _hintTimer = Timer(SplashScreen.hintDelay, () {
      if (mounted) setState(() => _showHint = true);
    });
    _longWaitTimer = Timer(SplashScreen.longWaitDelay, () {
      if (mounted) setState(() => _showLongWait = true);
    });
  }

  @override
  void dispose() {
    _hintTimer?.cancel();
    _longWaitTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xxl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: koro.accent,
                  borderRadius: BorderRadius.circular(AppRadii.lg),
                ),
                child: const Center(
                  child: Text(
                    'K',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 30,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.xl),
              const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(strokeWidth: 2.5),
              ),
              if (_showHint) ...[
                const SizedBox(height: AppSpacing.xl),
                Text(
                  'Despertando el servidor…',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
              ],
              if (_showLongWait) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'La primera conexión del día puede tardar unos minutos. '
                  'Dejá la app abierta.',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
