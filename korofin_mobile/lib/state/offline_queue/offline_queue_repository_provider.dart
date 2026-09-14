import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/income_repository.dart';
import '../../data/repositories/offline_queue_repository.dart';

// Instancias propias de `ExpenseRepository`/`IncomeRepository` (en vez de
// reusar `expenseRepositoryProvider`/`incomeRepositoryProvider` de
// `movements_controller.dart`) a propósito: ese archivo necesita importar la
// cola offline para encolar en `add()`, así que importar a la inversa acá
// crearía un ciclo entre los dos features. Ambas instancias son wrappers sin
// estado propio sobre el mismo `ApiClient` singleton, así que duplicarlas no
// tiene costo real.
final _offlineExpenseRepositoryProvider = Provider<ExpenseRepository>(
  (ref) => ExpenseRepository(ref.read(apiClientProvider)),
);

final _offlineIncomeRepositoryProvider = Provider<IncomeRepository>(
  (ref) => IncomeRepository(ref.read(apiClientProvider)),
);

final offlineQueueRepositoryProvider = Provider<OfflineQueueRepository>(
  (ref) => OfflineQueueRepository(
    ref.read(offlineQueueStoreProvider),
    ref.read(_offlineExpenseRepositoryProvider),
    ref.read(_offlineIncomeRepositoryProvider),
  ),
);
