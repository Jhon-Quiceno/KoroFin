/// Error normalizado de una llamada a la API.
///
/// Traduce la forma `ErrorResponse` del backend (`{ status, message, ... }`) y
/// los fallos de transporte (timeout, conexión rechazada) a algo que la UI
/// puede mostrar sin conocer Dio.
class ApiException implements Exception {
  const ApiException({
    required this.message,
    this.statusCode,
    this.isNetworkError = false,
  });

  /// Mensaje listo para mostrar al usuario. Si el backend mandó un `message` en
  /// el cuerpo del error, es ese; si no, un texto genérico por código.
  final String message;

  /// Código HTTP de la respuesta, o `null` si nunca hubo respuesta.
  final int? statusCode;

  /// `true` cuando no hubo respuesta del servidor (sin red, backend caído, DNS).
  final bool isNetworkError;

  /// Un 401/403 que la capa de sesión puede intentar recuperar con un refresh.
  bool get isUnauthorized => statusCode == 401 || statusCode == 403;

  @override
  String toString() => 'ApiException($statusCode): $message';
}
