import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/offline_queue/offline_queue_controller.dart';
import '../../state/offline_queue/offline_queue_state.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Aviso de movimientos creados sin conexión que todavía no se sincronizaron.
/// Se oculta por completo cuando no hay pendientes; mientras sincroniza
/// muestra un spinner en vez del botón manual.
class OfflineQueueBanner extends ConsumerWidget {
  const OfflineQueueBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final OfflineQueueState state = ref.watch(offlineQueueControllerProvider);
    if (!state.hasPending) return const SizedBox.shrink();

    final koro = context.koroColors;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.md),
      child: Container(
        padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.md, vertical: AppSpacing.sm),
        decoration: BoxDecoration(
          color: koro.warning.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(AppRadii.md),
          border: Border.all(color: koro.warning.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            Icon(Icons.cloud_off_outlined, size: 18, color: koro.warning),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Text(
                state.syncing
                    ? 'Sincronizando ${state.pendingCount} movimiento(s)...'
                    : '${state.pendingCount} movimiento(s) sin sincronizar',
                style: TextStyle(
                  color: koro.warning,
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                ),
              ),
            ),
            if (state.syncing)
              const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            else
              TextButton(
                onPressed: () =>
                    ref.read(offlineQueueControllerProvider.notifier).syncNow(),
                child: const Text('Sincronizar'),
              ),
          ],
        ),
      ),
    );
  }
}
