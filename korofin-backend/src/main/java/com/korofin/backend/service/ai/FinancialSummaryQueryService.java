package com.korofin.backend.service.ai;

import com.korofin.backend.dto.ai.SummaryPeriod;
import com.korofin.backend.dto.ai.SummaryQueryIntent;
import com.korofin.backend.dto.ai.SummaryTopic;
import com.korofin.backend.entity.ai.AiUsageEventType;
import com.korofin.backend.entity.expense.CategoryType;
import com.korofin.backend.service.ai.provider.AiCallContext;
import com.korofin.backend.service.ai.provider.AiChatOrchestrator;
import com.korofin.backend.service.ai.provider.ChatCompletionResult;
import com.korofin.backend.service.ai.provider.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;

/**
 * Extrae el período, tipo de movimiento y categoría a los que se refiere una pregunta de resumen
 * financiero en texto libre (p. ej. {@code "¿Cuánto gasté en comida este mes?"}), con una sola
 * llamada de IA.
 *
 * <p>El plan documenta que esta responsabilidad también la usará la integración de Telegram en
 * una fase posterior — se implementa acá, en el dominio {@code ai}, tal como lo pide
 * {@code docs/backend-plan.md} sección 12, sin ningún acoplamiento hacia ese dominio futuro (esta
 * clase no depende de nada de {@code integration}).
 *
 * <p>Sigue el mismo patrón de JSON estricto, parseado a la defensiva, que
 * {@link AiCategorizationService#classifyMovement}. A diferencia de ese método,
 * {@link #parseQuery} nunca deja propagar NINGUNA falla — ni siquiera una falla de
 * proveedor/configuración de {@link AiChatOrchestrator#complete} — y en cambio degrada a
 * {@link #DEFAULT_INTENT} ({@link SummaryPeriod#MONTH}, sin filtro de tipo de movimiento, sin
 * categoría). Un resumen es una respuesta de solo lectura, así que caer a "este mes, todo" cuando
 * el proveedor de IA no está disponible es simplemente una respuesta un poco menos precisa, no un
 * riesgo de integridad de datos.
 */
@Service
public class FinancialSummaryQueryService {

    private static final Logger log = LoggerFactory.getLogger(FinancialSummaryQueryService.class);

    private static final SummaryQueryIntent DEFAULT_INTENT =
            new SummaryQueryIntent(SummaryPeriod.MONTH, null, null, SummaryTopic.MOVEMENT);

    private static final String INSTRUCTION =
            "Sos un asistente que interpreta preguntas en lenguaje natural sobre finanzas personales. A partir de "
                    + "una pregunta de texto libre, extraé el período, el tema, el tipo de movimiento y la "
                    + "categoría a la que se refiere. Responde EXCLUSIVAMENTE con un objeto JSON, sin bloques de "
                    + "código markdown ni texto adicional, con esta forma exacta: {\"period\":\"TODAY\"|\"WEEK\"|"
                    + "\"MONTH\"|\"LAST_MONTH\"|\"YEAR\",\"topic\":\"MOVEMENT\"|\"DEBT\","
                    + "\"movementType\":\"EXPENSE\"|\"INCOME\"|null,\"categoryName\":\"texto\"|null}. Usá "
                    + "\"TODAY\" solo si la pregunta menciona explícitamente hoy, \"WEEK\" solo si menciona "
                    + "explícitamente esta semana, \"LAST_MONTH\" solo si menciona explícitamente el mes pasado, "
                    + "\"YEAR\" solo si menciona explícitamente este año o en el año, y \"MONTH\" en cualquier "
                    + "otro caso, incluido cuando no se menciona ningún período. Usá \"DEBT\" para preguntas "
                    + "sobre deudas (ej. \"¿cómo voy con mis deudas?\", \"cuánto debo\"), y \"MOVEMENT\" para "
                    + "cualquier otra pregunta sobre gastos, ingresos o balance. Usá \"EXPENSE\" para preguntas "
                    + "sobre gastos, \"INCOME\" para preguntas sobre ingresos, y null para preguntas de balance, "
                    + "resumen general, sobre deudas, o cualquier otra que no distinga entre ambos. El campo "
                    + "categoryName debe contener el nombre de la categoría mencionada (por ejemplo, \"comida\", "
                    + "\"transporte\") EXCLUSIVAMENTE si la pregunta nombra una categoría específica; en caso "
                    + "contrario respondé null. No inventes una categoría que no esté explícita en la pregunta.";

