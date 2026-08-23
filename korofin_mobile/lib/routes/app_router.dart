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

/// Central route table. The four bottom-nav destinations (Inicio,
/// Movimientos, Deudas, Asistente) live inside a [StatefulShellRoute] so
/// the bottom nav + Quick-Add FAB persist across them; every other screen
/// is a normal full-screen push on top.
final GoRouter appRouter = GoRouter(
  initialLocation: '/home',
  routes: [
    GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
    GoRoute(path: '/register', builder: (context, state) => const RegisterScreen()),
    GoRoute(path: '/lock', builder: (context, state) => const BiometricLockScreen()),
    StatefulShellRoute.indexedStack(
      builder: (context, state, navigationShell) => ScaffoldWithNav(navigationShell: navigationShell),
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
              final tab = int.tryParse(state.uri.queryParameters['tab'] ?? '') ?? 0;
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
