import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../models/movement.dart';
import '../auth/auth_controller.dart';
import '../auth/auth_state.dart';
import '../movements/movements_controller.dart';
import 'offline_queue_repository_provider.dart';
import 'offline_queue_state.dart';

final offlineQueueControllerProvider =
    NotifierProvider<OfflineQueueController, OfflineQueueState>(
        OfflineQueueController.new);

/// Orquesta la cola offline: sincroniza automáticamente al recuperar
/// conexión y al abrir la app (si hay sesión y pendientes), y expone el
/// conteo de pendientes para que la UI muestre feedback.
///
/// Se suscribe a los cambios de conectividad en vez de esperar a que el
/// usuario reintente a mano: una cola offline que solo sincroniza si te
/// acordás de volver a la app no cumple su propósito.
class OfflineQueueController extends Notifier<OfflineQueueState> {
  StreamSubscription<bool>? _connectivitySub;

  @override
  OfflineQueueState build() {
    _connectivitySub =
        ref.read(connectivityGatewayProvider).onConnectivityChanged.listen(
      (bool connected) {
        if (connected) unawaited(_syncCurrentUser());
      },
    );
    ref.onDispose(() => _connectivitySub?.cancel());

    // El id de usuario cambia en login (de null a un id) y en logout (de un
    // id a null); ambos casos ya quedan cubiertos por comparar contra el
    // anterior en vez de reaccionar a cualquier rebuild de `AuthState`.
    ref.listen<AuthState>(authControllerProvider, (previous, next) {
      if (next.status == AuthStatus.authenticated &&
          previous?.user?.id != next.user?.id) {
        unawaited(_syncCurrentUser());
      } else if (next.status != AuthStatus.authenticated) {
        state = const OfflineQueueState.initial();
      }
    });

    if (ref.read(authControllerProvider).status == AuthStatus.authenticated) {
      unawaited(_syncCurrentUser());
    }

    return const OfflineQueueState.initial();
  }

  /// Actualiza el conteo de pendientes sin forzar una sincronización — lo usa
  /// `MovementsController` justo después de encolar, para que la UI refleje
  /// el nuevo pendiente sin esperar al próximo evento de conectividad.
  Future<void> refreshPendingCount() async {
    final int? userId = ref.read(authControllerProvider).user?.id;
    if (userId == null) return;
    final int count =
        await ref.read(offlineQueueRepositoryProvider).pendingCount(userId);
    state = state.copyWith(pendingCount: count);
  }

  /// Sincronización manual (botón "Sincronizar" del aviso de pendientes).
  Future<void> syncNow() => _syncCurrentUser();

  Future<void> _syncCurrentUser() async {
    final int? userId = ref.read(authControllerProvider).user?.id;
    if (userId == null) return;
    if (!await ref.read(connectivityGatewayProvider).hasConnection()) return;

    state = state.copyWith(syncing: true);
    await ref.read(offlineQueueRepositoryProvider).sync(userId);
    final int count =
        await ref.read(offlineQueueRepositoryProvider).pendingCount(userId);
    state = state.copyWith(syncing: false, pendingCount: count);

    // Los listados de movimientos ya pintados pueden haber quedado
    // desactualizados si algo se sincronizó recién.
    ref.invalidate(movementsProvider(MovementType.expense));
    ref.invalidate(movementsProvider(MovementType.income));
  }
}
