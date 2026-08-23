import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../widgets/nav/app_bottom_nav.dart';
import '../quick_add/quick_add_sheet.dart';

/// Wraps the four bottom-nav branches (Inicio, Movimientos, Deudas,
/// Asistente) in a single Scaffold so the bottom nav and the Quick-Add FAB
/// stay persistent while go_router's [StatefulShellRoute] swaps branches.
class ScaffoldWithNav extends StatelessWidget {
  const ScaffoldWithNav({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(bottom: false, child: navigationShell),
      bottomNavigationBar: AppBottomNav(
        currentIndex: navigationShell.currentIndex,
        onTap: (index) => navigationShell.goBranch(index, initialLocation: index == navigationShell.currentIndex),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => showQuickAddSheet(context),
        child: const Icon(Icons.add),
      ),
    );
  }
}
