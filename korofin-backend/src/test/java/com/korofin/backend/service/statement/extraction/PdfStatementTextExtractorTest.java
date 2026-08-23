package com.korofin.backend.service.statement.extraction;

import com.korofin.backend.exception.statement.StatementExtractionException;
import com.korofin.backend.exception.statement.StatementPasswordException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfStatementTextExtractorTest {

    private final PdfStatementTextExtractor extractor = new PdfStatementTextExtractor();

    @Test
    void supportsReturnsTrueOnlyForPdfExtensionCaseInsensitive() {
        assertThat(extractor.supports("extracto.pdf")).isTrue();
        assertThat(extractor.supports("EXTRACTO.PDF")).isTrue();
        assertThat(extractor.supports("extracto.csv")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractReadsRealTextFromUnprotectedPdf() throws IOException {
        byte[] pdfBytes = buildPdf("2026-06-05 Supermercado Exito -187500", null);

        String extracted = extractor.extract(pdfBytes, null);

        assertThat(extracted).contains("2026-06-05 Supermercado Exito -187500");
    }

    @Test
    void extractReadsRealTextFromPasswordProtectedPdfWithCorrectPassword() throws IOException {
        byte[] pdfBytes = buildPdf("2026-06-06 Salario 4200000", "correcta123");

        String extracted = extractor.extract(pdfBytes, "correcta123");

        assertThat(extracted).contains("2026-06-06 Salario 4200000");
    }

    @Test
    void extractThrowsStatementPasswordExceptionWhenPasswordIsWrong() throws IOException {
        byte[] pdfBytes = buildPdf("texto protegido", "correcta123");

        assertThatThrownBy(() -> extractor.extract(pdfBytes, "incorrecta"))
                .isInstanceOf(StatementPasswordException.class);
    }

    @Test
    void extractThrowsStatementPasswordExceptionWhenPasswordIsMissing() throws IOException {
        byte[] pdfBytes = buildPdf("texto protegido", "correcta123");

        assertThatThrownBy(() -> extractor.extract(pdfBytes, null))
                .isInstanceOf(StatementPasswordException.class);
    }

    @Test
    void extractThrowsStatementExtractionExceptionForCorruptedBytes() {
        byte[] garbage = "esto no es un pdf valido".getBytes();

        assertThatThrownBy(() -> extractor.extract(garbage, null))
                .isInstanceOf(StatementExtractionException.class);
    }

    private static byte[] buildPdf(String text, String password) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }

            if (password != null) {
                AccessPermission accessPermission = new AccessPermission();
                StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, accessPermission);
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
