import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';

/// Shared top bar for every main screen: a title/subtitle on the left and
/// three discrete, persistent icons on the right (bell, avatar, gear) that
/// are NOT tabs — they open Notificaciones, Perfil and Configuración from
/// anywhere in the app.
class AppHeader extends StatelessWidget implements PreferredSizeWidget {
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
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 12, 4),
        child: Row(
          children: [
            if (leading != null) Padding(padding: const EdgeInsets.only(right: 12), child: leading),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(title, style: Theme.of(context).textTheme.headlineMedium),
                  if (subtitle != null)
                    Text(subtitle!, style: Theme.of(context).textTheme.bodyMedium),
                ],
              ),
            ),
            _HeaderIconButton(
              icon: Icons.notifications_outlined,
              tooltip: 'Notificaciones',
              onTap: onNotificationsTap,
            ),
            const SizedBox(width: 4),
            InkWell(
              borderRadius: BorderRadius.circular(999),
              onTap: onProfileTap,
              child: CircleAvatar(
                radius: 16,
                backgroundColor: koro.accent,
                child: const Text('V', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 13)),
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
  const _HeaderIconButton({required this.icon, required this.tooltip, this.onTap});

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
