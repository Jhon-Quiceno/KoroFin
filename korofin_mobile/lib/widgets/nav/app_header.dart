import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/auth/auth_controller.dart';
import '../../state/notifications/notifications_controller.dart';
import '../../theme/app_theme.dart';

/// Barra superior compartida: título/subtítulo a la izquierda y tres íconos
/// persistentes (campana con badge de no leídas, avatar con la inicial real,
/// engranaje) que abren Notificaciones, Perfil y Configuración.
class AppHeader extends ConsumerWidget implements PreferredSizeWidget {
  const AppHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.onNotificationsTap,
    this.onProfileTap,
    this.onSettingsTap,
    this.leading,
  });

  final String title;
  final String? subtitle;
  final VoidCallback? onNotificationsTap;
  final VoidCallback? onProfileTap;
  final VoidCallback? onSettingsTap;
  final Widget? leading;

  @override
  Size get preferredSize => const Size.fromHeight(72);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final koro = context.koroColors;
    final int unread =
        ref.watch(unreadCountProvider).maybeWhen(data: (n) => n, orElse: () => 0);
    final String initial = ref
        .watch(authControllerProvider.select((s) => s.user?.name ?? ''))
        .trim()
        .split(' ')
        .where((p) => p.isNotEmpty)
        .map((p) => p[0].toUpperCase())
        .take(1)
        .join();

    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 12, 4),
        child: Row(
          children: [
            if (leading != null)
              Padding(
                  padding: const EdgeInsets.only(right: 12), child: leading),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(title,
                      style: Theme.of(context).textTheme.headlineMedium),
                  if (subtitle != null)
                    Text(subtitle!,
                        style: Theme.of(context).textTheme.bodyMedium),
                ],
              ),
            ),
            Stack(
              clipBehavior: Clip.none,
              children: [
                _HeaderIconButton(
                  icon: Icons.notifications_outlined,
                  tooltip: 'Notificaciones',
                  onTap: onNotificationsTap,
                ),
                if (unread > 0)
                  Positioned(
                    right: 2,
                    top: 2,
                    child: Container(
                      padding: const EdgeInsets.all(3),
                      constraints:
                          const BoxConstraints(minWidth: 16, minHeight: 16),
                      decoration: BoxDecoration(
                        color: koro.accent,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        unread > 9 ? '9+' : '$unread',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 9,
                            fontWeight: FontWeight.w700),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 4),
            InkWell(
              borderRadius: BorderRadius.circular(999),
              onTap: onProfileTap,
              child: CircleAvatar(
                radius: 16,
                backgroundColor: koro.accent,
                child: Text(initial.isEmpty ? '?' : initial,
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 13)),
              ),
            ),
            const SizedBox(width: 4),
            _HeaderIconButton(
              icon: Icons.settings_outlined,
              tooltip: 'Configuración',
              onTap: onSettingsTap,
            ),
          ],
        ),
      ),
    );
  }
}

class _HeaderIconButton extends StatelessWidget {
  const _HeaderIconButton(
      {required this.icon, required this.tooltip, this.onTap});

  final IconData icon;
  final String tooltip;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return IconButton(
      tooltip: tooltip,
      onPressed: onTap,
      icon: Icon(icon, size: 22, color: koro.foreground),
      style: IconButton.styleFrom(
        backgroundColor: koro.surfaceElevated,
        shape: const CircleBorder(),
        minimumSize: const Size(38, 38),
      ),
    );
  }
}
