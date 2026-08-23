package com.korofin.backend.statement.service.extraction;

import com.korofin.backend.statement.exception.StatementExtractionException;
import com.korofin.backend.statement.exception.StatementPasswordException;

/**
 * Extrae el texto plano de un archivo de extracto bancario en un formato concreto.
 *
 * <p>Cada implementación resuelve un formato de archivo (PDF, CSV, XLSX); el despacho por
 * extensión de archivo lo hace {@code StatementTextExtractionService}.
 */
public interface StatementTextExtractor {

    /**
     * @param filename nombre original del archivo subido (incluye la extensión)
     * @return {@code true} si esta implementación sabe extraer texto de {@code filename}
     */
    boolean supports(String filename);

    /**
     * @param content  contenido crudo del archivo
     * @param password contraseña del archivo, o {@code null} si no está protegido; ignorada por
     *                 implementaciones que no soportan archivos protegidos (CSV, XLSX)
     * @return el texto plano extraído del archivo
     * @throws StatementPasswordException   si el archivo está protegido y la contraseña no es válida
     * @throws StatementExtractionException si el archivo no se pudo leer o parsear
     */
    String extract(byte[] content, String password);
}
