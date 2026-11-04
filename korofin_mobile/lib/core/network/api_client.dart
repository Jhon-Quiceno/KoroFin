import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';

import '../config/app_config.dart';
import 'api_exception.dart';
import 'certificate_pinning.dart';
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
///   toquen `DioException` directamente;
/// - si se compiló con `PINNED_CERT_SHA256` (ver [AppConfig]), valida el
///   certificado del backend contra ese hash además del almacén de CAs del
///   SO (certificate pinning opt-in, pensado para producción).
class ApiClient {
  // Un parámetro con nombre no puede ser privado en Dart, así que el campo se
  // asigna en la lista de inicialización en vez de con un formal.
  ApiClient({
    required TokenReader readAccessToken,
    Dio? dio,
    String? pinnedCertSha256,
  })  : _readAccessToken = readAccessToken, // ignore: prefer_initializing_formals
        _dio = dio ?? Dio() {
    _dio.options
      ..baseUrl = AppConfig.apiBaseUrl
      ..connectTimeout = AppConfig.requestTimeout
      ..receiveTimeout = AppConfig.requestTimeout
      ..sendTimeout = AppConfig.requestTimeout
      ..headers[Headers.contentTypeHeader] = Headers.jsonContentType;

    _applyCertificatePinning(pinnedCertSha256 ?? AppConfig.pinnedCertSha256);

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
  Future<bool>? _inFlightProbe;

  /// Cuándo falló el último sondeo al health, para no repetirlo en cadena.
  DateTime? _lastProbeFailure;

  /// Cuánto dura ese recuerdo. Corto a propósito: si el backend despierta
  /// justo después del fallo, la app no debe quedar convencida de que está
  /// caído más que unos segundos.
  static const Duration _probeFailureMemory = Duration(seconds: 10);

  /// Acceso directo a Dio para casos que no encajan en los verbos de abajo
  /// (por ejemplo `multipart/form-data` o descargas en streaming).
  Dio get dio => _dio;

  void close() => _dio.close(force: true);

  /// Opciones por request; hoy solo se usa para alargar el timeout de lectura
  /// en las operaciones que pasan por un proveedor de IA.
  Options? _opts(Duration? timeout) =>
      timeout == null ? null : Options(receiveTimeout: timeout);

  Future<Response<dynamic>> get(
    String path, {
    Map<String, dynamic>? query,
    Duration? timeout,
  }) =>
      _send(() => _dio.get<dynamic>(path,
          queryParameters: query, options: _opts(timeout)));

  Future<Response<dynamic>> post(
    String path, {
    Object? body,
    Duration? timeout,
  }) =>
      _send(() =>
          _dio.post<dynamic>(path, data: body, options: _opts(timeout)));

  /// Igual que [post] pero para `multipart/form-data` (Dio pone el header y el
  /// boundary a partir del [FormData]). Pasa por el mismo mapeo de errores.
  Future<Response<dynamic>> postForm(
    String path,
    FormData form, {
    Duration? timeout,
  }) =>
      _send(() =>
          _dio.post<dynamic>(path, data: form, options: _opts(timeout)));

  Future<Response<dynamic>> put(String path, {Object? body}) =>
      _send(() => _dio.put<dynamic>(path, data: body));

  Future<Response<dynamic>> patch(String path, {Object? body}) =>
      _send(() => _dio.patch<dynamic>(path, data: body));

  Future<Response<dynamic>> delete(String path, {Object? body}) =>
      _send(() => _dio.delete<dynamic>(path, data: body));

  /// Ruta pública del health del backend. Es la única que sirve para sondear
  /// si el servicio está arriba sin necesitar sesión (`permitAll` en
  /// `SecurityConfig`).
  static const String _healthPath = '/actuator/health';

  /// Métodos que se pueden repetir sin efecto colateral. Un `GET` perdido se
  /// vuelve a pedir sin riesgo; un `POST` no.
  static const Set<String> _idempotentMethods = <String>{'GET', 'HEAD'};

  /// Despierta el backend de forma proactiva, antes de la primera request real.
  ///
  /// Pensado para llamarse al arrancar la app: si el servicio estaba dormido,
  /// esto absorbe los ~200 s de arranque en un sondeo que la UI puede mostrar
  /// como "conectando" en vez de hacer fallar el bootstrap de sesión.
  ///
  /// Nunca lanza: si el backend no despierta, las requests siguientes fallarán
  /// con su propio [ApiException] y la UI decide qué mostrar.
  Future<void> warmUp() async {
    // Al arrancar la app el escenario ES el arranque en frío, así que se le da
    // el presupuesto completo.
    await _probeHealth(AppConfig.coldStartTimeout);
  }

  Future<Response<dynamic>> _send(
    Future<Response<dynamic>> Function() run,
  ) async {
    try {
      return await run();
    } on DioException catch (error) {
      if (!_looksLikeSleepingBackend(error)) {
        throw mapDioException(error);
      }

      // El backend pudo haberse dormido (plan Free de Render): confirmar con el
      // health antes de dar el fallo por real. El presupuesto del sondeo
      // depende de qué tan lejos llegó la request original — ver
      // [_probeBudgetFor].
      if (!await _probeHealth(_probeBudgetFor(error))) {
        throw mapDioException(error);
      }

      final RequestOptions original = error.requestOptions;
      if (!_idempotentMethods.contains(original.method.toUpperCase())) {
        if (_mayHaveReachedServer(error)) {
          // Un POST/PUT/PATCH/DELETE que falló en fase de envío o recepción
          // pudo haber llegado al backend. Repetirlo duplicaría el movimiento,
          // así que se le devuelve el control al usuario con la verdad.
          throw const ApiException(
            message: 'El servidor estaba despertando y ya está listo. '
                'Volvé a intentarlo.',
            isNetworkError: true,
            ambiguousAfterSend: true,
          );
        }
        // La conexión nunca se estableció, así que el pedido no salió: se
        // propaga como error de red común, sin marcarlo ambiguo. Esto importa
        // porque la cola offline usa `ambiguousAfterSend` para decidir entre
        // reintentar sola (`break`) y retener la entrada esperando una
        // confirmación manual del usuario — marcar ambiguo algo que nunca
        // salió dejaría un movimiento parado hasta que alguien lo toque.
        throw mapDioException(error);
      }

      // Un solo reintento, fuera de [_send]: si vuelve a expirar se propaga sin
      // abrir otro ciclo de despertar.
      try {
        return await _dio.request<dynamic>(
          original.path,
          data: original.data,
          queryParameters: original.queryParameters,
          // Se copian explícitamente los campos que `Options` NO hereda de la
          // request original: construir un `Options` nuevo con solo método y
          // headers los perdía, así que el reintento podía comportarse distinto
          // de la request que repite (por ejemplo volver a `json` un
          // `ResponseType.plain`, o caer al timeout global uno que traía el
          // suyo propio por parámetro).
          options: Options(
            method: original.method,
            headers: original.headers,
            responseType: original.responseType,
            contentType: original.contentType,
            receiveTimeout: original.receiveTimeout,
            sendTimeout: original.sendTimeout,
            listFormat: original.listFormat,
            extra: <String, dynamic>{...original.extra, 'wokenUp': true},
          ),
        );
      } on DioException catch (retryError) {
        throw mapDioException(retryError);
      }
    }
  }

  /// `true` si el fallo ocurrió con la conexión ya establecida, así que el
  /// pedido pudo haber llegado al backend. Mismo criterio que
  /// `mapDioException` en `dio_error_mapper.dart`: solo las fases de envío y
  /// recepción implican que el pedido salió.
  bool _mayHaveReachedServer(DioException error) =>
      error.type == DioExceptionType.receiveTimeout ||
      error.type == DioExceptionType.sendTimeout;

  /// `true` si el fallo es compatible con un backend dormido o inalcanzable:
  /// nunca llegó respuesta. Un 4xx/5xx del backend significa que está vivo y
  /// no debe disparar ningún sondeo.
  bool _looksLikeSleepingBackend(DioException error) {
    if (error.response != null) return false;
    if (error.requestOptions.extra['wokenUp'] == true) return false;
    if (error.requestOptions.path.endsWith(_healthPath)) return false;
    return error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.sendTimeout ||
        error.type == DioExceptionType.receiveTimeout ||
        error.type == DioExceptionType.connectionError;
  }

  /// Cuánto vale la pena esperar al sondeo, según hasta dónde llegó la request
  /// que falló. Es la diferencia entre un backend arrancando y una conexión
  /// mala, y está en la propia señal del error:
  ///
  /// - **Fase de envío o recepción:** la conexión TCP/TLS se estableció y el
  ///   servidor colgó sin contestar. Eso es exactamente la forma de Render
  ///   aceptando la conexión mientras levanta el contenedor, así que vale
  ///   esperar el arranque completo.
  /// - **Fallo al conectar:** nunca hubo conexión. No hay nadie arrancando del
  ///   otro lado — es el dispositivo sin red, un DNS que no resuelve o un host
  ///   inalcanzable. Esperar ahí los minutos del arranque en frío solo cuelga
  ///   al usuario sin ninguna chance de que la espera sirva.
  Duration _probeBudgetFor(DioException error) => _mayHaveReachedServer(error)
      ? AppConfig.coldStartTimeout
      : AppConfig.requestTimeout;

  /// Single-flight, igual que [_refreshOnce]: si una pantalla dispara varias
  /// requests a la vez contra un backend dormido, todas esperan el mismo
  /// sondeo en vez de abrir N sondeos que competirían por el único décimo de
  /// CPU que el contenedor necesita justamente para arrancar.
  ///
  /// Además recuerda el último fallo: mientras ese recuerdo esté fresco, no se
  /// vuelve a sondear. Sin eso, un backend que no despierta acumulaba dos
  /// esperas completas seguidas —la de [warmUp] y la del primer [_send]— con
  /// la app muda en el medio.
  Future<bool> _probeHealth(Duration budget) {
    if (_lastProbeFailure != null &&
        DateTime.now().difference(_lastProbeFailure!) < _probeFailureMemory) {
      return Future<bool>.value(false);
    }
    return _inFlightProbe ??= _probeHealthOnce(budget).whenComplete(() {
      _inFlightProbe = null;
    });
  }

  /// Pega al health con el presupuesto dado. `true` si el backend contestó.
  /// No lanza nunca.
  Future<bool> _probeHealthOnce(Duration budget) async {
    try {
      final Response<dynamic> response = await _dio.get<dynamic>(
        _healthPath,
        options: Options(
          receiveTimeout: budget,
          sendTimeout: budget,
          // `retried: true` deja fuera este sondeo del rescate por 401: el
          // health es público y un refresh acá no tendría sentido.
          extra: <String, dynamic>{'retried': true, 'wokenUp': true},
        ),
      );
      final int? status = response.statusCode;
      final bool up = status != null && status >= 200 && status < 300;
      _lastProbeFailure = up ? null : DateTime.now();
      return up;
    } on DioException {
      _lastProbeFailure = DateTime.now();
      return false;
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
      // Igual que en el reintento del wake-up: `Options` no hereda nada de la
      // request original, así que los campos que no se copian a mano se
      // pierden y el reintento se comporta distinto de lo que repite.
      final Options retryOptions = Options(
        method: error.requestOptions.method,
        headers: <String, dynamic>{
          ...error.requestOptions.headers,
          'Authorization': 'Bearer $newToken',
        },
        responseType: error.requestOptions.responseType,
        contentType: error.requestOptions.contentType,
        receiveTimeout: error.requestOptions.receiveTimeout,
        sendTimeout: error.requestOptions.sendTimeout,
        listFormat: error.requestOptions.listFormat,
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

  /// Certificate pinning opt-in: solo hace algo si [pinnedSha256Hex] viene con
  /// valor (por defecto, `AppConfig.pinnedCertSha256`, que es `''` en
  /// dev/local y en los tests que no lo pasan explícito). Vacío -> no se toca
  /// el `httpClientAdapter`: mismo comportamiento que antes de este cambio.
  ///
  /// `validateCertificate` es un hook propio de `IOHttpClientAdapter` (no de
  /// `dart:io`) que se evalúa sobre el certificado *hoja* de la respuesta ya
  /// recibida, sin importar si la cadena fue aceptada por el almacén de CAs
  /// del SO o no — por eso sirve como defensa incluso si un CA malicioso
  /// llegó a instalarse como confiable en el dispositivo (el escenario que
  /// justamente motiva el pinning). Si no coincide, Dio lanza
  /// `DioException.badCertificate` en vez de entregar la respuesta.
  void _applyCertificatePinning(String pinnedSha256Hex) {
    if (pinnedSha256Hex.isEmpty) return;

    _dio.httpClientAdapter = IOHttpClientAdapter(
      validateCertificate: (X509Certificate? certificate, String host, int port) =>
          certificate != null &&
          certificateMatchesPinnedHash(certificate, pinnedSha256Hex),
    );
  }
}
