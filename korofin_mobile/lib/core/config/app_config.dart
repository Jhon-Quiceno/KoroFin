/// Configuración de entorno resuelta en tiempo de compilación.
///
/// La URL base de la API se pasa con `--dart-define=API_BASE_URL=...` para no
/// hornear una URL de producción en el binario. En el emulador de Android,
/// `10.0.2.2` es la máquina anfitriona; en un dispositivo físico hay que usar
/// la IP LAN de la PC (nunca `localhost`).
/// flutter run --dart-define=API_BASE_URL=http://192.168.1.11:8080
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  /// Tope de espera de una request normal, con el backend ya despierto. Sin
  /// esto, con el backend caído la app se queda colgada indefinidamente.
  ///
  /// Medido contra Render el 2026-11-04: con el servicio despierto,
  /// `/actuator/health` responde en 3,6 s y `POST /api/users/login` en 4,9 s.
  /// 30 s deja margen de sobra para una red móvil mala sin dejar al usuario
  /// mirando un spinner eterno cuando algo sí está roto.
  ///
  /// El caso del backend dormido NO se cubre acá — ver [coldStartTimeout].
  static const Duration requestTimeout = Duration(seconds: 30);

  /// Tope de espera del sondeo que despierta el backend dormido.
  ///
  /// En el plan Free de Render el servicio se suspende tras 15 min sin tráfico,
  /// y arrancarlo de nuevo tarda **mucho** más de lo que parecía: la medición
  /// real del log de arranque es
  /// `Started KorofinBackendApplication in 186.602 seconds`, porque el plan
  /// Free da ~0,1 CPU y la JVM queda asfixiada (las métricas muestran el CPU
  /// topado en 0,15 durante los tres minutos completos).
  ///
  /// Por eso el valor anterior de 75 s para todas las requests no servía: se
  /// rendía a menos de la mitad del tiempo que el backend necesita. 240 s
  /// cubre los ~200 s medidos con margen.
  ///
  /// Esto es un **colchón**, no la solución: el keep-alive externo (ver
  /// `docs/backend-plan.md`, sección de keep-alive) es lo que evita que el
  /// backend se duerma. Este timeout solo existe para cuando ese pinger falla.
  static const Duration coldStartTimeout = Duration(seconds: 240);

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
