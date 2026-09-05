import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'api_exception.dart';
import 'dio_error_mapper.dart';

/// Devuelve el access token vigente (en memoria), o `null` si no hay sesión.
typedef TokenReader = String? Function();

/// Intenta renovar la sesión y devuelve el nuevo access token, o `null` si el
/// refresh falló y la sesión debe darse por terminada.
typedef TokenRefresher = Future<String?> Function();

/// Se invoca cuando el refresh falla de forma definitiva: la capa de sesión
/// debe limpiar el estado y mandar al usuario al login.
typedef SessionExpiredCallback = void Function();

/// Cliente HTTP central de la app. Toda llamada al backend pasa por acá.
///
/// Responsabilidades:
/// - fija `baseUrl`, timeouts y `Content-Type: application/json`;
/// - agrega `Authorization: Bearer <accessToken>` cuando hay sesión;
/// - ante un 401/403 en una ruta protegida, intenta **un** refresh (con
///   single-flight: varias requests que fallan a la vez comparten el mismo
///   intento) y reintenta la request original;
/// - traduce cualquier fallo a [ApiException] para que los repositorios no
///   toquen `DioException` directamente.
class ApiClient {
  // Un parámetro con nombre no puede ser privado en Dart, así que el campo se
  // asigna en la lista de inicialización en vez de con un formal.
  ApiClient({
    required TokenReader readAccessToken,
    Dio? dio,
  })  : _readAccessToken = readAccessToken, // ignore: prefer_initializing_formals
        _dio = dio ?? Dio() {
    _dio.options
      ..baseUrl = AppConfig.apiBaseUrl
      ..connectTimeout = AppConfig.requestTimeout
      ..receiveTimeout = AppConfig.requestTimeout
      ..sendTimeout = AppConfig.requestTimeout
      ..headers[Headers.contentTypeHeader] = Headers.jsonContentType;

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: _attachAuthHeader,
        onError: _recoverFromUnauthorized,
      ),
    );
  }

  final Dio _dio;
  final TokenReader _readAccessToken;

  /// Rutas de sesión que un 401 NO debe intentar recuperar con refresh: acá un
  /// 401 significa "credenciales inválidas", no "token vencido".
  static const Set<String> _sessionPaths = <String>{
    '/api/users/login',
    '/api/users/register',
    '/api/users/refresh',
    '/api/users/logout',
  };

  /// Lo cablea la capa de sesión (Fase 1). Mientras sea `null`, un 401 se
  /// propaga tal cual como [ApiException].
  TokenRefresher? onRefresh;
  SessionExpiredCallback? onSessionExpired;

  Future<String?>? _inFlightRefresh;

  /// Acceso directo a Dio para casos que no encajan en los verbos de abajo
  /// (por ejemplo `multipart/form-data` o descargas en streaming).
  Dio get dio => _dio;

  void close() => _dio.close(force: true);

  Future<Response<dynamic>> get(
    String path, {
    Map<String, dynamic>? query,
  }) =>
      _send(() => _dio.get<dynamic>(path, queryParameters: query));

  Future<Response<dynamic>> post(String path, {Object? body}) =>
      _send(() => _dio.post<dynamic>(path, data: body));

  /// Igual que [post] pero para `multipart/form-data` (Dio pone el header y el
  /// boundary a partir del [FormData]). Pasa por el mismo mapeo de errores.
  Future<Response<dynamic>> postForm(String path, FormData form) =>
      _send(() => _dio.post<dynamic>(path, data: form));

  Future<Response<dynamic>> put(String path, {Object? body}) =>
      _send(() => _dio.put<dynamic>(path, data: body));

  Future<Response<dynamic>> patch(String path, {Object? body}) =>
      _send(() => _dio.patch<dynamic>(path, data: body));

  Future<Response<dynamic>> delete(String path, {Object? body}) =>
      _send(() => _dio.delete<dynamic>(path, data: body));

  Future<Response<dynamic>> _send(
    Future<Response<dynamic>> Function() run,
  ) async {
    try {
      return await run();
    } on DioException catch (error) {
      throw mapDioException(error);
    }
  }

  void _attachAuthHeader(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) {
    // En un reintento post-refresh el header ya trae el token nuevo puesto a
    // mano; no se pisa con lo que devuelva el lector (que podría no haberse
    // actualizado todavía).
    if (options.extra['retried'] == true &&
        options.headers.containsKey('Authorization')) {
      handler.next(options);
      return;
    }

    final String? token = _readAccessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    } else {
      options.headers.remove('Authorization');
    }
    handler.next(options);
  }

  Future<void> _recoverFromUnauthorized(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    final int? status = error.response?.statusCode;
    final bool recoverable = (status == 401 || status == 403) &&
        onRefresh != null &&
        error.requestOptions.extra['retried'] != true &&
        !_isSessionPath(error.requestOptions.path);

    if (!recoverable) {
      handler.next(error);
      return;
    }

    final String? newToken = await _refreshOnce();
    if (newToken == null || newToken.isEmpty) {
      onSessionExpired?.call();
      handler.next(error);
      return;
    }

    try {
      final Options retryOptions = Options(
        method: error.requestOptions.method,
        headers: <String, dynamic>{
          ...error.requestOptions.headers,
          'Authorization': 'Bearer $newToken',
        },
        extra: <String, dynamic>{
          ...error.requestOptions.extra,
          'retried': true,
        },
      );
      final Response<dynamic> response = await _dio.request<dynamic>(
        error.requestOptions.path,
        data: error.requestOptions.data,
        queryParameters: error.requestOptions.queryParameters,
        options: retryOptions,
      );
      handler.resolve(response);
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }

  /// Single-flight: si ya hay un refresh en curso, todas las requests que
  /// fallaron esperan ese mismo `Future` en vez de disparar refreshes en
  /// paralelo (que rotarían el refresh token varias veces y romperían la sesión).
  Future<String?> _refreshOnce() {
    return _inFlightRefresh ??= onRefresh!().whenComplete(() {
      _inFlightRefresh = null;
    });
  }

  bool _isSessionPath(String path) =>
      _sessionPaths.any((sessionPath) => path.endsWith(sessionPath) || path == sessionPath);
}
