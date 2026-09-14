// Corre la cola offline contra SQLite real (vía sqflite_common_ffi) en vez de
// un fake en memoria: la persistencia real -sobrevivir a que maten la app- es
// justamente lo que un fake en memoria no puede probar.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/storage/offline_queue_store.dart';
import 'package:korofin_mobile/models/movement.dart';
import 'package:korofin_mobile/models/queued_movement.dart';
import 'package:path/path.dart' show join;
import 'package:sqflite_common_ffi/sqflite_common_ffi.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  late String dbPath;

  setUp(() {
    dbPath = join(
      Directory.systemTemp.path,
      'korofin_offline_queue_test_${DateTime.now().microsecondsSinceEpoch}.db',
    );
  });

  final List<SqfliteOfflineQueueStore> openStores = <SqfliteOfflineQueueStore>[];

  // Cada store abierto se registra acá para cerrarlo al terminar el test: en
  // Windows el archivo de la base no se puede borrar mientras el proceso lo
  // tenga tomado, y varios tests abren dos instancias sobre el mismo archivo.
  SqfliteOfflineQueueStore openStore() {
    final SqfliteOfflineQueueStore store =
        SqfliteOfflineQueueStore(path: dbPath);
    openStores.add(store);
    return store;
  }

  tearDown(() async {
    for (final SqfliteOfflineQueueStore store in openStores) {
      await store.close();
    }
    openStores.clear();
    final File file = File(dbPath);
    if (file.existsSync()) file.deleteSync();
  });

  MovementDraft draftOf({int? categoryId, PaymentMethod? paymentMethod}) =>
      MovementDraft(
        amount: 15000,
        date: DateTime(2026, 9, 1),
        description: 'Almuerzo',
        categoryId: categoryId,
        paymentMethod: paymentMethod,
      );

  test('una entrada encolada sobrevive a recrear el store', () async {
    final store = openStore();
    await store.enqueue(
      userId: 1,
      type: MovementType.expense,
      draft: draftOf(categoryId: 3, paymentMethod: PaymentMethod.cash),
      createdAt: DateTime(2026, 9, 1, 10),
    );

    // Nueva instancia sobre el mismo archivo: simula que se mató el proceso y
    // se volvió a abrir la app.
    final reopened = openStore();
    final List<QueuedMovement> pending = await reopened.pendingFor(1);

    expect(pending, hasLength(1));
    expect(pending.single.draft.amount, 15000);
    expect(pending.single.draft.categoryId, 3);
    expect(pending.single.draft.paymentMethod, PaymentMethod.cash);
    expect(pending.single.userId, 1);
  });

  test('el scoping por userId aísla la cola entre usuarios', () async {
    final store = openStore();
    await store.enqueue(
      userId: 1,
      type: MovementType.expense,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 1),
    );
    await store.enqueue(
      userId: 2,
      type: MovementType.income,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 1),
    );

    final List<QueuedMovement> forUser1 = await store.pendingFor(1);
    final List<QueuedMovement> forUser2 = await store.pendingFor(2);

    expect(forUser1, hasLength(1));
    expect(forUser1.single.userId, 1);
    expect(forUser2, hasLength(1));
    expect(forUser2.single.userId, 2);
    // Ninguno ve la entrada del otro.
    expect(forUser1.any((m) => m.userId == 2), isFalse);
    expect(forUser2.any((m) => m.userId == 1), isFalse);
  });

  test('un ingreso sin método de pago se reconstruye como null, no CASH',
      () async {
    final store = openStore();
    await store.enqueue(
      userId: 1,
      type: MovementType.income,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 1),
    );

    final QueuedMovement pending = (await store.pendingFor(1)).single;

    expect(pending.draft.paymentMethod, isNull);
  });

  test('pendingFor devuelve en orden FIFO (el más viejo primero)', () async {
    final store = openStore();
    final int firstId = await store.enqueue(
      userId: 1,
      type: MovementType.expense,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 1),
    );
    final int secondId = await store.enqueue(
      userId: 1,
      type: MovementType.expense,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 2),
    );

    final List<QueuedMovement> pending = await store.pendingFor(1);

    expect(pending.map((m) => m.id), <int>[firstId, secondId]);
  });

  test('remove saca la entrada de la cola', () async {
    final store = openStore();
    final int id = await store.enqueue(
      userId: 1,
      type: MovementType.expense,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 1),
    );

    await store.remove(id);

    expect(await store.pendingFor(1), isEmpty);
  });

  test('incrementAttempt suma un intento sin descartar la entrada', () async {
    final store = openStore();
    final int id = await store.enqueue(
      userId: 1,
      type: MovementType.expense,
      draft: draftOf(),
      createdAt: DateTime(2026, 9, 1),
    );

    await store.incrementAttempt(id);
    await store.incrementAttempt(id);

    final QueuedMovement pending = (await store.pendingFor(1)).single;
    expect(pending.attemptCount, 2);
  });
}
