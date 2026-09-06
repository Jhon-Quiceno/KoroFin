package com.korofin.backend.statement.service.extraction;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Extrae el texto de extractos bancarios exportados en formato CSV: el contenido ya es texto
 * plano, así que solo se decodifica como UTF-8 sin ninguna transformación adicional (el propio
 * texto delimitado por comas es suficiente contexto para el modelo de IA).
 */
@Component
public class CsvStatementTextExtractor implements StatementTextExtractor {

    private static final String EXTENSION = ".csv";

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    @Override
    public String extract(byte[] content, String password) {
        return new String(content, StandardCharsets.UTF_8);
    }
}
