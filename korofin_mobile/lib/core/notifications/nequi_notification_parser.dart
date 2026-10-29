/// Resultado de parsear el título+cuerpo de una notificación de Nequi.
sealed class NequiParseResult {
  const NequiParseResult();
}

/// La notificación no corresponde a un pago/envío/recibo de plata reconocible
/// (por ejemplo, la vista previa está oculta por la pantalla de bloqueo, o es
/// una notificación de otro tipo).
class NequiIgnored extends NequiParseResult {
  const NequiIgnored();
}

/// Se detectó un ingreso de plata (recibiste o te transfirieron por Transfiya).
class NequiIncomeDetected extends NequiParseResult {
  const NequiIncomeDetected({required this.amount});

  final double amount;
}

/// Se detectó un egreso de plata (enviaste plata o pagaste algo).
class NequiExpenseDetected extends NequiParseResult {
  const NequiExpenseDetected({required this.amount, this.description});

  final double amount;

  /// Nombre/número de quien recibió la plata o del comercio/servicio pagado,
  /// sugerido como descripción del gasto. `null` si no se pudo extraer.
  final String? description;
}

/// Parser puro (sin dependencias de Flutter/Android) de los textos de las
/// notificaciones push de Nequi. Los formatos reconocidos son los de la spec
/// S4 de `odd/tasks/captura-notificaciones-nequi.md`; cualquier otro texto se
/// ignora.
class NequiNotificationParser {
  NequiNotificationParser._();

  static final RegExp _lockedScreenTitle = RegExp(
    r'^Nequi:\s*Tienes una nueva notificaci[oó]n$',
    caseSensitive: false,
  );

  static final RegExp _incomeTransfiya = RegExp(
    r'Te enviaron\s*\$(?<amount>[\d.,]+)\s*por Transfiya',
    caseSensitive: false,
  );

  static final RegExp _incomeDirect = RegExp(
    r'^.+?\s+te envi[oó]\s*\$(?<amount>[\d.,]+)\.',
    caseSensitive: false,
  );

  static final RegExp _expenseSent = RegExp(
    r'^Enviaste\s*\$(?<amount>[\d.,]+)\s+a\s+(?<target>.+?)\.',
    caseSensitive: false,
  );

  static final RegExp _expensePaid = RegExp(
    r'^Pagaste\s*\$(?<amount>[\d.,]+)\s+en\s+(?<target>.+?)\.',
    caseSensitive: false,
  );

  /// Parsea el título+cuerpo de una notificación de Android y devuelve el
  /// resultado tipado correspondiente. Nunca lanza: un formato desconocido se
  /// devuelve como [NequiIgnored].
  static NequiParseResult parse({required String title, required String body}) {
    final String normalizedTitle = title.trim();
    final String normalizedBody = body.trim();

    if (normalizedBody.isEmpty || _lockedScreenTitle.hasMatch(normalizedTitle)) {
      return const NequiIgnored();
    }

    final RegExpMatch? transfiya = _incomeTransfiya.firstMatch(normalizedBody);
    if (transfiya != null) {
      return NequiIncomeDetected(amount: _parseAmount(transfiya.namedGroup('amount')));
    }

    final RegExpMatch? income = _incomeDirect.firstMatch(normalizedBody);
    if (income != null) {
      return NequiIncomeDetected(amount: _parseAmount(income.namedGroup('amount')));
    }

    final RegExpMatch? sent = _expenseSent.firstMatch(normalizedBody);
    if (sent != null) {
      return NequiExpenseDetected(
        amount: _parseAmount(sent.namedGroup('amount')),
        description: sent.namedGroup('target')?.trim(),
      );
    }

    final RegExpMatch? paid = _expensePaid.firstMatch(normalizedBody);
    if (paid != null) {
      return NequiExpenseDetected(
        amount: _parseAmount(paid.namedGroup('amount')),
        description: paid.namedGroup('target')?.trim(),
      );
    }

    return const NequiIgnored();
  }

  /// Los montos de Nequi usan el punto como separador de miles (ej. "50.000")
  /// sin decimales; se descarta cualquier caracter no numérico.
  static double _parseAmount(String? raw) {
    final String digitsOnly = (raw ?? '').replaceAll(RegExp(r'[^\d]'), '');
    return double.tryParse(digitsOnly) ?? 0;
  }
}
