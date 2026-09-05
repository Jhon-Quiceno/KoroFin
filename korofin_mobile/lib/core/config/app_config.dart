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

  /// Tope de espera de una request. Sin esto, con el backend caído la app se
  /// queda colgada indefinidamente.
  static const Duration requestTimeout = Duration(seconds: 15);
}
