// Smoke test: sin sesión guardada, la app arranca (tras el splash) en el login.

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/widgets/brand/koro_mark.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:korofin_mobile/core/providers.dart';
import 'package:korofin_mobile/core/storage/session_store.dart';
import 'package:korofin_mobile/main.dart';

void main() {
  testWidgets('sin sesión, KoroFin arranca en el login', (tester) async {
    await initializeDateFormatting('es_CO');

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[
          sessionStoreProvider.overrideWithValue(InMemorySessionStore()),
        ],
        child: const KoroFinApp(),
      ),
    );

    // Splash → bootstrap resuelve "sin sesión" → router monta el login.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pumpAndSettle();

    // El logo junto al nombre y la marca de agua de fondo.
    expect(find.byType(KoroMark), findsNWidgets(2));
    expect(find.text('KoroFin'), findsOneWidget);
    expect(find.text('Tu plata,\nen partes claras.'), findsOneWidget);
    expect(find.text('Ingresar'), findsOneWidget);
  });
}
