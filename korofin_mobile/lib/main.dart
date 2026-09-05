import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'routes/app_router.dart';
import 'theme/app_theme.dart';
import 'theme/theme_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_CO');
  runApp(const ProviderScope(child: KoroFinApp()));
}

/// KoroFin — Finanzas personales, más claras.
///
/// Root widget: wires the centralized dark/light [AppTheme], the
/// [ThemeController] toggle (set from Configuración/Perfil) and the
/// go_router-based navigation graph.
class KoroFinApp extends StatelessWidget {
  const KoroFinApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: ThemeController.mode,
      builder: (context, mode, _) {
        return MaterialApp.router(
          title: 'KoroFin',
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light(),
          darkTheme: AppTheme.dark(),
          themeMode: mode,
          routerConfig: appRouter,
        );
      },
    );
  }
}
