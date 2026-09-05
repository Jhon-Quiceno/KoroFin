import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:korofin_mobile/core/network/api_client.dart';
import 'package:korofin_mobile/core/network/api_exception.dart';
import 'package:korofin_mobile/data/repositories/statement_repository.dart';
import 'package:korofin_mobile/models/movement.dart';
import 'package:korofin_mobile/models/statement.dart';

import '../support/capturing_adapter.dart';

StatementRepository _repo(CapturingAdapter a) => StatementRepository(
    ApiClient(readAccessToken: () => 't', dio: Dio()..httpClientAdapter = a));

Map<String, dynamic> _row({
  bool dup = false,
  String type = 'EXPENSE',
  String date = '2026-09-01',
}) =>
    <String, dynamic>{
      'date': date,
      'description': 'Compra X',
      'amount': 12345,
      'movementType': type,
      'isDuplicate': dup,
      'suggestedCategoryId': 4,
      'suggestedCategoryName': 'Compras',
    };

void main() {
  test('StatementRow: los duplicados arrancan sin marcar', () {
    final dup = StatementRow.fromJson(_row(dup: true));
    final ok = StatementRow.fromJson(_row(dup: false));
    expect(dup.selected, isFalse);
    expect(ok.selected, isTrue);
  });

  test('preview hace POST multipart a /preview y parsea rows/totales', () async {
    final a = CapturingAdapter(body: <String, dynamic>{
      'rows': <dynamic>[_row(), _row(dup: true, type: 'INCOME')],
      'totalRows': 2,
      'duplicateRows': 1,
    });

    final preview = await _repo(a).preview(
      bytes: Uint8List.fromList(<int>[1, 2, 3]),
      filename: 'extracto.csv',
    );

    expect(a.lastRequest.path, '/api/statement-imports/preview');
    expect(
        (a.lastRequest.headers['content-type'] as String?)
                ?.contains('multipart/form-data') ??
            false,
        isTrue);
    expect(preview.rows, hasLength(2));
    expect(preview.duplicateRows, 1);
    expect(preview.rows[1].type, MovementType.income);
  });

  test('confirm manda solo las filas elegidas con la forma de ImportConfirmRow',
      () async {
    final a = CapturingAdapter(body: <String, dynamic>{'createdCount': 1});
    final rows = [
      StatementRow.fromJson(_row())..selected = true,
      StatementRow.fromJson(_row())..selected = true,
    ];

    final created = await _repo(a).confirm(rows);

    expect(a.lastRequest.path, '/api/statement-imports/confirm');
    final body = a.lastRequest.data as Map<String, dynamic>;
    final sent = body['rows'] as List<dynamic>;
    expect(sent, hasLength(2));
    final first = sent.first as Map<String, dynamic>;
    expect(first['movementType'], 'EXPENSE');
    expect(first['date'], '2026-09-01');
    expect(first['categoryId'], 4);
    expect(created, 1);
  });

  test('un 422 (PDF con contraseña) se propaga como ApiException', () async {
    final a = CapturingAdapter(
      status: 422,
      body: <String, dynamic>{'message': 'El PDF requiere una contraseña válida'},
    );
    expect(
      () => _repo(a).preview(
          bytes: Uint8List.fromList(<int>[1]), filename: 'x.pdf'),
      throwsA(isA<ApiException>().having((e) => e.statusCode, 's', 422)),
    );
  });
}
