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
///
/// En Android 13+ las apps instaladas por APK (fuera de Play Store) tienen ese
/// interruptor bloqueado como "ajuste restringido". Ningún código puede
/// saltarlo: el usuario debe habilitar "Permitir ajustes restringidos" desde
/// "Información de la app". Por eso, mientras no esté concedido, se muestran
/// esos pasos y un acceso directo a esa pantalla.
class NequiCapturePermissionScreen extends ConsumerStatefulWidget {
  const NequiCapturePermissionScreen({super.key});

  @override
  ConsumerState<NequiCapturePermissionScreen> createState() =>
      _NequiCapturePermissionScreenState();
}

class _NequiCapturePermissionScreenState
    extends ConsumerState<NequiCapturePermissionScreen> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// Al volver de Ajustes del sistema se vuelve a leer el estado, para que el
  /// badge refleje el cambio sin tener que tirar para refrescar.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.invalidate(nequiPermissionStatusProvider);
    }
  }

  Future<void> _openListenerSettings() async {
    await ref.read(nequiCaptureControllerProvider.notifier).requestPermission();
    ref.invalidate(nequiPermissionStatusProvider);
  }

  Future<void> _openAppDetails() =>
      ref.read(nequiCaptureControllerProvider.notifier).openAppDetails();

  @override
  Widget build(BuildContext context) {
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
                    onPressed: _openListenerSettings,
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
            if (!granted) ...[
              const SizedBox(height: AppSpacing.lg),
              _RestrictedSettingsHelp(onOpenAppDetails: _openAppDetails),
            ],
          ],
        ),
      ),
    );
  }
}

/// Pasos para desbloquear el "ajuste restringido" de Android 13+.
class _RestrictedSettingsHelp extends StatelessWidget {
  const _RestrictedSettingsHelp({required this.onOpenAppDetails});

  final VoidCallback onOpenAppDetails;

  static const List<String> _steps = [
    'Tocá "Abrir información de la app".',
    'Arriba a la derecha, tocá el menú ⋮ y elegí "Permitir ajustes restringidos" '
        '(confirmá con tu PIN o huella).',
    'Volvé acá y tocá "Activar acceso": el interruptor de KoroFin ya se podrá activar.',
  ];

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('¿El interruptor aparece bloqueado?', style: AppTextStyles.titleMedium(koro.foreground)),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'En Android 13 o superior, si instalaste KoroFin desde un APK, el '
              'sistema marca este acceso como "Ajuste restringido". Para '
              'desbloquearlo:',
              style: AppTextStyles.bodyMedium(koro.mutedForeground),
            ),
            const SizedBox(height: AppSpacing.md),
            for (int i = 0; i < _steps.length; i++)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: Text('${i + 1}. ${_steps[i]}', style: AppTextStyles.bodyMedium(koro.foreground)),
              ),
            const SizedBox(height: AppSpacing.sm),
            OutlinedButton.icon(
              onPressed: onOpenAppDetails,
              icon: const Icon(Icons.info_outline),
              label: const Text('Abrir información de la app'),
            ),
          ],
        ),
      ),
    );
  }
}
