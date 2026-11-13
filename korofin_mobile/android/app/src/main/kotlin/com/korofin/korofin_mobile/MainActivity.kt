package com.korofin.korofin_mobile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Hereda de FlutterFragmentActivity y no de FlutterActivity porque local_auth necesita un
 * FragmentActivity para mostrar el prompt de BiometricPrompt. Con FlutterActivity la
 * autenticacion biometrica falla en tiempo de ejecucion.
 */
class MainActivity : FlutterFragmentActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // Abre la pantalla "Informacion de la app". En Android 13+ las apps instaladas por APK
        // (fuera de Play Store) tienen el acceso a notificaciones bloqueado como "ajuste
        // restringido"; solo se desbloquea desde el menu de esa pantalla.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, APP_SETTINGS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "openAppDetails" -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        result.success(true)
                    }
                    // Pantalla "Acceso a notificaciones". Se abre con startActivity, sin
                    // esperar resultado: asi no hay una segunda respuesta al canal.
                    "openNotificationListenerSettings" -> {
                        startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private companion object {
        const val APP_SETTINGS_CHANNEL = "korofin/app_settings"
    }
}
