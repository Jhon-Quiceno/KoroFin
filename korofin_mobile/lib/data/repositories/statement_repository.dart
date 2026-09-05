import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../../core/network/api_client.dart';
import '../../models/statement.dart';

/// Acceso a `/api/statement-imports` (importación de extractos bancarios).
class StatementRepository {
  StatementRepository(this._client);

  final ApiClient _client;

  /// Previsualiza un extracto. `bytes` + `filename` (PDF/CSV/XLSX). `password`
  /// solo para PDFs protegidos.
  Future<StatementPreview> preview({
    required Uint8List bytes,
    required String filename,
    String? password,
  }) async {
    final FormData form = FormData.fromMap(<String, dynamic>{
      'file': MultipartFile.fromBytes(bytes, filename: filename),
      if (password != null && password.isNotEmpty) 'password': password,
    });
    final Response<dynamic> response =
        await _client.postForm('/api/statement-imports/preview', form);
    return StatementPreview.fromJson(response.data as Map<String, dynamic>);
  }

  /// Confirma las filas elegidas. Devuelve cuántos movimientos se crearon.
  Future<int> confirm(List<StatementRow> selectedRows) async {
    final response = await _client.post(
      '/api/statement-imports/confirm',
      body: <String, dynamic>{
        'rows': [for (final r in selectedRows) r.toConfirmJson()],
      },
    );
    return (response.data as Map<String, dynamic>)['createdCount'] as int? ?? 0;
  }
}
