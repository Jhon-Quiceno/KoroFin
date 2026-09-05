import 'package:flutter/material.dart';

import '../models/category.dart';
import '../models/chat_message.dart';
import '../models/transaction.dart';
import '../theme/app_colors.dart';

/// Hardcoded, realistic COP data so every screen looks populated before the
/// backend is wired up. Mirrors the fictional data used in the v0 prototype.
class MockData {
  MockData._();

  static final List<AppCategory> categories = <AppCategory>[
    AppCategory(id: 'food', name: 'Comida', icon: Icons.restaurant_outlined, color: AppColors.categoryPalette[0]),
    AppCategory(id: 'transport', name: 'Transporte', icon: Icons.directions_car_outlined, color: AppColors.categoryPalette[1]),
    AppCategory(id: 'home', name: 'Hogar', icon: Icons.home_outlined, color: AppColors.categoryPalette[2]),
    AppCategory(id: 'entertainment', name: 'Entretenimiento', icon: Icons.movie_outlined, color: AppColors.categoryPalette[3]),
    AppCategory(id: 'health', name: 'Salud', icon: Icons.medical_services_outlined, color: AppColors.categoryPalette[4]),
    AppCategory(id: 'education', name: 'Educación', icon: Icons.school_outlined, color: AppColors.categoryPalette[5]),
    AppCategory(id: 'shopping', name: 'Compras', icon: Icons.shopping_bag_outlined, color: AppColors.categoryPalette[6]),
    AppCategory(id: 'other', name: 'Otros', icon: Icons.more_horiz, color: AppColors.categoryPalette[7]),
    AppCategory(id: 'salary', name: 'Salario', icon: Icons.payments_outlined, color: AppColors.success),
    AppCategory(id: 'freelance', name: 'Freelance', icon: Icons.laptop_mac_outlined, color: AppColors.info),
  ];

  static AppCategory categoryById(String id) =>
      categories.firstWhere((c) => c.id == id, orElse: () => categories.last);

  static final DateTime _now = DateTime.now();

  static final List<AppTransaction> transactions = <AppTransaction>[
    AppTransaction(
      id: 't1',
      title: 'Supermercado Éxito',
      category: categoryById('food'),
      amount: 187500,
      date: _now.subtract(const Duration(hours: 3)),
      type: TransactionType.expense,
      categorizedByAi: true,
    ),
    AppTransaction(
      id: 't2',
      title: 'Salario agosto',
      category: categoryById('salary'),
      amount: 4200000,
      date: _now.subtract(const Duration(days: 1)),
      type: TransactionType.income,
    ),
    AppTransaction(
      id: 't3',
      title: 'Uber a oficina',
      category: categoryById('transport'),
      amount: 18900,
      date: _now.subtract(const Duration(days: 1, hours: 4)),
      type: TransactionType.expense,
      categorizedByAi: true,
      fromTelegram: true,
    ),
    AppTransaction(
      id: 't4',
      title: 'Netflix',
      category: categoryById('entertainment'),
      amount: 44900,
      date: _now.subtract(const Duration(days: 2)),
      type: TransactionType.expense,
    ),
    AppTransaction(
      id: 't5',
      title: 'Proyecto freelance',
      category: categoryById('freelance'),
      amount: 950000,
      date: _now.subtract(const Duration(days: 3)),
      type: TransactionType.income,
    ),
    AppTransaction(
      id: 't6',
      title: 'Farmacia La Rebaja',
      category: categoryById('health'),
      amount: 62300,
      date: _now.subtract(const Duration(days: 4)),
      type: TransactionType.expense,
      categorizedByAi: true,
    ),
    AppTransaction(
      id: 't7',
      title: 'Arriendo',
      category: categoryById('home'),
      amount: 1350000,
      date: _now.subtract(const Duration(days: 5)),
      type: TransactionType.expense,
    ),
    AppTransaction(
      id: 't8',
      title: 'Curso de inglés',
      category: categoryById('education'),
      amount: 120000,
      date: _now.subtract(const Duration(days: 6)),
      type: TransactionType.expense,
    ),
  ];

  static List<AppTransaction> get expenses =>
      transactions.where((t) => t.type == TransactionType.expense).toList();

  static List<AppTransaction> get incomes =>
      transactions.where((t) => t.type == TransactionType.income).toList();

  static double get totalIncome => incomes.fold(0, (sum, t) => sum + t.amount);

  static double get totalExpense => expenses.fold(0, (sum, t) => sum + t.amount);

  static double get totalBalance => 8450300;






  static final List<ChatMessage> chatHistory = <ChatMessage>[
    ChatMessage(author: ChatAuthor.user, text: '¿Cuánto gasté en comida este mes?', time: _now.subtract(const Duration(minutes: 12))),
    ChatMessage(
      author: ChatAuthor.assistant,
      text: 'Este mes llevas \$612.000 en la categoría Comida, un 9% menos que el mes pasado. ¡Vas muy bien!',
      time: _now.subtract(const Duration(minutes: 11)),
    ),
  ];

  static const List<String> assistantSuggestions = <String>[
    '¿Cómo voy con mi presupuesto?',
    'Escanear un recibo',
    'Ver mi reporte',
    '¿Puedo pagar mi deuda antes?',
  ];
}
