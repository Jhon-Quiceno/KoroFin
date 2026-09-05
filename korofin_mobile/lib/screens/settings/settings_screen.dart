import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import '../../models/notification.dart';
import '../../models/user_preferences.dart';
import '../../state/auth/auth_controller.dart';
import '../../state/notifications/notifications_controller.dart';
import '../../state/preferences/preferences_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../../widgets/cards/section_card.dart';

/// Pantalla 15 — Configuración/Perfil: perfil editable, cambio de contraseña,
/// preferencias de notificación, tema/moneda/idioma y cerrar sesión.
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _loggingOut = false;

  Future<void> _logout() async {
    setState(() => _loggingOut = true);
    await ref.read(authControllerProvider.notifier).logout();
  }

  Future<void> _editProfile() async {
    final user = ref.read(authControllerProvider).user;
    if (user == null) return;
    final result = await showModalBottomSheet<({String name, String email})>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _EditProfileSheet(name: user.name, email: user.email),
    );
    if (result == null) return;
    try {
      final updated = await ref.read(userRepositoryProvider).updateProfile(
            name: result.name,
            email: result.email,
          );
      ref.read(authControllerProvider.notifier).applyUser(updated);
      _snack('Perfil actualizado');
    } on ApiException catch (e) {
      _snack(e.message);
    }
  }

  Future<void> _changePassword() async {
    final result =
        await showModalBottomSheet<({String current, String next})>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const _ChangePasswordSheet(),
    );
    if (result == null) return;
    try {
      await ref.read(userRepositoryProvider).changePassword(
            currentPassword: result.current,
            newPassword: result.next,
          );
      _snack('Contraseña actualizada');
    } on ApiException catch (e) {
      _snack(e.message);
    }
  }

  void _snack(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final user = ref.watch(authControllerProvider).user;

    return Scaffold(
      appBar: AppBar(title: const Text('Configuración')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          SectionCard(
            child: InkWell(
              onTap: _editProfile,
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: koro.accent,
                    child: Text(
                      (user?.name.trim().isNotEmpty ?? false)
                          ? user!.name.trim()[0].toUpperCase()
                          : '?',
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.lg),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(user?.name ?? '—',
                            style: Theme.of(context).textTheme.titleLarge),
                        Text(user?.email ?? '',
                            style: Theme.of(context).textTheme.bodyMedium),
                      ],
                    ),
                  ),
                  const Icon(Icons.edit_outlined, size: 18),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Cuenta',
            child: _SettingsTile(
              icon: Icons.lock_outline,
              label: 'Cambiar contraseña',
              onTap: _changePassword,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          const SectionCard(
            title: 'Notificaciones',
            child: _NotificationPreferencesSection(),
          ),
          const SizedBox(height: AppSpacing.lg),
          const SectionCard(
            title: 'Preferencias',
            child: _PreferencesSection(),
          ),
          const SizedBox(height: AppSpacing.xl),
          OutlinedButton.icon(
            onPressed: _loggingOut ? null : _logout,
            icon: _loggingOut
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2.5),
                  )
                : Icon(Icons.logout, color: koro.accent),
            label: Text('Cerrar sesión',
                style: AppTextStyles.bodyMediumMedium(koro.accent)),
            style: OutlinedButton.styleFrom(side: BorderSide(color: koro.accent)),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _PreferencesSection extends ConsumerWidget {
  const _PreferencesSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final prefs = ref.watch(userPreferencesProvider);
    final notifier = ref.read(userPreferencesProvider.notifier);
    return prefs.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(AppSpacing.md),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (_, _) => const Text('No se pudieron cargar las preferencias.'),
      data: (p) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Tema'),
          const SizedBox(height: AppSpacing.sm),
          SegmentedButton<ThemePreference>(
            segments: const [
              ButtonSegment(
                  value: ThemePreference.light,
                  icon: Icon(Icons.light_mode_outlined),
                  label: Text('Claro')),
              ButtonSegment(
                  value: ThemePreference.dark,
                  icon: Icon(Icons.dark_mode_outlined),
                  label: Text('Oscuro')),
              ButtonSegment(
                  value: ThemePreference.system,
                  icon: Icon(Icons.settings_suggest_outlined),
                  label: Text('Sistema')),
            ],
            selected: {p.theme},
            onSelectionChanged: (s) => notifier.setTheme(s.first),
          ),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(
                child: _Dropdown<String>(
                  label: 'Moneda',
                  value: p.currency,
                  items: {for (final c in supportedCurrencies) c: c},
                  onChanged: notifier.setCurrency,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: _Dropdown<AppLanguage>(
                  label: 'Idioma',
                  value: p.language,
                  items: {for (final l in AppLanguage.values) l: l.label},
                  onChanged: notifier.setLanguage,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Dropdown<T> extends StatelessWidget {
  const _Dropdown({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
  });

  final String label;
  final T value;
  final Map<T, String> items;
  final ValueChanged<T> onChanged;

  @override
  Widget build(BuildContext context) => InputDecorator(
        decoration: InputDecoration(labelText: label),
        child: DropdownButtonHideUnderline(
          child: DropdownButton<T>(
            isExpanded: true,
            isDense: true,
            value: value,
            items: [
              for (final entry in items.entries)
                DropdownMenuItem(value: entry.key, child: Text(entry.value)),
            ],
            onChanged: (v) => v == null ? null : onChanged(v),
          ),
        ),
      );
}

class _NotificationPreferencesSection extends ConsumerWidget {
  const _NotificationPreferencesSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final prefs = ref.watch(notificationPreferencesProvider);
    return prefs.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(AppSpacing.md),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (_, _) => const Text('No se pudieron cargar las preferencias.'),
      data: (p) {
        void save(NotificationPreferences next) =>
            ref.read(notificationPreferencesProvider.notifier).save(next);
        return Column(
          children: [
            _switch('Recordatorios de pago', p.paymentReminders,
                (v) => save(p.copyWith(paymentReminders: v))),
            _switch('Alertas de sobregasto', p.overspendAlerts,
                (v) => save(p.copyWith(overspendAlerts: v))),
            _switch('Resumen semanal', p.weeklySummary,
                (v) => save(p.copyWith(weeklySummary: v))),
            _switch('Recordatorios de inactividad', p.inactivityReminders,
                (v) => save(p.copyWith(inactivityReminders: v))),
            _switch('Cierre de ciclo de tarjeta', p.cardCycleClose,
                (v) => save(p.copyWith(cardCycleClose: v))),
            _switch('Notificaciones por email', p.emailEnabled,
                (v) => save(p.copyWith(emailEnabled: v))),
          ],
        );
      },
    );
  }

  Widget _switch(String label, bool value, ValueChanged<bool> onChanged) =>
      SwitchListTile.adaptive(
        contentPadding: EdgeInsets.zero,
        title: Text(label),
        value: value,
        onChanged: onChanged,
      );
}

class _SettingsTile extends StatelessWidget {
  const _SettingsTile(
      {required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Icon(icon, size: 20),
        title: Text(label),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: onTap,
      );
}

// ---------------------------------------------------------------------------

class _EditProfileSheet extends StatefulWidget {
  const _EditProfileSheet({required this.name, required this.email});

  final String name;
  final String email;

  @override
  State<_EditProfileSheet> createState() => _EditProfileSheetState();
}

class _EditProfileSheetState extends State<_EditProfileSheet> {
  late final TextEditingController _name =
      TextEditingController(text: widget.name);
  late final TextEditingController _email =
      TextEditingController(text: widget.email);
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    super.dispose();
  }

  void _submit() {
    final name = _name.text.trim();
    final email = _email.text.trim();
    if (name.isEmpty || !email.contains('@') || !email.contains('.')) {
      setState(() => _error = 'Revisá el nombre y el correo.');
      return;
    }
    Navigator.of(context).pop((name: name, email: email));
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg,
            AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Editar perfil',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(
                controller: _name,
                decoration: const InputDecoration(labelText: 'Nombre')),
            const SizedBox(height: AppSpacing.md),
            TextField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration:
                    const InputDecoration(labelText: 'Correo electrónico')),
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(_error!,
                  style: TextStyle(
                      color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: AppSpacing.lg),
            ElevatedButton(
                onPressed: _submit, child: const Text('Guardar')),
          ],
        ),
      ),
    );
  }
}

class _ChangePasswordSheet extends StatefulWidget {
  const _ChangePasswordSheet();

  @override
  State<_ChangePasswordSheet> createState() => _ChangePasswordSheetState();
}

class _ChangePasswordSheetState extends State<_ChangePasswordSheet> {
  final _current = TextEditingController();
  final _next = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    super.dispose();
  }

  void _submit() {
    if (_current.text.isEmpty || _next.text.length < 6) {
      setState(() =>
          _error = 'La nueva contraseña necesita al menos 6 caracteres.');
      return;
    }
    Navigator.of(context).pop((current: _current.text, next: _next.text));
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg,
            AppSpacing.lg + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Cambiar contraseña',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            TextField(
                controller: _current,
                obscureText: true,
                decoration:
                    const InputDecoration(labelText: 'Contraseña actual')),
            const SizedBox(height: AppSpacing.md),
            TextField(
                controller: _next,
                obscureText: true,
                decoration:
                    const InputDecoration(labelText: 'Nueva contraseña')),
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(_error!,
                  style: TextStyle(
                      color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: AppSpacing.lg),
            ElevatedButton(
                onPressed: _submit, child: const Text('Guardar')),
          ],
        ),
      ),
    );
  }
}
