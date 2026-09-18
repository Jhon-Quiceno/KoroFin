/// Configuración de entorno resuelta en tiempo de compilación.
///
/// La URL base de la API se pasa con `--dart-define=API_BASE_URL=...` para no
/// hornear una URL de producción en el binario. En el emulador de Android,
/// `10.0.2.2` es la máquina anfitriona; en un dispositivo físico hay que usar
/// la IP LAN de la PC (nunca `localhost`).
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  /// Tope de espera de una request normal. Sin esto, con el backend caído la
  /// app se queda colgada indefinidamente.
  static const Duration requestTimeout = Duration(seconds: 15);

  /// Tope de espera para operaciones que pasan por un proveedor de IA (chat,
  /// escaneo de recibos, generación de insight, extracción de extractos): la
  /// primera llamada en frío ronda los 15-20 s y el backend reintenta en
  /// cascada entre proveedores.
  static const Duration aiRequestTimeout = Duration(seconds: 90);

  /// Hash SHA-256 (hex) del certificado TLS de producción, para activar
  /// certificate pinning en [ApiClient]. Vacío por defecto: sin este valor
  /// el pinning queda deshabilitado y Dio valida contra el almacén de CAs
  /// del SO como hasta ahora (el caso normal en dev/local, donde el backend
  /// ni siquiera tiene TLS real). No hornear acá el hash real de producción.
  ///
  /// Cómo calcular el valor real a partir del certificado del backend:
  ///   openssl x509 -in cert.pem -outform der | openssl dgst -sha256
  ///
  /// Cómo pasarlo al compilar: `--dart-define=PINNED_CERT_SHA256=<hash-hex>`
  static const String pinnedCertSha256 = String.fromEnvironment(
    'PINNED_CERT_SHA256',
    defaultValue: '',
  );
}
