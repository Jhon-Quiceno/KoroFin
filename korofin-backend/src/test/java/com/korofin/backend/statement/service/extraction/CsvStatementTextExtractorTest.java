package com.korofin.backend.statement.service.extraction;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvStatementTextExtractorTest {

    private final CsvStatementTextExtractor extractor = new CsvStatementTextExtractor();

    @Test
    void supportsReturnsTrueForCsvExtensionCaseInsensitive() {
        assertThat(extractor.supports("extracto.csv")).isTrue();
        assertThat(extractor.supports("EXTRACTO.CSV")).isTrue();
    }

    @Test
    void supportsReturnsFalseForOtherExtensionsOrNull() {
        assertThat(extractor.supports("extracto.pdf")).isFalse();
        assertThat(extractor.supports("extracto.xlsx")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractDecodesRealCsvBytesAsUtf8() {
        String csv = "fecha,descripcion,monto\n2026-06-05,Supermercado Éxito,-187500\n2026-06-06,Salario,4200000\n";
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        String extracted = extractor.extract(content, null);

        assertThat(extracted).isEqualTo(csv);
        assertThat(extracted).contains("Supermercado Éxito").contains("Salario");
    }

    @Test
    void extractIgnoresPasswordParameter() {
        byte[] content = "a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8);

        String extracted = extractor.extract(content, "some-password");

        assertThat(extracted).isEqualTo("a,b,c\n1,2,3\n");
    }
}
