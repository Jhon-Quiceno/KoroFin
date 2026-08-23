import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Screen 1 — Login: email + password, "Continuar con Google", link to
/// Registro and a biometric shortcut into the lock screen.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  bool _obscure = true;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.xxl),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: AppSpacing.xxl),
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(color: koro.accent, borderRadius: BorderRadius.circular(AppRadii.lg)),
                child: const Center(child: Text('K', style: TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w700))),
              ),
              const SizedBox(height: AppSpacing.xl),
              Text('Bienvenido de nuevo', style: Theme.of(context).textTheme.displayLarge),
              const SizedBox(height: AppSpacing.xs),
              Text('Ingresa a tu cuenta de KoroFin', style: Theme.of(context).textTheme.bodyLarge),
              const SizedBox(height: AppSpacing.xxl),
              const TextField(decoration: InputDecoration(labelText: 'Correo electrónico', hintText: 'tu@correo.com')),
              const SizedBox(height: AppSpacing.md),
              TextField(
                obscureText: _obscure,
                decoration: InputDecoration(
                  labelText: 'Contraseña',
                  suffixIcon: IconButton(
                    icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                    onPressed: () => setState(() => _obscure = !_obscure),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(onPressed: () {}, child: const Text('¿Olvidaste tu contraseña?')),
              ),
              const SizedBox(height: AppSpacing.md),
              ElevatedButton(
                onPressed: () => context.go('/home'),
                child: const Text('Continuar'),
              ),
              const SizedBox(height: AppSpacing.lg),
              Row(
                children: [
                  Expanded(child: Divider(color: koro.border)),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
                    child: Text('o', style: Theme.of(context).textTheme.bodyMedium),
                  ),
                  Expanded(child: Divider(color: koro.border)),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              OutlinedButton.icon(
                onPressed: () => context.go('/home'),
                icon: const Icon(Icons.g_mobiledata, size: 26),
                label: const Text('Continuar con Google'),
              ),
              const SizedBox(height: AppSpacing.md),
              OutlinedButton.icon(
                onPressed: () => context.push('/lock'),
                icon: const Icon(Icons.fingerprint),
                label: const Text('Ingresar con biometría'),
              ),
              const SizedBox(height: AppSpacing.xxl),
              Center(
                child: Wrap(
                  alignment: WrapAlignment.center,
                  children: [
                    Text('¿No tienes cuenta? ', style: Theme.of(context).textTheme.bodyMedium),
                    GestureDetector(
                      onTap: () => context.push('/register'),
                      child: Text('Regístrate', style: TextStyle(color: koro.accent, fontWeight: FontWeight.w600)),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
