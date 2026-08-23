package com.korofin.backend.controller.report;

import com.korofin.backend.dto.report.MonthlyReportResponse;
import com.korofin.backend.dto.report.MovementResponse;
import com.korofin.backend.service.report.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Endpoints REST de reportes del usuario actual.
 *
 * <p>{@link #export} escribe el CSV a mano (sin librería de terceros), replicando tal cual las dos
 * protecciones de seguridad del backend viejo (ver docs/backend-plan.md sección 7):
 * <ul>
 *   <li>Escapado RFC4180 estándar (comillas dobladas cuando el valor tiene coma/comilla/salto de línea).</li>
 *   <li><b>Neutralización de CSV injection (CWE-1236):</b> si un campo empieza con {@code =}, {@code +},
 *       {@code -} o {@code @}, se le antepone una comilla simple — de lo contrario Excel/Sheets
 *       podría interpretar el campo como una fórmula ejecutable al abrir el CSV exportado.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final List<String> CSV_HEADER = List.of("Fecha", "Tipo", "Descripción", "Categoría", "Monto");

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ReportController(ReportService reportService, ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReportResponse> getMonthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(reportService.getMonthlyReport(resolvePeriod(year, month)));
    }

    @GetMapping("/movements")
    public ResponseEntity<List<MovementResponse>> getMovements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(reportService.getMovements(from, to));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response
    ) throws IOException {
        List<MovementResponse> movements = reportService.getMovements(from, to);
        boolean isJson = "json".equalsIgnoreCase(format);

        response.setHeader("Content-Disposition", buildContentDisposition(isJson ? "json" : "csv", from, to));

        if (isJson) {
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), movements);
        } else {
            response.setContentType("text/csv;charset=UTF-8");
            writeCsv(movements, response.getWriter());
        }
    }

    private static YearMonth resolvePeriod(Integer year, Integer month) {
        if (year == null || month == null) {
            return YearMonth.now();
        }
        return YearMonth.of(year, month);
    }

    private static String buildContentDisposition(String extension, LocalDate from, LocalDate to) {
        String filename = String.format(Locale.ROOT, "korofin-movimientos-%s_a_%s.%s", from, to, extension);
        return "attachment; filename=\"" + filename + "\"";
    }

    private void writeCsv(List<MovementResponse> movements, Writer writer) throws IOException {
        writeCsvRow(writer, CSV_HEADER);
        for (MovementResponse movement : movements) {
            writeCsvRow(writer, List.of(
                    movement.date().toString(),
                    movement.type().name(),
                    movement.description() == null ? "" : movement.description(),
                    movement.categoryName(),
                    movement.amount().toPlainString()
            ));
        }
        writer.flush();
    }

    private void writeCsvRow(Writer writer, List<String> fields) throws IOException {
        writer.write(fields.stream().map(this::escapeCsvField).collect(Collectors.joining(",")));
        writer.write("\r\n");
    }

    /** Aplica primero la neutralización CWE-1236 y después el escapado RFC4180 estándar. */
    private String escapeCsvField(String rawValue) {
        String value = neutralizeFormulaInjection(rawValue == null ? "" : rawValue);
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Neutralización de CSV injection (CWE-1236): un campo que empieza con {@code =}, {@code +},
     * {@code -} o {@code @} se antepone con una comilla simple, para que Excel/Sheets lo trate
     * siempre como texto literal en vez de interpretarlo como el inicio de una fórmula ejecutable.
     */
    private static String neutralizeFormulaInjection(String value) {
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                return "'" + value;
            }
        }
        return value;
    }
}
