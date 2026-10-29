/// Estado observable de la cola offline: cuántos movimientos hay pendientes
/// de sincronizar y si hay una sincronización en curso ahora mismo.
class OfflineQueueState {
  const OfflineQueueState({required this.pendingCount, required this.syncing});

  const OfflineQueueState.initial() : this(pendingCount: 0, syncing: false);

  final int pendingCount;
  final bool syncing;

  bool get hasPending => pendingCount > 0;

  OfflineQueueState copyWith({int? pendingCount, bool? syncing}) =>
      OfflineQueueState(
        pendingCount: pendingCount ?? this.pendingCount,
        syncing: syncing ?? this.syncing,
      );
}
