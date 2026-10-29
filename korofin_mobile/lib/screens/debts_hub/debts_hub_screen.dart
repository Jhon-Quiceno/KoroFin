import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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
class DebtsHubScreen extends ConsumerStatefulWidget {
  const DebtsHubScreen({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  ConsumerState<DebtsHubScreen> createState() => _DebtsHubScreenState();
}

class _DebtsHubScreenState extends ConsumerState<DebtsHubScreen> {
  late int _tab = widget.initialTab.clamp(0, 2);

  @override
  void didUpdateWidget(covariant DebtsHubScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialTab != widget.initialTab) {
      setState(() => _tab = widget.initialTab.clamp(0, 2));
    }
  }

  static const List<Widget> _tabs = [DebtsTab(), CreditCardsTab(), SubscriptionsTab()];

  /// Delega al alta correspondiente según la sub-pestaña activa, reusando
  /// exactamente el mismo flujo que cada tab ya expone (ver sección D).
  Future<void> _onFabPressed() {
    switch (_tab) {
      case 1:
        return CreditCardsTab.add(context, ref);
      case 2:
        return SubscriptionsTab.add(context, ref);
      case 0:
      default:
        return DebtsTab.add(context, ref);
    }
  }

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return Scaffold(
      body: Column(
        children: [
          AppHeader(
            title: 'Deudas',
            subtitle: 'Compromisos financieros recurrentes',
            onNotificationsTap: () => context.push('/notifications'),
            onProfileTap: () => context.push('/settings'),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
            child: SegmentedButton<int>(
              showSelectedIcon: false,
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
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _onFabPressed,
        child: const Icon(Icons.add),
      ),
    );
  }
}
