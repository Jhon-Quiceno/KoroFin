import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/nav/app_header.dart';
import 'credit_cards_tab.dart';
import 'debts_tab.dart';
import 'subscriptions_tab.dart';

/// "Deudas" bottom-nav destination: a hub grouping three recurring
/// financial-commitment modules behind a segmented control — Deudas,
/// Tarjetas de crédito, Servicios/Suscripciones (screens 5, 9, 10).
class DebtsHubScreen extends StatefulWidget {
  const DebtsHubScreen({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  State<DebtsHubScreen> createState() => _DebtsHubScreenState();
}

class _DebtsHubScreenState extends State<DebtsHubScreen> {
  late int _tab = widget.initialTab.clamp(0, 2);

  @override
  void didUpdateWidget(covariant DebtsHubScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialTab != widget.initialTab) {
      setState(() => _tab = widget.initialTab.clamp(0, 2));
    }
  }

  static const List<Widget> _tabs = [DebtsTab(), CreditCardsTab(), SubscriptionsTab()];

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Column(
      children: [
        AppHeader(
          title: 'Deudas',
          subtitle: 'Compromisos financieros recurrentes',
          onNotificationsTap: () => context.push('/notifications'),
          onProfileTap: () => context.push('/settings'),
          onSettingsTap: () => context.push('/settings'),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          child: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 0, label: Text('Deudas')),
              ButtonSegment(value: 1, label: Text('Tarjetas')),
              ButtonSegment(value: 2, label: Text('Servicios')),
            ],
            selected: {_tab},
            onSelectionChanged: (s) => setState(() => _tab = s.first),
            style: SegmentedButton.styleFrom(
              selectedBackgroundColor: koro.accent,
              selectedForegroundColor: Colors.white,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.sm),
        Expanded(child: IndexedStack(index: _tab, sizing: StackFit.expand, children: _tabs)),
      ],
    );
  }
}
