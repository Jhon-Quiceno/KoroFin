import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/api_exception.dart';
import '../../state/auth/auth_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/brand/auth_brand_layout.dart';
import '../../widgets/brand/auth_form_fields.dart';

/// Pantalla 2 — Registro contra `POST /api/users/register`. Al crear la cuenta
/// el backend ya devuelve la sesión, así que se entra directo al home (lo
/// resuelve el `redirect` del router).
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _name = TextEditingController();
  final TextEditingController _email = TextEditingController();
  final TextEditingController _password = TextEditingController();

  bool _acceptedTerms = false;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || !_acceptedTerms) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .register(
            name: _name.text,
            email: _email.text,
            password: _password.text,
          );
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
      leading: IconButton(
        tooltip: 'Volver',
        icon: const Icon(Icons.arrow_back),
        onPressed: () => context.pop(),
      ),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 40),
            Text(
              'Crea tu cuenta',
              style: theme.textTheme.displayLarge?.copyWith(
                fontSize: 34,
                height: 40 / 34,
                letterSpacing: -0.8,
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Empieza a ver tu plata en partes claras.',
              style: theme.textTheme.bodyLarge?.copyWith(
                color: koro.mutedForeground,
              ),
            ),
            const SizedBox(height: 32),
            AuthTextField(
              label: 'Nombre',
              hintText: 'Como quieres que te llamemos',
              controller: _name,
              textCapitalization: TextCapitalization.words,
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Ingresa tu nombre' : null,
            ),
            const SizedBox(height: AppSpacing.lg),
            AuthTextField(
              label: 'Correo electrónico',
              hintText: 'tu@correo.com',
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              validator: (v) {
                final String value = v?.trim() ?? '';
                if (value.isEmpty) return 'Ingresa tu correo';
                if (!value.contains('@') || !value.contains('.')) {
                  return 'Correo inválido';
                }
                return null;
              },
            ),
            const SizedBox(height: AppSpacing.lg),
            AuthTextField(
              label: 'Contraseña',
              hintText: 'Mínimo 8 caracteres',
              controller: _password,
              isPassword: true,
              autofillHints: const [AutofillHints.newPassword],
              validator: (v) =>
                  (v == null || v.length < 8) ? 'Mínimo 8 caracteres' : null,
            ),
            const SizedBox(height: AppSpacing.md),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Checkbox(
                  value: _acceptedTerms,
                  onChanged: (v) => setState(() => _acceptedTerms = v ?? false),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Text.rich(
                      TextSpan(
                        style: Theme.of(context).textTheme.bodyMedium,
                        children: [
                          const TextSpan(text: 'Acepto los '),
                          TextSpan(
                            text: 'términos y condiciones',
                            style: TextStyle(
                              color: koro.accent,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const TextSpan(text: ' y la política de privacidad.'),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: AppSpacing.lg),
            AuthPrimaryButton(
              label: 'Crear cuenta',
              loading: _submitting,
              onPressed: _acceptedTerms ? _submit : null,
            ),
            const SizedBox(height: AppSpacing.xl),
            AuthSwitchLink(
              question: '¿Ya tienes cuenta?',
              action: 'Ingresa',
              onTap: () => context.pop(),
            ),
          ],
        ),
      ),
    );
  }
}
