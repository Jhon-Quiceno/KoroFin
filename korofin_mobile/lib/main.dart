import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'routes/app_router.dart';
import 'screens/shell/splash_screen.dart';
import 'state/auth/auth_controller.dart';
import 'state/auth/auth_state.dart';
import 'theme/app_theme.dart';
import 'theme/theme_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_CO');
  runApp(const ProviderScope(child: KoroFinApp()));
}

/// KoroFin — Finanzas personales, más claras.
///
/// Mientras el bootstrap de sesión no resuelve (`AuthStatus.unknown`) se muestra
/// un splash; después se monta el router, que ya decide login vs. home según el
/// estado de sesión.
class KoroFinApp extends ConsumerWidget {
  const KoroFinApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AuthStatus status =
        ref.watch(authControllerProvider.select((state) => state.status));

    return ValueListenableBuilder<ThemeMode>(
      valueListenable: ThemeController.mode,
      builder: (context, mode, _) {
        if (status == AuthStatus.unknown) {
          return MaterialApp(
            title: 'KoroFin',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.light(),
            darkTheme: AppTheme.dark(),
            themeMode: mode,
            home: const SplashScreen(),
          );
        }
        return MaterialApp.router(
          title: 'KoroFin',
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light(),
          darkTheme: AppTheme.dark(),
          themeMode: mode,
          routerConfig: ref.watch(goRouterProvider),
        );
      },
    );
  }
}
