import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../data/repositories/statement_repository.dart';

final statementRepositoryProvider = Provider<StatementRepository>(
  (ref) => StatementRepository(ref.read(apiClientProvider)),
);
