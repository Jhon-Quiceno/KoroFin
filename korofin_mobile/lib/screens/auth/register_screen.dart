import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Screen 2 — Registro: nombre, email, password y checkbox de términos.
class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  bool _acceptedTerms = false;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      appBar: AppBar(title: const Text('Crear cuenta')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.xxl),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Crea tu cuenta', style: Theme.of(context).textTheme.displayLarge),
              const SizedBox(height: AppSpacing.xs),
              Text('Empieza a controlar tus finanzas con KoroFin', style: Theme.of(context).textTheme.bodyLarge),
              const SizedBox(height: AppSpacing.xxl),
              const TextField(decoration: InputDecoration(labelText: 'Nombre completo', hintText: 'Valentina Ramírez')),
              const SizedBox(height: AppSpacing.md),
              const TextField(decoration: InputDecoration(labelText: 'Correo electrónico', hintText: 'tu@correo.com')),
              const SizedBox(height: AppSpacing.md),
              const TextField(obscureText: true, decoration: InputDecoration(labelText: 'Contraseña')),
              const SizedBox(height: AppSpacing.lg),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Checkbox(value: _acceptedTerms, onChanged: (v) => setState(() => _acceptedTerms = v ?? false)),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(top: 12),
                      child: Text.rich(
                        TextSpan(
                          style: Theme.of(context).textTheme.bodyMedium,
                          children: [
                            const TextSpan(text: 'Acepto los '),
                            TextSpan(text: 'términos y condiciones', style: TextStyle(color: koro.accent, fontWeight: FontWeight.w600)),
                            const TextSpan(text: ' y la política de privacidad.'),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              ElevatedButton(
                onPressed: _acceptedTerms ? () => context.go('/home') : null,
                child: const Text('Crear cuenta'),
              ),
              const SizedBox(height: AppSpacing.xl),
              Center(
                child: Wrap(
                  alignment: WrapAlignment.center,
                  children: [
                    Text('¿Ya tienes cuenta? ', style: Theme.of(context).textTheme.bodyMedium),
                    GestureDetector(
                      onTap: () => context.pop(),
                      child: Text('Inicia sesión', style: TextStyle(color: koro.accent, fontWeight: FontWeight.w600)),
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
