// Basic smoke test: the app boots into the Dashboard (Inicio) tab.

import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:korofin_mobile/main.dart';

void main() {
  testWidgets('KoroFin boots on the Dashboard', (WidgetTester tester) async {
    await initializeDateFormatting('es_CO');
    await tester.pumpWidget(const KoroFinApp());
    await tester.pumpAndSettle();

    expect(find.text('Buenos días, Valentina'), findsOneWidget);
    expect(find.text('Inicio'), findsOneWidget);
  });
}