    private final AiChatOrchestrator aiChatOrchestrator;
    private final ObjectMapper objectMapper;

    public FinancialSummaryQueryService(
            AiChatOrchestrator aiChatOrchestrator,
            ObjectMapper objectMapper
    ) {
        this.aiChatOrchestrator = aiChatOrchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * @param userId el usuario que hace la pregunta, usado solo para atribuir el rastreo de uso
     *               de IA
     * @param text   la pregunta de texto libre (p. ej. {@code "¿Cuánto gasté en comida este mes?"})
     * @return el período/tipo de movimiento/categoría decididos, o {@link #DEFAULT_INTENT} cuando
     *         el proveedor de IA falla o su respuesta no se pudo interpretar — nunca lanza
     */
    public SummaryQueryIntent parseQuery(Long userId, String text) {
        try {
            List<ChatMessage> messages = List.of(
                    ChatMessage.system(INSTRUCTION),
                    ChatMessage.user(text)
            );
            ChatCompletionResult result = aiChatOrchestrator.complete(
                    messages, new AiCallContext(userId, AiUsageEventType.CATEGORIZE)
            );
            return parseIntent(result.content(), userId);
        } catch (RuntimeException ex) {
            log.warn("ai_summary_query_failed userId={}", userId, ex);
            return DEFAULT_INTENT;
        }
    }

    /**
     * Parsea la respuesta JSON cruda del modelo a un {@link SummaryQueryIntent}, degradando a
     * {@link #DEFAULT_INTENT} ante JSON mal formado (nunca lanza) — los campos {@code period} y
     * {@code movementType} además se defaulean/descartan individualmente cuando no se reconocen,
     * en vez de fallar toda la respuesta por un solo campo malo.
     */
    private SummaryQueryIntent parseIntent(String rawResponse, Long userId) {
        RawIntent raw;
        try {
            String json = extractJsonObject(rawResponse);
            raw = objectMapper.readValue(json, RawIntent.class);
        } catch (JacksonException | IllegalArgumentException ex) {
            log.warn("ai_summary_query_parse_failed userId={}", userId);
            return DEFAULT_INTENT;
        }

        SummaryPeriod period = parsePeriod(raw.period());
        CategoryType movementType = parseMovementType(raw.movementType());
        String categoryName = raw.categoryName() == null || raw.categoryName().isBlank() ? null : raw.categoryName().trim();
        SummaryTopic topic = parseTopic(raw.topic());
        return new SummaryQueryIntent(period, movementType, categoryName, topic);
    }

    /** Cualquier valor no reconocido, incluido {@code null}, cae al default documentado en la clase: {@code MONTH}. */
    private static SummaryPeriod parsePeriod(String rawPeriod) {
        try {
            return SummaryPeriod.valueOf((rawPeriod == null ? "" : rawPeriod.trim()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SummaryPeriod.MONTH;
        }
    }

    /** Cualquier valor no reconocido, incluido {@code null}, cae al default documentado en la clase: {@code MOVEMENT}. */
    private static SummaryTopic parseTopic(String rawTopic) {
        try {
            return SummaryTopic.valueOf((rawTopic == null ? "" : rawTopic.trim()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SummaryTopic.MOVEMENT;
        }
    }

    /** Cualquier valor no reconocido, incluido {@code null}, cae a {@code null} (balance/ambos tipos). */
    private static CategoryType parseMovementType(String rawMovementType) {
        if (rawMovementType == null || rawMovementType.isBlank()) {
            return null;
        }
        try {
            return CategoryType.valueOf(rawMovementType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Limpia la respuesta cruda del modelo: recorta espacios, quita un posible bloque de código
     * markdown y se queda con la subcadena entre el primer {@code {} y el último {@code }}.
     *
     * @throws IllegalArgumentException si no se encontró un objeto JSON en la respuesta
     */
    private static String extractJsonObject(String rawResponse) {
        if (rawResponse == null) {
            throw new IllegalArgumentException("La respuesta del modelo está vacía");
        }
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("json")) {
                trimmed = trimmed.substring(4);
            }
            int closingFence = trimmed.lastIndexOf("```");
            if (closingFence >= 0) {
                trimmed = trimmed.substring(0, closingFence);
            }
            trimmed = trimmed.trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("No se encontró un objeto JSON en la respuesta del modelo");
        }
        return trimmed.substring(start, end + 1);
    }

    /** Forma cruda de la respuesta del modelo, antes de validar/normalizar sus campos. */
    private record RawIntent(String period, String movementType, String categoryName, String topic) {
    }
}
