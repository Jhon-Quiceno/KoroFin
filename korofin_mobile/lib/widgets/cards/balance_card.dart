import 'package:flutter/material.dart';

import '../../data/formatters.dart';
import '../../theme/app_spacing.dart';

/// Hero card on the Dashboard: total balance in large bold type over a
/// dark slate surface, with income/expense mini-summary below.
class BalanceCard extends StatelessWidget {
  const BalanceCard({
    super.key,
    required this.balance,
    required this.income,
    required this.expense,
    this.onToggleVisibility,
  });

  final double balance;
  final double income;
  final double expense;
  final VoidCallback? onToggleVisibility;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.xl),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF1E293B), Color(0xFF0F172A)],
        ),
        borderRadius: BorderRadius.circular(AppRadii.xl),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text('Balance total', style: TextStyle(color: Color(0xFF94A3B8), fontSize: 14, fontWeight: FontWeight.w500)),
              const Spacer(),
              InkWell(
                onTap: onToggleVisibility,
                borderRadius: BorderRadius.circular(999),
                child: const Padding(
                  padding: EdgeInsets.all(4),
                  child: Icon(Icons.visibility_outlined, size: 18, color: Color(0xFF94A3B8)),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            AppFormatters.currency(balance),
            style: const TextStyle(color: Colors.white, fontSize: 34, fontWeight: FontWeight.w700, letterSpacing: -0.5),
          ),
          const SizedBox(height: AppSpacing.xl),
          Row(
            children: [
              Expanded(
                child: _MiniStat(
                  icon: Icons.arrow_downward_rounded,
                  color: const Color(0xFF4ADE80),
                  label: 'Ingresos',
                  amount: income,
                ),
              ),
              Container(width: 1, height: 32, color: Colors.white.withValues(alpha: 0.08)),
              Expanded(
                child: _MiniStat(
                  icon: Icons.arrow_upward_rounded,
                  color: const Color(0xFFF87171),
                  label: 'Gastos',
                  amount: expense,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MiniStat extends StatelessWidget {
  const _MiniStat({required this.icon, required this.color, required this.label, required this.amount});

  final IconData icon;
  final Color color;
  final String label;
  final double amount;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: color),
              const SizedBox(width: 4),
              Text(label, style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12, fontWeight: FontWeight.w500)),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            AppFormatters.currency(amount),
            style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w600),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
