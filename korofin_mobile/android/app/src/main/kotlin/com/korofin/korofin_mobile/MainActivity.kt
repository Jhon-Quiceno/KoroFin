package com.korofin.korofin_mobile

import io.flutter.embedding.android.FlutterFragmentActivity

/**
 * Hereda de FlutterFragmentActivity y no de FlutterActivity porque local_auth necesita un
 * FragmentActivity para mostrar el prompt de BiometricPrompt. Con FlutterActivity la
 * autenticacion biometrica falla en tiempo de ejecucion.
 */
class MainActivity : FlutterFragmentActivity()
