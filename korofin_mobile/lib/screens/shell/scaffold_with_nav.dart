import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../core/notifications/pending_nequi_match.dart';
import '../../state/movements/movements_controller.dart';
import '../../state/nequi_capture/nequi_capture_controller.dart';
import '../../widgets/nav/app_bottom_nav.dart';
import '../movements/transaction_form_sheet.dart';

/// Envuelve las cuatro ramas del bottom-nav (Inicio, Movimientos, Deudas,
/// Asistente) en un solo Scaffold para que el nav persista mientras
/// `StatefulShellRoute` cambia de rama. El FAB es contextual a cada rama (ver
/// `dashboard_screen.dart`, `movements_screen.dart` y `debts_hub_screen.dart`);
/// en Asistente no hay ningún FAB.
///
/// También escucha [nequiCaptureControllerProvider]: cuando se detecta un
/// pago/envío/recibo de plata de Nequi (en vivo, o porque el usuario tocó la
/// notificación propia que se le ofreció), ofrece el formulario pre-llenado
/// para que confirme el gasto/ingreso antes de crearlo.
class ScaffoldWithNav extends ConsumerWidget {
  const ScaffoldWithNav({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  Future<void> _handleNequiMatch(
    BuildContext context,
    WidgetRef ref,
    PendingNequiMatch match,
  ) async {
    ref.read(nequiCaptureControllerProvider.notifier).dismiss();
    final MovementFormResult? result = await showTransactionForm(
      context,
      type: match.type,
      initial: match.toMovement(),
      allowTypeToggle: false,
    );
    if (result == null || !context.mounted) return;
    try {
      await ref.read(movementsProvider(result.type).notifier).add(result.draft);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Movimiento guardado')),
        );
      }
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.listen<PendingNequiMatch?>(nequiCaptureControllerProvider, (previous, next) {
      if (next != null) {
        unawaited(_handleNequiMatch(context, ref, next));
      }
    });

    return Scaffold(
      body: SafeArea(bottom: false, child: navigationShell),
      bottomNavigationBar: AppBottomNav(
        currentIndex: navigationShell.currentIndex,
        onTap: (index) => navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        ),
      ),
    );
  }
}
