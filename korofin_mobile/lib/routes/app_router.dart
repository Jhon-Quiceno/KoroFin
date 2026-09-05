import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../screens/assistant/assistant_screen.dart';
import '../screens/auth/biometric_lock_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/register_screen.dart';
import '../screens/categories/categories_screen.dart';
import '../screens/dashboard/dashboard_screen.dart';
import '../screens/debts_hub/debts_hub_screen.dart';
import '../screens/import_statement/import_statement_screen.dart';
import '../screens/movements/movements_screen.dart';
import '../screens/notifications/notifications_screen.dart';
import '../screens/receipt_scan/receipt_scan_screen.dart';
import '../screens/reports/reports_screen.dart';
import '../screens/settings/settings_screen.dart';
import '../screens/shell/scaffold_with_nav.dart';
import '../screens/telegram/telegram_screen.dart';
import '../state/auth/auth_controller.dart';
import '../state/auth/auth_state.dart';

/// Tabla central de rutas. Las cuatro destinos del bottom-nav (Inicio,
/// Movimientos, Deudas, Asistente) viven dentro de un [StatefulShellRoute] para
/// que el nav + el FAB de Quick-Add persistan; el resto son pushes normales.
///
/// El `redirect` usa el estado de sesión: sin sesión, cualquier ruta protegida
/// manda al login; con sesión, `/login` y `/register` mandan al home. El estado
/// `unknown` (bootstrap en curso) no llega acá — `main.dart` muestra el splash
/// hasta que se resuelve.
final goRouterProvider = Provider<GoRouter>((ref) {
  final _RouterRefresh refresh = _RouterRefresh(ref);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: '/home',
    refreshListenable: refresh,
    redirect: (context, state) {
      final AuthStatus status = ref.read(authControllerProvider).status;
      final String location = state.matchedLocation;
      final bool onAuthScreen =
          location == '/login' || location == '/register';

      if (status == AuthStatus.unauthenticated) {
        return onAuthScreen ? null : '/login';
      }
      if (status == AuthStatus.authenticated && onAuthScreen) {
        return '/home';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      GoRoute(path: '/register', builder: (context, state) => const RegisterScreen()),
      GoRoute(path: '/lock', builder: (context, state) => const BiometricLockScreen()),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            ScaffoldWithNav(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/home', builder: (context, state) => const DashboardScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/movements', builder: (context, state) => const MovementsScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/debts',
              builder: (context, state) {
                final tab =
                    int.tryParse(state.uri.queryParameters['tab'] ?? '') ?? 0;
                return DebtsHubScreen(initialTab: tab);
              },
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/assistant', builder: (context, state) => const AssistantScreen()),
          ]),
        ],
      ),
      GoRoute(path: '/notifications', builder: (context, state) => const NotificationsScreen()),
      GoRoute(path: '/settings', builder: (context, state) => const SettingsScreen()),
      GoRoute(path: '/categories', builder: (context, state) => const CategoriesScreen()),
      GoRoute(path: '/import-statement', builder: (context, state) => const ImportStatementScreen()),
      GoRoute(path: '/reports', builder: (context, state) => const ReportsScreen()),
      GoRoute(path: '/receipt-scan', builder: (context, state) => const ReceiptScanScreen()),
      GoRoute(path: '/telegram', builder: (context, state) => const TelegramScreen()),
    ],
  );
});

/// Puente entre el estado de auth de Riverpod y el `refreshListenable` de
/// go_router: cada cambio de [AuthStatus] fuerza a re-evaluar el `redirect`.
class _RouterRefresh extends ChangeNotifier {
  _RouterRefresh(Ref ref) {
    _subscription = ref.listen<AuthStatus>(
      authControllerProvider.select((state) => state.status),
      (_, _) => notifyListeners(),
    );
  }

  late final ProviderSubscription<AuthStatus> _subscription;

  @override
  void dispose() {
    _subscription.close();
    super.dispose();
  }
}
