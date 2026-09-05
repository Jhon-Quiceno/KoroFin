import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/notification.dart';
import '../../state/auth/auth_controller.dart';
import '../../state/notifications/notifications_controller.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_text_styles.dart';
import '../../theme/app_theme.dart';
import '../../theme/theme_controller.dart';
import '../../widgets/cards/section_card.dart';

/// Screen 15 — Configuración/Perfil: datos de perfil, cambiar contraseña,
/// integración Telegram, preferencias de notificación, selector de tema y
/// cerrar sesión. Reached from both the header avatar and gear icons.
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _loggingOut = false;

  Future<void> _logout() async {
    setState(() => _loggingOut = true);
    // Al terminar, la sesión pasa a no autenticada y el redirect del router
    // manda al login solo.
    await ref.read(authControllerProvider.notifier).logout();
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      appBar: AppBar(title: const Text('Configuración')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          Builder(builder: (context) {
            final user = ref.watch(authControllerProvider).user;
            return SectionCard(
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
                ],
              ),
            );
          }),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Cuenta',
            child: Column(
              children: [
                _SettingsTile(icon: Icons.lock_outline, label: 'Cambiar contraseña', onTap: () {}),
                _SettingsTile(icon: Icons.send_outlined, label: 'Integración Telegram', onTap: () => context.push('/telegram')),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          const SectionCard(
            title: 'Notificaciones',
            child: _NotificationPreferencesSection(),
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            title: 'Apariencia',
            child: ValueListenableBuilder<ThemeMode>(
              valueListenable: ThemeController.mode,
              builder: (context, mode, _) => SegmentedButton<ThemeMode>(
                segments: const [
                  ButtonSegment(value: ThemeMode.light, icon: Icon(Icons.light_mode_outlined), label: Text('Claro')),
                  ButtonSegment(value: ThemeMode.dark, icon: Icon(Icons.dark_mode_outlined), label: Text('Oscuro')),
                  ButtonSegment(value: ThemeMode.system, icon: Icon(Icons.settings_suggest_outlined), label: Text('Sistema')),
                ],
                selected: {mode},
                onSelectionChanged: (s) => ThemeController.mode.value = s.first,
              ),
            ),
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

/// Switches de preferencias de notificación contra
/// `/api/notifications/preferences`. Cada cambio se guarda de una.
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
        void save(NotificationPreferences next) => ref
            .read(notificationPreferencesProvider.notifier)
            .save(next);
        return Column(
          children: [
            _row('Recordatorios de pago', p.paymentReminders,
                (v) => save(p.copyWith(paymentReminders: v))),
            _row('Alertas de sobregasto', p.overspendAlerts,
                (v) => save(p.copyWith(overspendAlerts: v))),
            _row('Resumen semanal', p.weeklySummary,
                (v) => save(p.copyWith(weeklySummary: v))),
            _row('Recordatorios de inactividad', p.inactivityReminders,
                (v) => save(p.copyWith(inactivityReminders: v))),
            _row('Cierre de ciclo de tarjeta', p.cardCycleClose,
                (v) => save(p.copyWith(cardCycleClose: v))),
            _row('Notificaciones por email', p.emailEnabled,
                (v) => save(p.copyWith(emailEnabled: v))),
          ],
        );
      },
    );
  }

  Widget _row(String label, bool value, ValueChanged<bool> onChanged) =>
      SwitchListTile.adaptive(
        contentPadding: EdgeInsets.zero,
        title: Text(label),
        value: value,
        onChanged: onChanged,
      );
}

class _SettingsTile extends StatelessWidget {
  const _SettingsTile({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(icon, size: 20),
      title: Text(label),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}
