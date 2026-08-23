import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../data/mock_data.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';
import '../../widgets/cards/section_card.dart';
import '../../widgets/list_items/transaction_tile.dart';

/// Screen 14 — Integración Telegram: link status, 6-digit linking code,
/// "Desvincular" action and the latest expenses registered via the bot.
class TelegramScreen extends StatefulWidget {
  const TelegramScreen({super.key});

  @override
  State<TelegramScreen> createState() => _TelegramScreenState();
}

class _TelegramScreenState extends State<TelegramScreen> {
  bool _linked = true;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    final fromTelegram = MockData.transactions.where((t) => t.fromTelegram).toList();

    return Scaffold(
      appBar: AppBar(title: const Text('Integración Telegram')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(color: koro.info.withValues(alpha: 0.14), shape: BoxShape.circle),
                      child: Icon(Icons.send_outlined, color: koro.info),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Bot de KoroFin', style: Theme.of(context).textTheme.titleMedium),
                          Text(_linked ? 'Vinculado' : 'No vinculado', style: TextStyle(color: _linked ? koro.success : koro.mutedForeground, fontWeight: FontWeight.w600, fontSize: 13)),
                        ],
                      ),
                    ),
                  ],
                ),
                if (!_linked) ...[
                  const SizedBox(height: AppSpacing.lg),
                  Text('Pegá este código en el bot de Telegram para vincular tu cuenta:', style: Theme.of(context).textTheme.bodyMedium),
                  const SizedBox(height: AppSpacing.md),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
                    decoration: BoxDecoration(color: koro.surfaceElevated, borderRadius: BorderRadius.circular(AppRadii.md)),
                    child: const Text('482 913', textAlign: TextAlign.center, style: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: 4)),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  ElevatedButton(onPressed: () => setState(() => _linked = true), child: const Text('Ya pegué el código')),
                ] else ...[
                  const SizedBox(height: AppSpacing.lg),
                  OutlinedButton(
                    style: OutlinedButton.styleFrom(foregroundColor: koro.accent, side: BorderSide(color: koro.accent)),
                    onPressed: () => setState(() => _linked = false),
                    child: const Text('Desvincular'),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text('Últimos gastos vía Telegram', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.sm),
          if (fromTelegram.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
              child: Text('Todavía no registraste gastos por Telegram.', style: Theme.of(context).textTheme.bodyMedium),
            )
          else
            for (final t in fromTelegram)
              Padding(padding: const EdgeInsets.only(bottom: 4), child: TransactionTile(transaction: t)),
          const SizedBox(height: AppSpacing.lg),
          Text(
            'Ejemplo: escribile al bot "Gasté \$${AppFormatters.currency(35000)} en almuerzo" y KoroFin lo registra y categoriza automáticamente.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
