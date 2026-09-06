package com.korofin.backend.scheduling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formateo compartido en español para los títulos/mensajes que arman los jobs programados y
 * listeners de eventos de {@code service/scheduling}.
 *
 * <p>Los montos se formatean con un {@link DecimalFormatSymbols} explícito en vez de un
 * {@link Locale} {@code es-*} — evita depender de que la JVM tenga datos de locale en español
 * empaquetados. Las fechas usan {@code dd/MM/yyyy} por la misma razón: se lee sin ambigüedad en
 * español sin necesitar nombres de mes localizados.
 */
final class NotificationMessageFormatter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private NotificationMessageFormatter() {
    }

    /** Formatea {@code amount} como cifra entera con separadores de miles "." (ej. {@code "45.000"}). */
    static String formatAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal rounded = safeAmount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        return format.format(rounded);
    }

    /** Formatea {@code date} como {@code dd/MM/yyyy} (ej. {@code "14/07/2026"}). */
    static String formatDate(LocalDate date) {
        return date.format(DATE_FORMAT);
    }
}
