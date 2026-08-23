import 'package:flutter/material.dart';

import '../models/category.dart';
import '../models/chat_message.dart';
import '../models/credit_card.dart';
import '../models/debt.dart';
import '../models/notification_item.dart';
import '../models/subscription.dart';
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

  static final List<Debt> debts = <Debt>[
    Debt(
      id: 'd1',
      name: 'Crédito vehículo',
      lender: 'Banco de Bogotá',
      totalAmount: 32000000,
      paidAmount: 21500000,
      dueDate: _now.add(const Duration(days: 400)),
      payments: <DebtPayment>[
        DebtPayment(date: _now.subtract(const Duration(days: 15)), amount: 890000),
        DebtPayment(date: _now.subtract(const Duration(days: 45)), amount: 890000),
        DebtPayment(date: _now.subtract(const Duration(days: 75)), amount: 890000),
      ],
    ),
    Debt(
      id: 'd2',
      name: 'Libre inversión',
      lender: 'Bancolombia',
      totalAmount: 6000000,
      paidAmount: 2100000,
      dueDate: _now.add(const Duration(days: 210)),
      payments: <DebtPayment>[
        DebtPayment(date: _now.subtract(const Duration(days: 10)), amount: 350000),
        DebtPayment(date: _now.subtract(const Duration(days: 40)), amount: 350000),
      ],
    ),
    Debt(
      id: 'd3',
      name: 'Estudios posgrado',
      lender: 'Sistecrédito',
      totalAmount: 9500000,
      paidAmount: 8700000,
      dueDate: _now.add(const Duration(days: 90)),
      payments: <DebtPayment>[
        DebtPayment(date: _now.subtract(const Duration(days: 5)), amount: 400000),
      ],
    ),
  ];

  static final List<AppCreditCard> creditCards = <AppCreditCard>[
    AppCreditCard(
      id: 'c1',
      name: 'Visa Signature',
      bank: 'Bancolombia',
      totalLimit: 8000000,
      usedAmount: 2350000,
      closingDay: 28,
      lastFourDigits: '4821',
    ),
    AppCreditCard(
      id: 'c2',
      name: 'Mastercard Black',
      bank: 'Davivienda',
      totalLimit: 5000000,
      usedAmount: 4100000,
      closingDay: 5,
      lastFourDigits: '7734',
    ),
  ];

  static final List<CardMovement> cardMovements = <CardMovement>[
    CardMovement(description: 'Amazon', amount: 245000, date: _now.subtract(const Duration(days: 2)), type: CardMovementType.purchase),
    CardMovement(description: 'Pago mínimo', amount: 500000, date: _now.subtract(const Duration(days: 10)), type: CardMovementType.payment),
    CardMovement(description: 'Restaurante La Puerta', amount: 138000, date: _now.subtract(const Duration(days: 12)), type: CardMovementType.purchase),
  ];

  static final List<Subscription> subscriptions = <Subscription>[
    Subscription(id: 's1', name: 'Netflix', icon: Icons.tv_outlined, color: const Color(0xFFE50914), amount: 44900, nextChargeDate: _now.add(const Duration(days: 2))),
    Subscription(id: 's2', name: 'Gimnasio Bodytech', icon: Icons.fitness_center_outlined, color: AppColors.categoryPalette[2], amount: 129900, nextChargeDate: _now.add(const Duration(days: 4))),
    Subscription(id: 's3', name: 'Spotify', icon: Icons.music_note_outlined, color: const Color(0xFF1DB954), amount: 19900, nextChargeDate: _now.add(const Duration(days: 12))),
    Subscription(id: 's4', name: 'iCloud+', icon: Icons.cloud_outlined, color: AppColors.info, amount: 12900, nextChargeDate: _now.add(const Duration(days: 20))),
  ];

  static final List<NotificationItem> notifications = <NotificationItem>[
    NotificationItem(id: 'n1', title: 'Netflix se cobra en 2 días', description: 'Se debitarán \$44.900 de tu tarjeta Visa Signature.', date: _now.subtract(const Duration(hours: 2)), type: NotificationType.dueDate),
    NotificationItem(id: 'n2', title: 'Nuevo insight de la IA', description: 'Detectamos que gastaste 18% más en transporte este mes.', date: _now.subtract(const Duration(hours: 6)), type: NotificationType.ai),
    NotificationItem(id: 'n3', title: 'Gasto registrado vía Telegram', description: 'Uber a oficina — \$18.900 categorizado automáticamente.', date: _now.subtract(const Duration(days: 1, hours: 4)), type: NotificationType.system, isRead: true),
    NotificationItem(id: 'n4', title: 'Cuota de crédito vehículo próxima', description: 'Vence en 5 días. Monto sugerido: \$890.000.', date: _now.subtract(const Duration(days: 2)), type: NotificationType.dueDate, isRead: true),
    NotificationItem(id: 'n5', title: 'Reporte mensual disponible', description: 'Tu reporte de julio ya está listo para revisar.', date: _now.subtract(const Duration(days: 3)), type: NotificationType.system, isRead: true),
  ];

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
