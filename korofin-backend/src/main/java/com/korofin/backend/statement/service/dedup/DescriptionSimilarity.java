package com.korofin.backend.statement.service.dedup;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Compara descripciones de movimientos financieros de forma tolerante a diferencias menores de
 * formato (mayúsculas, tildes, orden de palabras, texto extra), para que {@link DuplicateDetector}
 * pueda decidir si dos movimientos probablemente son el mismo.
 *
 * <p>No usa un algoritmo de distancia de edición (ej. Levenshtein) porque las descripciones de
 * extractos bancarios suelen diferir por texto agregado/quitado (ej. un número de referencia) más
 * que por errores de tipeo; un enfoque de conjunto de palabras (Jaccard) tolera mejor ese patrón.
 */
public final class DescriptionSimilarity {

    /** Umbral mínimo de similitud de Jaccard entre conjuntos de palabras para considerarlas similares. */
    private static final double JACCARD_THRESHOLD = 0.34;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DescriptionSimilarity() {
    }

    /**
     * Normaliza una descripción para comparación: minúsculas, sin tildes/diacríticos, caracteres
     * no alfanuméricos reemplazados por espacio, y espacios colapsados y recortados.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String withoutAccents = COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        String withSpaces = NON_ALPHANUMERIC.matcher(withoutAccents).replaceAll(" ");
        return WHITESPACE.matcher(withSpaces).replaceAll(" ").trim();
    }

    /**
     * @return {@code true} si {@code a} y {@code b} se consideran la misma descripción. Cuando
     *         alguna de las dos, normalizada, queda en blanco, se asume similar por defecto —
     *         {@link DuplicateDetector} ya exige coincidencia de monto y cercanía de fecha, así
     *         que ante la falta de texto comparable esas dos señales bastan para decidir.
     */
    public static boolean isSimilar(String a, String b) {
        String normalizedA = normalize(a);
        String normalizedB = normalize(b);
        if (normalizedA.isEmpty() || normalizedB.isEmpty()) {
            return true;
        }
        if (normalizedA.contains(normalizedB) || normalizedB.contains(normalizedA)) {
            return true;
        }
        return jaccardSimilarity(normalizedA, normalizedB) >= JACCARD_THRESHOLD;
    }

    private static double jaccardSimilarity(String normalizedA, String normalizedB) {
        Set<String> tokensA = tokenize(normalizedA);
        Set<String> tokensB = tokenize(normalizedB);

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private static Set<String> tokenize(String normalized) {
        return new HashSet<>(Arrays.asList(normalized.split(" ")));
    }
}
