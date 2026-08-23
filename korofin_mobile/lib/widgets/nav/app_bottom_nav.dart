import 'package:flutter/material.dart';

/// Fixed bottom navigation — 4 tabs: Inicio · Movimientos · Deudas ·
/// Asistente. "Deudas" is a hub grouping the former "Más" items (deudas,
/// tarjetas de crédito, servicios/suscripciones) since they are all
/// recurring financial commitments.
class AppBottomNav extends StatelessWidget {
  const AppBottomNav({super.key, required this.currentIndex, required this.onTap});

  final int currentIndex;
  final ValueChanged<int> onTap;

  static const List<_NavItem> _items = <_NavItem>[
    _NavItem(icon: Icons.home_outlined, activeIcon: Icons.home, label: 'Inicio'),
    _NavItem(icon: Icons.swap_horiz_outlined, activeIcon: Icons.swap_horiz, label: 'Movimientos'),
    _NavItem(icon: Icons.account_balance_wallet_outlined, activeIcon: Icons.account_balance_wallet, label: 'Deudas'),
    _NavItem(icon: Icons.smart_toy_outlined, activeIcon: Icons.smart_toy, label: 'Asistente'),
  ];

  @override
  Widget build(BuildContext context) {
    return BottomNavigationBar(
      currentIndex: currentIndex,
      onTap: onTap,
      items: [
        for (final item in _items)
          BottomNavigationBarItem(
            icon: Icon(item.icon),
            activeIcon: Icon(item.activeIcon),
            label: item.label,
          ),
      ],
    );
  }
}

class _NavItem {
  const _NavItem({required this.icon, required this.activeIcon, required this.label});

  final IconData icon;
  final IconData activeIcon;
  final String label;
}
