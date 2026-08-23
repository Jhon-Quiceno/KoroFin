import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Screen 18 — Bloqueo biométrico: lock screen with Face ID/huella icon and
/// a "Usar PIN" fallback with a numeric keypad.
class BiometricLockScreen extends StatefulWidget {
  const BiometricLockScreen({super.key});

  @override
  State<BiometricLockScreen> createState() => _BiometricLockScreenState();
}

class _BiometricLockScreenState extends State<BiometricLockScreen> {
  bool _usePin = false;
  String _pin = '';

  void _onDigit(String digit) {
    if (_pin.length >= 4) return;
    setState(() => _pin += digit);
    if (_pin.length == 4) {
      Future.delayed(const Duration(milliseconds: 250), () {
        if (mounted) context.go('/home');
      });
    }
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
                  onPressed: () => context.go('/home'),
                  icon: const Icon(Icons.fingerprint),
                  label: const Text('Desbloquear'),
                )
              else ...[
                Row(
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
                const SizedBox(height: AppSpacing.xxl),
                _NumericKeypad(onDigit: _onDigit, onBackspace: () => setState(() => _pin = _pin.isEmpty ? '' : _pin.substring(0, _pin.length - 1))),
              ],
              const Spacer(),
              TextButton(
                onPressed: () => setState(() {
                  _usePin = !_usePin;
                  _pin = '';
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
