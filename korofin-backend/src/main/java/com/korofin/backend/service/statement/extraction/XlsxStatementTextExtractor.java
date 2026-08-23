package com.korofin.backend.service.statement.extraction;

import com.korofin.backend.exception.statement.StatementExtractionException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Extrae el texto de extractos bancarios exportados en formato XLSX (Excel), volcando cada hoja a
 * texto separado por tabulaciones: una fila del extracto se convierte en una línea con las celdas
 * separadas por {@code \t}, lo bastante estructurado para que el modelo de IA distinga columnas
 * sin necesitar el formato original de la planilla.
 *
 * <p>Usa {@link DataFormatter} para leer cada celda como el modelo de datos "vería" el valor
 * formateado (fechas, montos con separador de miles, etc.) en lugar del valor crudo subyacente.
 */
@Component
public class XlsxStatementTextExtractor implements StatementTextExtractor {

    private static final String EXTENSION = ".xlsx";

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    @Override
    public String extract(byte[] content, String password) {
        DataFormatter dataFormatter = new DataFormatter();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                appendSheet(workbook.getSheetAt(sheetIndex), dataFormatter, text);
            }
            return text.toString();
        } catch (IOException | RuntimeException ex) {
            throw new StatementExtractionException("No se pudo leer el archivo XLSX del extracto.", ex);
        }
    }

    private static void appendSheet(Sheet sheet, DataFormatter dataFormatter, StringBuilder text) {
        for (Row row : sheet) {
            appendRow(row, dataFormatter, text);
            text.append('\n');
        }
    }

    private static void appendRow(Row row, DataFormatter dataFormatter, StringBuilder text) {
        boolean firstCell = true;
        for (Cell cell : row) {
            if (!firstCell) {
                text.append('\t');
            }
            text.append(dataFormatter.formatCellValue(cell));
            firstCell = false;
        }
    }
}
