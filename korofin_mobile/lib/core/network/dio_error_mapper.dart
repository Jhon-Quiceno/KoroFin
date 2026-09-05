import 'package:dio/dio.dart';

import 'api_exception.dart';

/// Traduce un [DioException] a la [ApiException] tipada que consume la app.
///
/// Mantiene el mismo criterio que el `getApiErrorMessage` del cliente web de
/// FinSmart: sin respuesta del servidor → mensaje de "revisá la conexión";
/// con `ErrorResponse` del backend → se muestra su `message`; en cualquier
/// otro caso → un texto genérico por código.
ApiException mapDioException(DioException error) {
  final bool noResponse = error.response == null ||
      error.type == DioExceptionType.connectionTimeout ||
      error.type == DioExceptionType.sendTimeout ||
      error.type == DioExceptionType.receiveTimeout ||
      error.type == DioExceptionType.connectionError;

  if (noResponse) {
    return const ApiException(
      message: 'No hay conexión con el servidor. Verificá que el backend esté '
          'corriendo y que la app apunte a la IP correcta.',
      isNetworkError: true,
    );
  }

  final int? status = error.response?.statusCode;
  final dynamic data = error.response?.data;
  final String? serverMessage =
      data is Map<String, dynamic> ? data['message'] as String? : null;

  return ApiException(
    message: serverMessage ?? _fallbackMessageFor(status),
    statusCode: status,
  );
}

String _fallbackMessageFor(int? status) {
  switch (status) {
    case 400:
      return 'La solicitud tiene datos inválidos.';
    case 401:
    case 403:
      return 'Tu sesión expiró. Volvé a iniciar sesión.';
    case 404:
      return 'No se encontró el recurso solicitado.';
    case 409:
      return 'El recurso ya existe o está en conflicto con otro.';
    case 422:
      return 'No se pudo procesar el contenido enviado.';
    case 429:
      return 'Demasiadas solicitudes. Esperá un momento e intentá de nuevo.';
    case 503:
      return 'El servicio no está disponible en este momento.';
    default:
      return 'Ocurrió un error inesperado. Intentá de nuevo.';
  }
}
