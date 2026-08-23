import 'package:flutter/material.dart';

/// A recurring payment (subscription, membership, service) shown in the
/// "Servicios / Suscripciones" sub-tab.
class Subscription {
  const Subscription({
    required this.id,
    required this.name,
    required this.icon,
    required this.color,
    required this.amount,
    required this.nextChargeDate,
  });

  final String id;
  final String name;
  final IconData icon;
  final Color color;
  final double amount;
  final DateTime nextChargeDate;

  bool get isDueSoon => nextChargeDate.difference(DateTime.now()).inDays < 5;
}
