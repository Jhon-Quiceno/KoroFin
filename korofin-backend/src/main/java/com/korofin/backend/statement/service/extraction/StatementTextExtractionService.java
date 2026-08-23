package com.korofin.backend.statement.service.extraction;

import com.korofin.backend.statement.exception.EmptyStatementTextException;
import com.korofin.backend.statement.exception.UnsupportedStatementFileException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Despacha la extracción de texto de un extracto bancario al {@link StatementTextExtractor}
 * correcto según la extensión del archivo ({@code .pdf}, {@code .csv} o {@code .xlsx}).
 *
 * <p>Spring inyecta automáticamente todas las implementaciones de {@link StatementTextExtractor}
 * registradas como {@code @Component} en la lista {@link #extractors}; agregar un formato nuevo
 * solo requiere agregar una implementación más, sin tocar esta clase.
 */
@Service
public class StatementTextExtractionService {

    private final List<StatementTextExtractor> extractors;

    public StatementTextExtractionService(List<StatementTextExtractor> extractors) {
        this.extractors = extractors;
    }

    /**
     * @param filename nombre original del archivo subido (incluye la extensión)
     * @param content  contenido crudo del archivo
     * @param password contraseña del archivo, o {@code null} si no está protegido
     * @return el texto plano extraído, nunca en blanco
     * @throws UnsupportedStatementFileException si ningún extractor soporta la extensión del archivo
     * @throws EmptyStatementTextException       si el texto extraído queda en blanco
     */
    public String extractText(String filename, byte[] content, String password) {
        StatementTextExtractor extractor = extractors.stream()
                .filter(candidate -> candidate.supports(filename))
                .findFirst()
                .orElseThrow(() -> new UnsupportedStatementFileException(
                        "El formato del archivo no es compatible. Se aceptan archivos .pdf, .csv y .xlsx."
                ));

        String text = extractor.extract(content, password);
        if (text == null || text.isBlank()) {
            throw new EmptyStatementTextException(
                    "No se encontró texto en el archivo. Verifique que el extracto no sea una imagen escaneada."
            );
        }
        return text;
    }
}
