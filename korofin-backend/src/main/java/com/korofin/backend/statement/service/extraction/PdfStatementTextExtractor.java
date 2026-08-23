package com.korofin.backend.statement.service.extraction;

import com.korofin.backend.statement.exception.StatementExtractionException;
import com.korofin.backend.statement.exception.StatementPasswordException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Extrae el texto de extractos bancarios en formato PDF, incluyendo PDFs protegidos con
 * contraseña de apertura.
 *
 * <p>{@link Loader#loadPDF(byte[], String)} acepta {@code password == null} de la misma forma que
 * una cadena vacía cuando el documento no está protegido, por lo que este extractor no necesita
 * distinguir entre "sin contraseña" y "contraseña no provista".
 */
@Component
public class PdfStatementTextExtractor implements StatementTextExtractor {

    private static final String EXTENSION = ".pdf";

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    @Override
    public String extract(byte[] content, String password) {
        try (PDDocument document = Loader.loadPDF(content, password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (InvalidPasswordException ex) {
            throw new StatementPasswordException(
                    "La contraseña del PDF es incorrecta o el archivo requiere una contraseña.", ex
            );
        } catch (IOException ex) {
            throw new StatementExtractionException(
                    "No se pudo leer el archivo PDF del extracto.", ex
            );
        }
    }
}
