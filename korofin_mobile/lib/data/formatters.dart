import 'package:intl/intl.dart';

/// COP currency and date formatting shared across the app.
class AppFormatters {
  AppFormatters._();

  static final NumberFormat _copFormat = NumberFormat.currency(
    locale: 'es_CO',
    symbol: '\$',
    decimalDigits: 0,
  );

  static String currency(num amount) => _copFormat.format(amount);

  static String shortDate(DateTime date) => DateFormat('d MMM', 'es_CO').format(date);

  static String longDate(DateTime date) => DateFormat('d MMMM y', 'es_CO').format(date);

  static String time(DateTime date) => DateFormat('HH:mm').format(date);

  static String relative(DateTime date) {
    final Duration diff = DateTime.now().difference(date);
    if (diff.inMinutes < 1) return 'Ahora';
    if (diff.inMinutes < 60) return 'Hace ${diff.inMinutes} min';
    if (diff.inHours < 24) return 'Hace ${diff.inHours} h';
    if (diff.inDays < 7) return 'Hace ${diff.inDays} d';
    return shortDate(date);
  }
}
