import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/screens/shell/splash_screen.dart';
import 'package:korofin_mobile/theme/app_theme.dart';

/// El splash cubre el arranque en frío del backend (plan Free de Render, ~200 s
/// medidos). Callado se ve igual que una app colgada, así que a partir de cierto
/// tiempo tiene que decir qué está pasando.
void main() {
  // El splash lee los colores de marca vía `context.koroColors`, que es una
  // extensión del tema de KoroFin: con un MaterialApp pelado no existe.
  Future<void> pumpSplash(WidgetTester tester) => tester.pumpWidget(
        MaterialApp(theme: AppTheme.light(), home: const SplashScreen()),
      );

  testWidgets('al principio solo muestra el indicador, sin texto de espera',
      (tester) async {
    await pumpSplash(tester);

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.textContaining('Despertando'), findsNothing);
  });

  testWidgets('si la espera se alarga, explica que el servidor está despertando',
      (tester) async {
    await pumpSplash(tester);

    await tester.pump(SplashScreen.hintDelay);
    await tester.pump();

    expect(find.textContaining('Despertando'), findsOneWidget);
  });

  testWidgets('en una espera muy larga avisa que puede tardar unos minutos',
      (tester) async {
    await pumpSplash(tester);

    await tester.pump(SplashScreen.longWaitDelay);
    await tester.pump();

    expect(find.textContaining('minutos'), findsOneWidget);
  });

  testWidgets('no deja timers vivos al desmontarse', (tester) async {
    await pumpSplash(tester);
    await tester.pump(const Duration(seconds: 2));

    await tester.pumpWidget(MaterialApp(theme: AppTheme.light(), home: const SizedBox()));

    // Si el State no cancelara sus timers, `pump` fallaría acá al dispararse
    // un setState sobre un widget ya desmontado.
    await tester.pump(SplashScreen.longWaitDelay);
    expect(tester.takeException(), isNull);
  });
}
