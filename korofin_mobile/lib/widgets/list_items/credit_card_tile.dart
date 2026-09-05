import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../models/credit_card.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_theme.dart';

/// Tile con forma de tarjeta: nombre, franquicia, banco y barra de uso del
/// cupo. Tocable para abrir el detalle.
class CreditCardTile extends StatelessWidget {
  const CreditCardTile({super.key, required this.card, required this.onTap});

  final CreditCard card;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final koro = context.koroColors;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.xl),
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.xl),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF334155), Color(0xFF0F172A)],
          ),
          borderRadius: BorderRadius.circular(AppRadii.xl),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(card.name,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w700)),
                ),
                Text(card.bank ?? card.franchise.label,
                    style: const TextStyle(
                        color: Color(0xFF94A3B8),
                        fontSize: 13,
                        fontWeight: FontWeight.w500)),
              ],
            ),
            Text(card.franchise.label,
                style: const TextStyle(
                    color: Color(0xFF94A3B8), fontSize: 13, letterSpacing: 1.2)),
            const SizedBox(height: AppSpacing.lg),
            ClipRRect(
              borderRadius: BorderRadius.circular(AppRadii.pill),
              child: LinearProgressIndicator(
                value: card.usageRatio,
                minHeight: 6,
                backgroundColor: Colors.white.withValues(alpha: 0.12),
                valueColor: AlwaysStoppedAnimation(koro.accent),
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _Stat(label: 'Disponible', value: card.availableCredit),
                _Stat(label: 'Usado', value: card.currentBalance, end: true),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value, this.end = false});

  final String label;
  final double value;
  final bool end;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment:
            end ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12)),
          Text(AppFormatters.currency(value),
              style: const TextStyle(
                  color: Colors.white, fontWeight: FontWeight.w600)),
        ],
      );
}
