import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

/// Adaptador de HTTP falso para tests: registra cada request y responde con un
/// estado/cuerpo fijos, o lanza un [DioException] de transporte.
///
/// Con [queue] se pueden encolar varias respuestas distintas (una por llamada),
/// útil para probar el reintento tras un refresh.
class CapturingAdapter implements HttpClientAdapter {
  CapturingAdapter({this.status = 200, this.body, this.throwType});

  /// Respuestas encoladas: `{ 'status': int, 'body': Object? }`. Si está vacío,
  /// se usan [status]/[body]/[throwType].
  final List<Map<String, dynamic>> queue = <Map<String, dynamic>>[];

  int status;
  Object? body;
  DioExceptionType? throwType;

  final List<RequestOptions> requests = <RequestOptions>[];
  int get calls => requests.length;
  RequestOptions get lastRequest => requests.last;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);

    if (queue.isNotEmpty) {
      final Map<String, dynamic> next = queue.removeAt(0);
      return ResponseBody.fromString(
        jsonEncode(next['body'] ?? <String, dynamic>{}),
        next['status'] as int? ?? 200,
        headers: _jsonHeaders,
      );
    }

    if (throwType != null) {
      throw DioException(requestOptions: options, type: throwType!);
    }
    return ResponseBody.fromString(
      jsonEncode(body ?? <String, dynamic>{}),
      status,
      headers: _jsonHeaders,
    );
  }

  @override
  void close({bool force = false}) {}
}

const Map<String, List<String>> _jsonHeaders = <String, List<String>>{
  Headers.contentTypeHeader: <String>[Headers.jsonContentType],
};
