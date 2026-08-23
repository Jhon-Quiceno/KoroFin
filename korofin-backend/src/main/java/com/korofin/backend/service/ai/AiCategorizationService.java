package com.korofin.backend.service.ai;

import com.korofin.backend.dto.ai.CategorizeRequest;
import com.korofin.backend.dto.ai.CategorizeResponse;
import com.korofin.backend.dto.ai.MovementClassification;
import com.korofin.backend.entity.ai.AiUsageEventType;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.CategoryType;
import com.korofin.backend.repository.expense.CategoryRepository;
import com.korofin.backend.security.SecurityUtils;
import com.korofin.backend.service.ai.provider.AiCallContext;
import com.korofin.backend.service.ai.provider.AiChatOrchestrator;
import com.korofin.backend.service.ai.provider.ChatCompletionResult;
import com.korofin.backend.service.ai.provider.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lógica de negocio de {@code POST /api/ai/categorize}: le pide al proveedor de IA configurado
 * del usuario actual que elija la categoría que mejor corresponde, de la propia lista de
 * categorías del usuario del {@link CategoryType} pedido (ingreso o gasto), para una descripción
 * de texto libre.
 *
 * <p>El prompt restringe al modelo a responder con exactamente un nombre de categoría de la lista
 * provista, o el token literal {@value #NO_MATCH_TOKEN} cuando ninguna corresponde —
 * {@link #matchCategory} parsea esa respuesta de forma defensiva (recortada, sin distinguir
 * mayúsculas/minúsculas, tolerante a puntuación o texto adicional alrededor) ya que una respuesta
 * de texto libre del modelo nunca está garantizada al 100% de cumplir el formato pedido.
 *
 * <p>{@link #classifyMovement} es un punto de entrada separado para llamadores que no conocen de
 * antemano el tipo del movimiento: una sola llamada de IA decide tanto el tipo como sugiere una
 * categoría.
 */
@Service
public class AiCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(AiCategorizationService.class);

    private static final String NO_MATCH_TOKEN = "NINGUNA";

    private final CategoryRepository categoryRepository;
    private final AiChatOrchestrator aiChatOrchestrator;
    private final ObjectMapper objectMapper;

    public AiCategorizationService(
            CategoryRepository categoryRepository,
            AiChatOrchestrator aiChatOrchestrator,
            ObjectMapper objectMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.aiChatOrchestrator = aiChatOrchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * @return el id/nombre de la categoría encontrada, o ambos {@code null} cuando el usuario no
     *         tiene categorías del {@link CategorizeRequest#type()} pedido todavía o el asistente
     *         no encontró una coincidencia adecuada — nunca lanza por "sin coincidencia", solo por
     *         fallas de proveedor/configuración (ver {@link AiChatOrchestrator#complete})
     */
    @Transactional
    public CategorizeResponse categorize(CategorizeRequest request) {
        return categorize(SecurityUtils.getCurrentUserId(), request);
    }

    /**
     * Igual que {@link #categorize(CategorizeRequest)} pero para un llamador que ya resolvió
     * {@code userId} por su cuenta.
     */
    public CategorizeResponse categorize(Long userId, CategorizeRequest request) {
        List<Category> categories = categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(userId, request.type());
        if (categories.isEmpty()) {
            return new CategorizeResponse(null, null);
        }

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildInstruction(categories, request.type())),
                ChatMessage.user(buildUserPrompt(request))
        );
        ChatCompletionResult result = aiChatOrchestrator.complete(
                messages, new AiCallContext(userId, AiUsageEventType.CATEGORIZE)
        );

        return matchCategory(result.content(), categories)
                .map(category -> new CategorizeResponse(category.getId(), category.getName()))
                .orElseGet(() -> new CategorizeResponse(null, null));
    }

    /**
     * Decide, con una sola llamada de IA, si un movimiento de texto libre es un
     * {@link CategoryType#INCOME} o un {@link CategoryType#EXPENSE}, y (best-effort) cuál de las
     * categorías del usuario de ese tipo decidido corresponde mejor.
     *
     * <p>A diferencia de {@link #categorize(Long, CategorizeRequest)}, este método nunca lanza por
     * una respuesta mal formada del modelo: cualquier falla de parseo degrada a
     * {@code new MovementClassification(CategoryType.EXPENSE, null, null)}, logueando un
     * {@code WARN} con {@code userId} pero nunca el contenido del mensaje. Sigue dejando propagar
     * las fallas de proveedor/configuración de {@link AiChatOrchestrator#complete}, igual que
     * {@link #categorize(Long, CategorizeRequest)}, para que el llamador aplique su propia
     * política de fallback.
     *
     * @param userId      el usuario dueño del movimiento
     * @param description descripción de texto libre del movimiento
     * @param amount      el monto del movimiento
     * @return el tipo decidido y, si se encontró, el id/nombre de la categoría
     */
    public MovementClassification classifyMovement(Long userId, String description, BigDecimal amount) {
        List<Category> incomeCategories = categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(userId, CategoryType.INCOME);
        List<Category> expenseCategories = categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(userId, CategoryType.EXPENSE);

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildClassificationInstruction(incomeCategories, expenseCategories)),
                ChatMessage.user(buildClassificationUserPrompt(description, amount))
        );
        ChatCompletionResult result = aiChatOrchestrator.complete(
                messages, new AiCallContext(userId, AiUsageEventType.CATEGORIZE)
        );

        return parseClassification(result.content(), userId, incomeCategories, expenseCategories);
    }

    private static String buildClassificationInstruction(List<Category> incomeCategories, List<Category> expenseCategories) {
        String incomeNames = joinNames(incomeCategories);
        String expenseNames = joinNames(expenseCategories);
        return "Eres un clasificador de movimientos financieros personales. A partir de una descripción de texto "
                + "libre y un monto, debes decidir si el movimiento es un INGRESO (dinero que entra a la cuenta: "
                + "salario, pago recibido, reembolso, regalo, ingreso freelance, etc.) o un GASTO (dinero que sale: "
                + "compra, pago, servicio, etc.). Ante una frase ambigua sin verbo claro (ej. solo un nombre de "
                + "comercio), asumí GASTO por defecto. Responde EXCLUSIVAMENTE con un objeto JSON, sin bloques de "
                + "código markdown ni texto adicional, con esta forma exacta: "
                + "{\"movementType\":\"INCOME\"|\"EXPENSE\",\"categoryName\":\"texto\"|null}. El campo "
                + "categoryName debe ser EXCLUSIVAMENTE uno de los siguientes nombres, según el tipo de "
                + "movimiento que decidiste, o null si ninguno corresponde. Categorías de ingreso disponibles: "
                + incomeNames + ". Categorías de gasto disponibles: " + expenseNames + ".";
    }

    private static String buildClassificationUserPrompt(String description, BigDecimal amount) {
        return "Descripción: " + description + ". Monto: " + amount + ".";
    }

    private static String joinNames(List<Category> categories) {
        if (categories.isEmpty()) {
            return "ninguna";
        }
        return categories.stream().map(Category::getName).collect(Collectors.joining(", "));
    }

    /**
     * Parsea la respuesta JSON cruda del modelo a una {@link MovementClassification}, degradando
     * con gracia (ver {@link #classifyMovement}) ante cualquier JSON mal formado o un valor de
     * {@code movementType} no reconocido.
     */
    private MovementClassification parseClassification(
            String rawResponse,
            Long userId,
            List<Category> incomeCategories,
            List<Category> expenseCategories
    ) {
        RawClassification raw;
        try {
            String json = extractJsonObject(rawResponse);
            raw = objectMapper.readValue(json, RawClassification.class);
        } catch (JacksonException | IllegalArgumentException ex) {
            log.warn("ai_classify_movement_parse_failed userId={}", userId);
            return new MovementClassification(CategoryType.EXPENSE, null, null);
        }

        CategoryType type;
        try {
            type = CategoryType.valueOf(
                    (raw.movementType() == null ? "" : raw.movementType().trim()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("ai_classify_movement_type_invalid userId={}", userId);
            return new MovementClassification(CategoryType.EXPENSE, null, null);
        }

        List<Category> candidates = type == CategoryType.INCOME ? incomeCategories : expenseCategories;
        Category matched = matchCategoryByName(raw.categoryName(), candidates);
        return new MovementClassification(
                type,
                matched != null ? matched.getId() : null,
                matched != null ? matched.getName() : null
        );
    }

    /**
     * Resuelve un nombre de categoría contra una lista de candidatas, primero con coincidencia
     * exacta (sin distinguir mayúsculas/minúsculas), y si no hay, con el nombre candidato más
     * largo que aparezca como subcadena.
     */
    private static Category matchCategoryByName(String categoryName, List<Category> candidates) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        String normalized = categoryName.trim();

        Optional<Category> exactMatch = candidates.stream()
                .filter(category -> category.getName().equalsIgnoreCase(normalized))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        String lowerCaseName = normalized.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .sorted(Comparator.comparingInt((Category category) -> category.getName().length()).reversed())
                .filter(category -> lowerCaseName.contains(category.getName().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Limpia la respuesta cruda del modelo: recorta espacios, quita un posible bloque de código
     * markdown ({@code ```json ... ```} o {@code ``` ... ```}) y se queda con la subcadena entre
     * el primer {@code {} y el último {@code }}, tolerando texto extra alrededor del objeto JSON
     * aunque el prompt lo prohíba.
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

    /** Forma cruda de la respuesta de clasificación del modelo, antes de validar/resolver sus campos. */
    private record RawClassification(String movementType, String categoryName) {
    }

    private static String buildInstruction(List<Category> categories, CategoryType type) {
        String categoryList = categories.stream().map(Category::getName).collect(Collectors.joining(", "));
        String movementNoun = type == CategoryType.INCOME ? "un ingreso" : "un gasto";
        return "Eres un clasificador de movimientos financieros personales. Debes elegir la categoría que mejor "
                + "corresponde a la descripción de " + movementNoun + ", utilizando EXCLUSIVAMENTE una de las "
                + "siguientes categorías: " + categoryList + ". Responde ÚNICAMENTE con el nombre exacto de la "
                + "categoría elegida, sin texto adicional, comillas ni explicación. Si ninguna categoría "
                + "corresponde, responde exactamente \"" + NO_MATCH_TOKEN + "\".";
    }

    private static String buildUserPrompt(CategorizeRequest request) {
        String movementLabel = request.type() == CategoryType.INCOME ? "Descripción del ingreso" : "Descripción del gasto";
        StringBuilder prompt = new StringBuilder(movementLabel).append(": \"").append(request.description()).append('"');
        if (request.amount() != null) {
            prompt.append(". Monto: $").append(request.amount());
        }
        return prompt.toString();
    }

    private static Optional<Category> matchCategory(String rawResponse, List<Category> categories) {
        if (rawResponse == null) {
            return Optional.empty();
        }
        String normalized = stripQuotesAndPunctuation(rawResponse.trim());
        if (normalized.equalsIgnoreCase(NO_MATCH_TOKEN)) {
            return Optional.empty();
        }

        Optional<Category> exactMatch = categories.stream()
                .filter(category -> category.getName().equalsIgnoreCase(normalized))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        String lowerCaseResponse = rawResponse.toLowerCase(Locale.ROOT);
        return categories.stream()
                .sorted(Comparator.comparingInt((Category category) -> category.getName().length()).reversed())
                .filter(category -> lowerCaseResponse.contains(category.getName().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private static String stripQuotesAndPunctuation(String value) {
        return value.replaceAll("^[\"'.\\s]+|[\"'.\\s]+$", "");
    }
}
