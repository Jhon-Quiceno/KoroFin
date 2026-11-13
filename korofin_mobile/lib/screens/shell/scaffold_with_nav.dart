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
/// También ofrece el movimiento de Nequi pendiente de
/// [nequiCaptureControllerProvider]: abre el formulario pre-llenado para que
/// el usuario confirme el gasto/ingreso antes de crearlo.
class ScaffoldWithNav extends ConsumerStatefulWidget {
  const ScaffoldWithNav({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  ConsumerState<ScaffoldWithNav> createState() => _ScaffoldWithNavState();
}

class _ScaffoldWithNavState extends ConsumerState<ScaffoldWithNav> {
  /// Evita abrir un segundo formulario encima del que ya se está mostrando.
  bool _showingNequiForm = false;

  @override
  void initState() {
    super.initState();
    // `ref.listen` solo avisa de cambios: si el movimiento quedó pendiente
    // mientras este widget no estaba montado (por ejemplo, detrás de la
    // pantalla de bloqueo con huella), hay que ofrecerlo al montarse.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        unawaited(_offerNequiMatch(ref.read(nequiCaptureControllerProvider)));
      }
    });
  }

  Future<void> _offerNequiMatch(PendingNequiMatch? match) async {
    if (match == null || _showingNequiForm) return;
    _showingNequiForm = true;
    final MovementFormResult? result;
    try {
      result = await showTransactionForm(
        context,
        type: match.type,
        initial: match.toMovement(),
        allowTypeToggle: false,
      );
    } finally {
      _showingNequiForm = false;
    }
    // Si el widget se desmontó no fue el usuario quien cerró el formulario
    // (por ejemplo, entró la pantalla de bloqueo): el movimiento sigue
    // pendiente y se vuelve a ofrecer al montarse de nuevo.
    if (!mounted) return;
    ref.read(nequiCaptureControllerProvider.notifier).dismiss();
    if (result == null) return;
    try {
      final AddOutcome outcome =
          await ref.read(movementsProvider(result.type).notifier).add(result.draft);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(outcome == AddOutcome.queued
              ? 'Sin conexión: se guardó en el dispositivo y se sincronizará automáticamente.'
              : 'Movimiento guardado'),
        ));
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final StatefulNavigationShell navigationShell = widget.navigationShell;
    ref.listen<PendingNequiMatch?>(
      nequiCaptureControllerProvider,
      (previous, next) => unawaited(_offerNequiMatch(next)),
    );

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
