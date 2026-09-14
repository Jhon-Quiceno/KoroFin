import 'package:path/path.dart' show join;
import 'package:sqflite/sqflite.dart';

import '../../models/movement.dart';
import '../../models/queued_movement.dart';

/// Persistencia local de la cola offline de altas de gasto/ingreso (alcance
/// acotado: solo altas, ver `OfflineQueueRepository`).
///
/// Cada fila lleva el `userId` de quien la encoló: sin este scoping, un
/// logout con movimientos todavía sin sincronizar seguido del login de otro
/// usuario en el mismo dispositivo terminaría sincronizando los movimientos
/// del primero contra la cuenta del segundo — una fuga de datos entre
/// cuentas inaceptable en una app de finanzas. [pendingFor] siempre filtra
/// por `userId`; nunca se opera sobre la cola completa entre usuarios.
///
/// Es una interfaz para poder inyectar una implementación en memoria en los
/// tests, igual que `LockStore`.
abstract interface class OfflineQueueStore {
  /// Encola un movimiento y devuelve el id local de la fila creada.
  Future<int> enqueue({
    required int userId,
    required MovementType type,
    required MovementDraft draft,
    required DateTime createdAt,
  });

  /// Movimientos pendientes de [userId], en orden FIFO (el más viejo primero).
  Future<List<QueuedMovement>> pendingFor(int userId);

  Future<void> remove(int id);

  /// Suma un intento fallido a la entrada, sin descartarla.
  Future<void> incrementAttempt(int id);
}

/// Implementación real sobre `sqflite`: sobrevive a que maten la app, algo
/// que una cola en memoria no puede ofrecer.
class SqfliteOfflineQueueStore implements OfflineQueueStore {
  /// [path] es inyectable para que los tests puedan apuntar a un archivo
  /// aislado (o `inMemoryDatabasePath`) en vez del path real del dispositivo.
  SqfliteOfflineQueueStore({String? path}) : _path = path;

  final String? _path;
  Database? _db;

  static const String _table = 'pending_movements';

  Future<Database> _open() async {
    final Database? existing = _db;
    if (existing != null) return existing;

    final String dbPath =
        _path ?? join(await getDatabasesPath(), 'korofin_offline_queue.db');
    final Database db = await openDatabase(
      dbPath,
      version: 1,
      onCreate: (db, version) => db.execute(
        'CREATE TABLE $_table ('
        'id INTEGER PRIMARY KEY AUTOINCREMENT, '
        'user_id INTEGER NOT NULL, '
        'kind TEXT NOT NULL, '
        'amount REAL NOT NULL, '
        'date TEXT NOT NULL, '
        'description TEXT, '
        'category_id INTEGER, '
        'payment_method TEXT, '
        'created_at TEXT NOT NULL, '
        'attempt_count INTEGER NOT NULL DEFAULT 0'
        ')',
      ),
    );
    _db = db;
    return db;
  }

  @override
  Future<int> enqueue({
    required int userId,
    required MovementType type,
    required MovementDraft draft,
    required DateTime createdAt,
  }) async {
    final Database db = await _open();
    return db.insert(_table, <String, Object?>{
      'user_id': userId,
      'kind': type.name,
      'amount': draft.amount,
      'date': MovementDraft.isoDate(draft.date),
      'description': draft.description,
      'category_id': draft.categoryId,
      'payment_method': draft.paymentMethod?.wire,
      'created_at': createdAt.toIso8601String(),
      'attempt_count': 0,
    });
  }

  @override
  Future<List<QueuedMovement>> pendingFor(int userId) async {
    final Database db = await _open();
    final List<Map<String, Object?>> rows = await db.query(
      _table,
      where: 'user_id = ?',
      whereArgs: <Object?>[userId],
      orderBy: 'id ASC',
    );
    return rows.map(_fromRow).toList(growable: false);
  }

  @override
  Future<void> remove(int id) async {
    final Database db = await _open();
    await db.delete(_table, where: 'id = ?', whereArgs: <Object?>[id]);
  }

  @override
  Future<void> incrementAttempt(int id) async {
    final Database db = await _open();
    await db.rawUpdate(
      'UPDATE $_table SET attempt_count = attempt_count + 1 WHERE id = ?',
      <Object?>[id],
    );
  }

  QueuedMovement _fromRow(Map<String, Object?> row) {
    final String? paymentMethodWire = row['payment_method'] as String?;
    return QueuedMovement(
      id: row['id']! as int,
      userId: row['user_id']! as int,
      type: MovementType.values.byName(row['kind']! as String),
      draft: MovementDraft(
        amount: (row['amount']! as num).toDouble(),
        date: DateTime.parse(row['date']! as String),
        description: row['description'] as String?,
        categoryId: row['category_id'] as int?,
        // `PaymentMethod.fromWire(null)` cae a CASH por defecto (pensado para
        // el formulario, que siempre necesita un valor seleccionado); acá un
        // ingreso sin método de pago debe seguir siendo `null`, no CASH.
        paymentMethod:
            paymentMethodWire == null ? null : PaymentMethod.fromWire(paymentMethodWire),
      ),
      createdAt: DateTime.parse(row['created_at']! as String),
      attemptCount: row['attempt_count']! as int,
    );
  }
}

/// Implementación en memoria para tests y previews.
class InMemoryOfflineQueueStore implements OfflineQueueStore {
  final List<QueuedMovement> _rows = <QueuedMovement>[];
  int _nextId = 1;

  @override
  Future<int> enqueue({
    required int userId,
    required MovementType type,
    required MovementDraft draft,
    required DateTime createdAt,
  }) async {
    final int id = _nextId++;
    _rows.add(QueuedMovement(
      id: id,
      userId: userId,
      type: type,
      draft: draft,
      createdAt: createdAt,
    ));
    return id;
  }

  @override
  Future<List<QueuedMovement>> pendingFor(int userId) async =>
      // Ya quedan en orden FIFO: se insertan en orden y nunca se reordenan.
      _rows.where((row) => row.userId == userId).toList(growable: false);

  @override
  Future<void> remove(int id) async {
    _rows.removeWhere((row) => row.id == id);
  }

  @override
  Future<void> incrementAttempt(int id) async {
    final int index = _rows.indexWhere((row) => row.id == id);
    if (index == -1) return;
    _rows[index] = _rows[index].withAttempt(_rows[index].attemptCount + 1);
  }
}
