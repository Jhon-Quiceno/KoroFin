import 'movement.dart';

/// Un movimiento (gasto o ingreso) creado sin conexión, pendiente de
/// sincronizar contra el backend.
///
/// Ver `OfflineQueueStore` para la persistencia y `OfflineQueueRepository`
/// para la lógica de sincronización.
class QueuedMovement {
  const QueuedMovement({
    required this.id,
    required this.userId,
    required this.type,
    required this.draft,
    required this.createdAt,
    this.attemptCount = 0,
  });

  /// Id local de la fila en la cola. No tiene relación con el id que asigne
  /// el backend una vez sincronizado.
  final int id;

  /// Dueño de la entrada. Ver el Javadoc de `OfflineQueueStore` sobre por qué
  /// este campo es central para evitar fugas de datos entre cuentas.
  final int userId;

  final MovementType type;
  final MovementDraft draft;
  final DateTime createdAt;

  /// Intentos de sincronización fallidos con una falla ambigua (ni de red ni
  /// permanente) desde que se encoló. Ver `_maxAmbiguousAttempts` en
  /// `OfflineQueueRepository`.
  final int attemptCount;

  QueuedMovement withAttempt(int count) => QueuedMovement(
        id: id,
        userId: userId,
        type: type,
        draft: draft,
        createdAt: createdAt,
        attemptCount: count,
      );
}
