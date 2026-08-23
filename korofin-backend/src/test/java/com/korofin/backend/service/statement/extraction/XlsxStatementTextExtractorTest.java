package com.korofin.backend.service.statement.extraction;

import com.korofin.backend.exception.statement.StatementExtractionException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxStatementTextExtractorTest {

    private final XlsxStatementTextExtractor extractor = new XlsxStatementTextExtractor();

    @Test
    void supportsReturnsTrueOnlyForXlsxExtensionCaseInsensitive() {
        assertThat(extractor.supports("extracto.xlsx")).isTrue();
        assertThat(extractor.supports("EXTRACTO.XLSX")).isTrue();
        assertThat(extractor.supports("extracto.csv")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractReadsRealWorkbookRowsAsTabSeparatedLines() throws IOException {
        byte[] xlsxBytes = buildWorkbook(
                new String[]{"Fecha", "Descripcion", "Monto"},
                new String[]{"2026-06-05", "Supermercado Éxito", "-187500"},
                new String[]{"2026-06-06", "Salario", "4200000"}
        );

        String extracted = extractor.extract(xlsxBytes, null);

        assertThat(extracted)
                .contains("Fecha\tDescripcion\tMonto")
                .contains("2026-06-05\tSupermercado Éxito\t-187500")
                .contains("2026-06-06\tSalario\t4200000");
    }

    @Test
    void extractThrowsStatementExtractionExceptionForCorruptedBytes() {
        byte[] garbage = "esto no es un xlsx valido".getBytes();

        assertThatThrownBy(() -> extractor.extract(garbage, null))
                .isInstanceOf(StatementExtractionException.class);
    }

    private static byte[] buildWorkbook(String[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Movimientos");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                String[] cells = rows[r];
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
