import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../widgets/nav/app_bottom_nav.dart';
import '../quick_add/quick_add_sheet.dart';

/// Envuelve las cuatro ramas del bottom-nav (Inicio, Movimientos, Deudas,
/// Asistente) en un solo Scaffold para que el nav y el FAB de Quick-Add
/// persistan mientras `StatefulShellRoute` cambia de rama.
class ScaffoldWithNav extends ConsumerWidget {
  const ScaffoldWithNav({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: SafeArea(bottom: false, child: navigationShell),
      bottomNavigationBar: AppBottomNav(
        currentIndex: navigationShell.currentIndex,
        onTap: (index) => navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => showQuickAddSheet(context, ref),
        child: const Icon(Icons.add),
      ),
    );
  }
}
