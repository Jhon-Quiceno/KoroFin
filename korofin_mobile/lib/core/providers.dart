import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'auth/biometric_authenticator.dart';
import 'network/api_client.dart';
import 'storage/lock_store.dart';
import 'storage/session_store.dart';

/// Sostiene el access token en memoria durante la vida del proceso. La capa de
/// sesión (Fase 1) lo escribe tras login/refresh y lo limpia en logout.
final accessTokenProvider = StateProvider<String?>((ref) => null);

/// Almacenamiento seguro del refresh token. Se sobreescribe en tests con
/// [InMemorySessionStore].
final sessionStoreProvider = Provider<SessionStore>(
  (ref) => SecureSessionStore(),
);

/// Almacenamiento seguro del bloqueo de la app (habilitado + PIN hasheado).
/// Se sobreescribe en tests con [InMemoryLockStore].
final lockStoreProvider = Provider<LockStore>(
  (ref) => SecureLockStore(),
);

/// Autenticación biométrica del dispositivo. Se sobreescribe en tests con un
/// fake que no depende del plugin nativo.
final biometricAuthenticatorProvider = Provider<BiometricAuthenticator>(
  (ref) => DeviceBiometricAuthenticator(),
);

/// Cliente HTTP único de la app. Lee el access token del [accessTokenProvider]
/// en cada request; el cableado de refresh se agrega en la Fase 1.
final apiClientProvider = Provider<ApiClient>((ref) {
  final ApiClient client = ApiClient(
    readAccessToken: () => ref.read(accessTokenProvider),
  );
  ref.onDispose(client.close);
  return client;
});
