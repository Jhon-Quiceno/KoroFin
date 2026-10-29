import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/nequi_capture/nequi_capture_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../../widgets/common/app_badge.dart';
import '../../widgets/common/empty_state.dart';

/// Pantalla de permisos de captura automática de pagos (Nequi). No hay un
/// patrón previo en el proyecto para este tipo de permiso: en vez de un
/// diálogo runtime normal, Android obliga a redirigir a
/// `ACTION_NOTIFICATION_LISTENER_SETTINGS` para que el usuario lo active a
/// mano desde Ajustes.
class NequiCapturePermissionScreen extends ConsumerWidget {
  const NequiCapturePermissionScreen({super.key});

  Future<void> _openSettings(BuildContext context, WidgetRef ref) async {
    await ref.read(nequiCaptureControllerProvider.notifier).requestPermission();
    ref.invalidate(nequiPermissionStatusProvider);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final koro = context.koroColors;
    final AsyncValue<bool> status = ref.watch(nequiPermissionStatusProvider);
    final bool granted = status.valueOrNull ?? false;

    return Scaffold(
      appBar: AppBar(title: const Text('Captura automática de pagos (Nequi)')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(nequiPermissionStatusProvider),
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            EmptyState(
              icon: Icons.notifications_active_outlined,
              title: 'Avisos automáticos de Nequi',
              description:
                  'Cuando llegue una notificación de Nequi de un pago, envío o '
                  'recibo de plata, KoroFin te va a ofrecer un formulario '
                  'pre-llenado para confirmarlo. Nunca se crea el movimiento '
                  'sin que lo confirmes vos.',
              child: Column(
                children: [
                  status.when(
                    loading: () => const Padding(
                      padding: EdgeInsets.all(AppSpacing.sm),
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    error: (_, _) => AppBadge(
                      label: 'No se pudo verificar',
                      color: koro.mutedForeground,
                      icon: Icons.error_outline,
                    ),
                    data: (isGranted) => AppBadge(
                      label: isGranted ? 'Activado' : 'Desactivado',
                      color: isGranted ? koro.success : koro.mutedForeground,
                      icon: isGranted ? Icons.check_circle_outline : Icons.block_outlined,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  ElevatedButton.icon(
                    onPressed: () => _openSettings(context, ref),
                    icon: const Icon(Icons.settings_outlined),
                    label: Text(
                      granted ? 'Abrir ajustes de notificaciones' : 'Activar acceso',
                    ),
                  ),
                  const SizedBox(height: AppSpacing.md),
                  Text(
                    'Esto abre los ajustes del sistema; buscá "KoroFin" en la '
                    'lista de apps con acceso a notificaciones y activalo.',
                    style: AppTextStyles.caption(koro.mutedForeground),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
