import '../../core/network/api_exception.dart';
import '../../core/storage/offline_queue_store.dart';
import '../../models/movement.dart';
import '../../models/queued_movement.dart';
import 'expense_repository.dart';
import 'income_repository.dart';

/// Resultado de una corrida de sincronización.
class SyncResult {
  const SyncResult({required this.synced, required this.discarded});

  final int synced;
  final int discarded;

  bool get hadActivity => synced > 0 || discarded > 0;
}

/// Cola de altas de gasto/ingreso creadas sin conexión (alcance acotado: solo
/// altas, no ediciones ni borrados, ni otros dominios).
///
/// La sincronización distingue tres tipos de falla porque tratarlas todas
/// igual es exactamente la forma en que una cola offline termina en un loop
/// infinito o perdiendo datos:
///
/// - **Fallo de red** (`ApiException.isNetworkError`) o **de sesión**
///   (401/403 tras agotar el refresh transparente del `ApiClient`): ni esta
///   ni las siguientes entradas van a poder sincronizar ahora mismo. Se
///   detiene todo el drenado en ese punto, preservando el orden FIFO para el
///   próximo intento (reconexión o re-login).
/// - **Fallo permanente del servidor** (400/404/409/422): el backend ya
///   respondió que el contenido en sí es inválido (ej. la categoría elegida
///   offline ya no existe). Reintentar nunca lo va a arreglar, así que esa
///   entrada puntual se descarta y se sigue con el resto — la alternativa
///   (no descartarla nunca) dejaría toda la cola trabada detrás de un ítem
///   irrecuperable.
/// - **Fallo ambiguo** (5xx, o cualquier código no mapeado arriba): puede ser
///   transitorio, así que se conserva para el próximo intento, pero con un
///   tope de reintentos (`_maxAmbiguousAttempts`) para que una entrada que
///   nunca va a poder sincronizar no quede reintentándose para siempre.
class OfflineQueueRepository {
  OfflineQueueRepository(
    this._store,
    this._expenses,
    this._incomes, {
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final OfflineQueueStore _store;
  final ExpenseRepository _expenses;
  final IncomeRepository _incomes;
  final DateTime Function() _now;

  static const Set<int> _permanentClientErrors = <int>{400, 404, 409, 422};

  static const int _maxAmbiguousAttempts = 5;

  Future<void> enqueueExpense({
    required int userId,
    required MovementDraft draft,
  }) =>
      _store.enqueue(
        userId: userId,
        type: MovementType.expense,
        draft: draft,
        createdAt: _now(),
      );

  Future<void> enqueueIncome({
    required int userId,
    required MovementDraft draft,
  }) =>
      _store.enqueue(
        userId: userId,
        type: MovementType.income,
        draft: draft,
        createdAt: _now(),
      );

  Future<int> pendingCount(int userId) async =>
      (await _store.pendingFor(userId)).length;

  Future<List<QueuedMovement>> pending(int userId) => _store.pendingFor(userId);

  Future<SyncResult>? _inFlight;

  /// Sincroniza la cola de [userId] contra el backend, en orden FIFO.
  ///
  /// Nunca corre dos veces en paralelo: si ya hay una sincronización en
  /// curso (por ejemplo, se recuperó la red justo cuando el usuario abrió la
  /// app), la segunda llamada espera el mismo resultado en vez de reprocesar
  /// la cola — evita mandar el mismo movimiento dos veces al backend. Mismo
  /// mecanismo de single-flight que usa `ApiClient` para el refresh de
  /// sesión.
  Future<SyncResult> sync(int userId) {
    return _inFlight ??= _runSync(userId).whenComplete(() => _inFlight = null);
  }

  Future<SyncResult> _runSync(int userId) async {
    int synced = 0;
    int discarded = 0;

    final List<QueuedMovement> queued = await _store.pendingFor(userId);
    for (final QueuedMovement item in queued) {
      try {
        await _send(item);
        await _store.remove(item.id);
        synced++;
      } on ApiException catch (error) {
        if (error.isNetworkError || error.isUnauthorized) {
          break;
        }
        if (_isPermanentFailure(error)) {
          await _store.remove(item.id);
          discarded++;
          continue;
        }
        if (item.attemptCount + 1 >= _maxAmbiguousAttempts) {
          await _store.remove(item.id);
          discarded++;
        } else {
          await _store.incrementAttempt(item.id);
        }
      }
    }

    return SyncResult(synced: synced, discarded: discarded);
  }

  bool _isPermanentFailure(ApiException error) =>
      error.statusCode != null &&
      _permanentClientErrors.contains(error.statusCode);

  Future<void> _send(QueuedMovement item) => item.type == MovementType.expense
      ? _expenses.create(item.draft)
      : _incomes.create(item.draft);
}
