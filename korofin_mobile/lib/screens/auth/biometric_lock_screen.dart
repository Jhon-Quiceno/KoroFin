import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/auth/auth_controller.dart';
import '../../state/lock/lock_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Screen 18 — Bloqueo biométrico: lock screen con Face ID/huella. Sin PIN de
/// respaldo: si la biometría falla o no responde, la única salida es cerrar
/// sesión (vuelve al login con correo y contraseña).
///
/// No navega directamente: el `redirect` de `app_router.dart` observa
/// [AppLockState] y saca a la app de esta pantalla en cuanto el desbloqueo
/// biométrico tiene éxito, devolviendo al destino original.
class BiometricLockScreen extends ConsumerStatefulWidget {
  const BiometricLockScreen({super.key});

  @override
  ConsumerState<BiometricLockScreen> createState() =>
      _BiometricLockScreenState();
}

class _BiometricLockScreenState extends ConsumerState<BiometricLockScreen> {
  bool _checking = true;
  bool _authenticating = false;
  bool _loggingOut = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _tryBiometric());
  }

  /// Si el desbloqueo tiene éxito, el redirect del router saca de esta
  /// pantalla solo; si no, se queda acá mostrando el botón para reintentar.
  Future<void> _tryBiometric() async {
    setState(() => _checking = true);
    await ref.read(lockControllerProvider.notifier).unlockWithBiometrics();
    if (!mounted) return;
    setState(() {
      _checking = false;
      _authenticating = false;
    });
  }

  Future<void> _retry() async {
    setState(() => _authenticating = true);
    await _tryBiometric();
  }

  Future<void> _logout() async {
    setState(() => _loggingOut = true);
    await ref.read(authControllerProvider.notifier).logout();
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xxl),
          child: Column(
            children: [
              const Spacer(),
              Container(
                width: 96,
                height: 96,
                decoration: BoxDecoration(
                    color: koro.accent.withValues(alpha: 0.15),
                    shape: BoxShape.circle),
                child: Icon(Icons.fingerprint, size: 44, color: koro.accent),
              ),
              const SizedBox(height: AppSpacing.xxl),
              const Text('Toca para desbloquear KoroFin',
                  style: TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.w700),
                  textAlign: TextAlign.center),
              const SizedBox(height: AppSpacing.sm),
              const Text(
                'Usa Face ID o tu huella para continuar',
                style: TextStyle(color: Color(0xFF94A3B8), fontSize: 14),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: AppSpacing.xxxl),
              ElevatedButton.icon(
                onPressed: _checking || _authenticating ? null : _retry,
                icon: const Icon(Icons.fingerprint),
                label: const Text('Desbloquear'),
              ),
              const Spacer(),
              TextButton(
                onPressed: _loggingOut ? null : _logout,
                child: const Text('Cerrar sesión',
                    style: TextStyle(color: Color(0xFF94A3B8))),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
