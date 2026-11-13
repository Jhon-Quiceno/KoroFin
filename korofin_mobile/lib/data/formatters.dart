import 'package:intl/intl.dart';

/// COP currency and date formatting shared across the app.
class AppFormatters {
  AppFormatters._();

  static final NumberFormat _copFormat = NumberFormat.currency(
    locale: 'es_CO',
    symbol: '\$',
    decimalDigits: 0,
  );

  static final NumberFormat _copFormatWithCents = NumberFormat.currency(
    locale: 'es_CO',
    symbol: '\$',
    decimalDigits: 2,
  );

  static bool _hasCents(num amount) => (amount * 100).round() % 100 != 0;

  /// Monto en pesos. Los centavos se muestran solo cuando el monto los tiene
  /// (`$ 172.875,24`); un monto entero queda como siempre (`$ 50.000`).
  static String currency(num amount) => _hasCents(amount)
      ? _copFormatWithCents.format(amount)
      : _copFormat.format(amount);

  /// Texto para pre-llenar un campo de monto al editar: sin separador de
  /// miles y con coma decimal solo si hay centavos (`172875,24`, `50000`).
  static String amountInput(num amount) => _hasCents(amount)
      ? amount.toStringAsFixed(2).replaceAll('.', ',')
      : amount.toStringAsFixed(0);

  /// Lee un monto escrito por el usuario, o `null` si no es válido.
  ///
  /// La coma es el separador decimal y el punto el de miles (`172.875,24`).
  /// Como el teclado numérico de Android suele ofrecer solo el punto, un único
  /// punto seguido de uno o dos dígitos también se toma como decimal
  /// (`172875.24`); seguido de tres dígitos es separador de miles (`50.000`).
  /// Se aceptan como máximo dos decimales, igual que el backend.
  static double? parseAmount(String raw) {
    String text = raw.replaceAll(RegExp(r'[\s$]'), '');
    if (text.isEmpty) return null;
    if (text.contains(',')) {
      if (text.indexOf(',') != text.lastIndexOf(',')) return null;
      text = text.replaceAll('.', '').replaceAll(',', '.');
    } else {
      final int lastDot = text.lastIndexOf('.');
      final bool singleDot = lastDot != -1 && text.indexOf('.') == lastDot;
      final int digitsAfterDot = text.length - lastDot - 1;
      final bool dotIsDecimal =
          singleDot && digitsAfterDot >= 1 && digitsAfterDot <= 2;
      if (!dotIsDecimal) text = text.replaceAll('.', '');
    }
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(text)) return null;
    return double.parse(text);
  }

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
