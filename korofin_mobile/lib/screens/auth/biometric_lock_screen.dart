import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/lock/lock_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Screen 18 — Bloqueo biométrico: lock screen con Face ID/huella y un
/// fallback de PIN de 4 dígitos.
///
/// No navega directamente: el `redirect` de `app_router.dart` observa
/// [AppLockState] y saca a la app de esta pantalla en cuanto el desbloqueo
/// (PIN o biometría) tiene éxito, devolviendo al destino original.
class BiometricLockScreen extends ConsumerStatefulWidget {
  const BiometricLockScreen({super.key});

  @override
  ConsumerState<BiometricLockScreen> createState() =>
      _BiometricLockScreenState();
}

class _BiometricLockScreenState extends ConsumerState<BiometricLockScreen>
    with SingleTickerProviderStateMixin {
  bool _usePin = false;
  bool _checkingBiometrics = true;
  bool _submitting = false;
  String _pin = '';
  String? _error;

  /// Tiempo restante del lockout de PIN, o `null` si se puede intentar. Se
  /// refresca solo con un timer de 1s mientras esté vigente.
  Duration? _pinLockout;
  Timer? _lockoutTicker;

  late final AnimationController _shakeController = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 400),
  );
  late final Animation<double> _shake = TweenSequence<double>([
    TweenSequenceItem(tween: Tween(begin: 0.0, end: -10.0), weight: 1),
    TweenSequenceItem(tween: Tween(begin: -10.0, end: 10.0), weight: 2),
    TweenSequenceItem(tween: Tween(begin: 10.0, end: -10.0), weight: 2),
    TweenSequenceItem(tween: Tween(begin: -10.0, end: 0.0), weight: 1),
  ]).animate(CurvedAnimation(parent: _shakeController, curve: Curves.easeOut));

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _tryBiometricAuto();
      _refreshPinLockout();
    });
  }

  @override
  void dispose() {
    _shakeController.dispose();
    _lockoutTicker?.cancel();
    super.dispose();
  }

  /// Relee el lockout de PIN y se reprograma solo cada segundo mientras siga
  /// vigente, para que la cuenta regresiva se actualice sin interacción.
  Future<void> _refreshPinLockout() async {
    final Duration? remaining =
        await ref.read(lockControllerProvider.notifier).currentPinLockout();
    if (!mounted) return;
    setState(() => _pinLockout = remaining);
    _lockoutTicker?.cancel();
    if (remaining != null) {
      _lockoutTicker = Timer(const Duration(seconds: 1), _refreshPinLockout);
    }
  }

  String _formatLockout(Duration d) {
    final int totalSeconds = d.inSeconds < 0 ? 0 : d.inSeconds;
    final int minutes = totalSeconds ~/ 60;
    final int seconds = totalSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  /// Intento automático al entrar: si hay biometría, dispara el prompt nativo
  /// sin que el usuario tenga que tocar nada; si falla o no hay biometría,
  /// cae al PIN (el fallback obligatorio).
  Future<void> _tryBiometricAuto() async {
    final notifier = ref.read(lockControllerProvider.notifier);
    final bool available = await notifier.isBiometricAvailable();
    if (!mounted) return;
    setState(() {
      _usePin = !available;
      _checkingBiometrics = false;
    });
    if (available) {
      final bool unlocked = await notifier.unlockWithBiometrics();
      if (!mounted || unlocked) return;
      setState(() => _usePin = true);
    }
  }

  Future<void> _retryBiometric() async {
    final bool unlocked =
        await ref.read(lockControllerProvider.notifier).unlockWithBiometrics();
    if (!mounted || unlocked) return;
    setState(() => _usePin = true);
  }

  Future<void> _onDigit(String digit) async {
    if (_pin.length >= 4 || _submitting || _pinLockout != null) return;
    setState(() {
      _pin += digit;
      _error = null;
    });
    if (_pin.length != 4) return;

    setState(() => _submitting = true);
    final bool ok =
        await ref.read(lockControllerProvider.notifier).unlockWithPin(_pin);
    if (!mounted) return;
    if (!ok) {
      unawaited(_shakeController.forward(from: 0));
      setState(() {
        _pin = '';
        _error = 'PIN incorrecto. Intenta de nuevo.';
        _submitting = false;
      });
      // Un intento fallido puede haber disparado (o extendido) el lockout.
      await _refreshPinLockout();
    }
    // Si `ok` es true, el redirect del router saca de esta pantalla solo.
  }

  void _onBackspace() {
    if (_submitting || _pinLockout != null) return;
    setState(() {
      _pin = _pin.isEmpty ? '' : _pin.substring(0, _pin.length - 1);
      _error = null;
    });
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
                decoration: BoxDecoration(color: koro.accent.withValues(alpha: 0.15), shape: BoxShape.circle),
                child: Icon(_usePin ? Icons.pin_outlined : Icons.face_retouching_natural, size: 44, color: koro.accent),
              ),
              const SizedBox(height: AppSpacing.xxl),
              const Text('Toca para desbloquear KoroFin', style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700), textAlign: TextAlign.center),
              const SizedBox(height: AppSpacing.sm),
              Text(
                _usePin ? 'Ingresa tu PIN de 4 dígitos' : 'Usa Face ID o tu huella para continuar',
                style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 14),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: AppSpacing.xxxl),
              if (!_usePin)
                ElevatedButton.icon(
                  onPressed: _checkingBiometrics ? null : _retryBiometric,
                  icon: const Icon(Icons.fingerprint),
                  label: const Text('Desbloquear'),
                )
              else ...[
                AnimatedBuilder(
                  animation: _shake,
                  builder: (context, child) =>
                      Transform.translate(offset: Offset(_shake.value, 0), child: child),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(4, (i) {
                      final bool filled = i < _pin.length;
                      return Container(
                        width: 14,
                        height: 14,
                        margin: const EdgeInsets.symmetric(horizontal: 8),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: filled ? koro.accent : Colors.transparent,
                          border: Border.all(color: filled ? koro.accent : const Color(0xFF334155)),
                        ),
                      );
                    }),
                  ),
                ),
                if (_pinLockout != null) ...[
                  const SizedBox(height: AppSpacing.md),
                  Text(
                    'Demasiados intentos. Intenta de nuevo en ${_formatLockout(_pinLockout!)}.',
                    style: const TextStyle(color: Color(0xFFEF4444), fontSize: 13),
                    textAlign: TextAlign.center,
                  ),
                ] else if (_error != null) ...[
                  const SizedBox(height: AppSpacing.md),
                  Text(_error!, style: const TextStyle(color: Color(0xFFEF4444), fontSize: 13)),
                ],
                const SizedBox(height: AppSpacing.xxl),
                Opacity(
                  opacity: _pinLockout != null ? 0.4 : 1,
                  child: IgnorePointer(
                    ignoring: _pinLockout != null,
                    child: _NumericKeypad(onDigit: _onDigit, onBackspace: _onBackspace),
                  ),
                ),
              ],
              const Spacer(),
              TextButton(
                onPressed: _checkingBiometrics
                    ? null
                    : () => setState(() {
                          _usePin = !_usePin;
                          _pin = '';
                          _error = null;
                        }),
                child: Text(_usePin ? 'Usar biometría' : 'Usar PIN', style: const TextStyle(color: Color(0xFF94A3B8))),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NumericKeypad extends StatelessWidget {
  const _NumericKeypad({required this.onDigit, required this.onBackspace});

  final ValueChanged<String> onDigit;
  final VoidCallback onBackspace;

  @override
  Widget build(BuildContext context) {
    const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '<'];
    return SizedBox(
      width: 240,
      child: GridView.count(
        crossAxisCount: 3,
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        mainAxisSpacing: AppSpacing.sm,
        crossAxisSpacing: AppSpacing.sm,
        children: [
          for (final key in keys)
            if (key.isEmpty)
              const SizedBox.shrink()
            else
              InkWell(
                borderRadius: BorderRadius.circular(999),
                onTap: () => key == '<' ? onBackspace() : onDigit(key),
                child: Center(
                  child: key == '<'
                      ? const Icon(Icons.backspace_outlined, color: Colors.white, size: 20)
                      : Text(key, style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w600)),
                ),
              ),
        ],
      ),
    );
  }
}
