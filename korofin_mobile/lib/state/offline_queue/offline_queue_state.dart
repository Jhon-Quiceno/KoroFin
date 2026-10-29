/// Estado observable de la cola offline: cuántos movimientos hay pendientes
/// de sincronizar y si hay una sincronización en curso ahora mismo.
class OfflineQueueState {
  const OfflineQueueState({
    required this.pendingCount,
    required this.syncing,
    this.lastDiscardedCount = 0,
    this.heldForConfirmationCount = 0,
  });

  const OfflineQueueState.initial() : this(pendingCount: 0, syncing: false);

  final int pendingCount;
  final bool syncing;

  /// Descartados en la última sincronización: se perdieron, hay que
  /// recrearlos a mano. Ver `SyncResult.discarded`.
  final int lastDiscardedCount;

  /// A la espera de confirmación manual antes de reintentar (riesgo de
  /// duplicado). Ver `SyncResult.heldForConfirmation`.
  final int heldForConfirmationCount;

  bool get hasPending => pendingCount > 0;

  OfflineQueueState copyWith({
    int? pendingCount,
    bool? syncing,
    int? lastDiscardedCount,
    int? heldForConfirmationCount,
  }) =>
      OfflineQueueState(
        pendingCount: pendingCount ?? this.pendingCount,
        syncing: syncing ?? this.syncing,
        lastDiscardedCount: lastDiscardedCount ?? this.lastDiscardedCount,
        heldForConfirmationCount:
            heldForConfirmationCount ?? this.heldForConfirmationCount,
      );
}
