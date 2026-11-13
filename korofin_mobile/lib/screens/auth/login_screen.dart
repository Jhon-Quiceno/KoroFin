import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../state/auth/auth_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/brand/auth_brand_layout.dart';
import '../../widgets/brand/auth_form_fields.dart';

/// Pantalla 1 — Login: correo + contraseña contra `POST /api/users/login`.
/// La navegación al home la resuelve el `redirect` del router cuando la sesión
/// pasa a autenticada.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _email = TextEditingController();
  final TextEditingController _password = TextEditingController();

  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .login(email: _email.text, password: _password.text);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final ThemeData theme = Theme.of(context);
    return AuthBrandLayout(
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 56),
            Text(
              'Tu plata,\nen partes claras.',
              style: theme.textTheme.displayLarge?.copyWith(
                fontSize: 34,
                height: 40 / 34,
                letterSpacing: -0.8,
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Ingresa para ver cómo va tu mes.',
              style: theme.textTheme.bodyLarge?.copyWith(
                color: koro.mutedForeground,
              ),
            ),
            const SizedBox(height: 40),
            AuthTextField(
              label: 'Correo electrónico',
              hintText: 'tu@correo.com',
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              validator: _validateEmail,
            ),
            const SizedBox(height: AppSpacing.lg),
            AuthTextField(
              label: 'Contraseña',
              hintText: 'Tu contraseña',
              controller: _password,
              isPassword: true,
              autofillHints: const [AutofillHints.password],
              validator: (v) =>
                  (v == null || v.isEmpty) ? 'Ingresa tu contraseña' : null,
              onFieldSubmitted: (_) => _submit(),
            ),
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
            ],
            const SizedBox(height: AppSpacing.xl),
            AuthPrimaryButton(
              label: 'Ingresar',
              loading: _submitting,
              onPressed: _submit,
            ),
            const SizedBox(height: AppSpacing.xxl),
            AuthSwitchLink(
              question: '¿No tienes cuenta?',
              action: 'Crea una',
              onTap: () => context.push('/register'),
            ),
          ],
        ),
      ),
    );
  }
}

String? _validateEmail(String? value) {
  final String v = value?.trim() ?? '';
  if (v.isEmpty) return 'Ingresa tu correo';
  if (!v.contains('@') || !v.contains('.')) return 'Correo inválido';
  return null;
}
