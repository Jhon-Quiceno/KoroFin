package com.korofin.backend.exception;

import com.korofin.backend.exception.ai.AiMessageQuotaExceededException;
import com.korofin.backend.exception.ai.AiProviderAuthException;
import com.korofin.backend.exception.ai.AiProviderException;
import com.korofin.backend.exception.ai.AiProviderModelNotFoundException;
import com.korofin.backend.exception.ai.AiProviderNotConfiguredException;
import com.korofin.backend.exception.ai.AiProviderRateLimitException;
import com.korofin.backend.exception.ai.AiProviderTimeoutException;
import com.korofin.backend.exception.ai.AiProviderUnavailableException;
import com.korofin.backend.exception.ai.AiProvidersExhaustedException;
import com.korofin.backend.exception.card.CardPaymentExceedsBalanceException;
import com.korofin.backend.exception.card.CardPurchaseOverLimitException;
import com.korofin.backend.exception.card.InstallmentAmountTooLowException;
import com.korofin.backend.exception.debt.DebtPaymentExceedsBalanceException;
import com.korofin.backend.exception.expense.DuplicateCategoryException;
import com.korofin.backend.exception.statement.EmptyStatementTextException;
import com.korofin.backend.exception.statement.StatementExtractionException;
import com.korofin.backend.exception.statement.StatementPasswordException;
import com.korofin.backend.exception.statement.UnsupportedStatementFileException;
import com.korofin.backend.exception.user.EmailAlreadyExistsException;
import com.korofin.backend.exception.user.InvalidCredentialsException;
import com.korofin.backend.exception.user.InvalidRefreshTokenException;
import com.korofin.backend.service.ai.provider.AiChatOrchestrator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Manejador global de excepciones. Traduce las excepciones de dominio y de framework a
 * {@link ErrorResponse} con el código HTTP correspondiente. Arrancó cubriendo solo lo que
 * necesitaba el dominio {@code user} (fase 1) más los casos genéricos transversales; los dominios
 * siguientes ({@code expense}/{@code income} en fase 2, {@code debt}/{@code card} en fase 3,
 * {@code ai} en fase 4, y los que vengan después) agregan sus propios {@code @ExceptionHandler}
 * acá a medida que se implementan.
 *
 * <p>Las excepciones de "no encontrado" propias de un dominio ({@code DebtNotFoundException},
 * {@code CreditCardNotFoundException}, {@code InstallmentPlanNotFoundException}) extienden
 * {@link ResourceNotFoundException} y por eso no necesitan handler propio: el handler de 404 ya
 * las cubre. Existen como tipos separados para que el sitio que las lanza documente qué recurso
 * faltó, no para cambiar el código HTTP.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateCategory(
            DuplicateCategoryException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Reglas de negocio de los dominios {@code debt} y {@code card} que el cliente violó con un
     * request por lo demás bien formado: abono mayor al saldo de la deuda, compra que supera el
     * cupo de la tarjeta, pago mayor al saldo de la tarjeta, o compra diferida cuyo monto es
     * demasiado bajo para la cantidad de cuotas. Todas son {@code 400}, no {@code 409}: no hay un
     * conflicto de estado que el cliente pueda resolver reintentando, el dato enviado no es
     * aceptable.
     */
    @ExceptionHandler({
            DebtPaymentExceedsBalanceException.class,
            CardPurchaseOverLimitException.class,
            CardPaymentExceedsBalanceException.class,
            InstallmentAmountTooLowException.class
    })
    public ResponseEntity<ErrorResponse> handleLedgerBusinessRule(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * El archivo de extracto subido no tiene un formato soportado (o está vacío). Es un error del
     * cliente, no una falla del proceso de extracción en sí — a diferencia de las tres excepciones
     * de más abajo, que sí son {@code 422}.
     */
    @ExceptionHandler(UnsupportedStatementFileException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedStatementFile(
            UnsupportedStatementFileException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Dominio {@code statement}: el archivo de extracto es válido y del formato correcto, pero su
     * contenido no se pudo procesar (sin texto legible, PDF con contraseña incorrecta, o la IA no
     * devolvió movimientos interpretables). Las tres comparten {@code 422 Unprocessable Entity}:
     * el request en sí está bien formado, lo que falló es el contenido.
     */
    @ExceptionHandler({
            EmptyStatementTextException.class,
            StatementPasswordException.class,
            StatementExtractionException.class
    })
    public ResponseEntity<ErrorResponse> handleStatementUnprocessable(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Datos de entrada inválidos");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    /**
     * Un parámetro de la URL que no se puede convertir al tipo esperado: un {@code ?type=} que no
     * es ningún valor del enum, un {@code {id}} que no es numérico, etc. Es un error del cliente
     * ({@code 400}), no un fallo del servidor — sin este handler caería en el {@code 500}
     * genérico.
     *
     * <p>El mensaje nombra el parámetro pero nunca los valores válidos: eso lo documenta OpenAPI,
     * y devolverlos acá haría el mensaje dependiente del enum interno.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String message = "Valor inválido para el parámetro '" + ex.getName() + "'";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido o mal formado", request.getRequestURI());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({
            ResourceNotFoundException.class,
            EntityNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Ambas excepciones son fallas terminales de {@link AiChatOrchestrator#complete}: o no hay
     * ningún proveedor configurado, o todos los configurados fallaron. Ninguna lleva nunca detalle
     * específico de proveedor, así que ambas se mapean a la misma respuesta 503 con exactamente el
     * mismo mensaje genérico, sin importar el mensaje propio de la excepción — esto hace imposible
     * filtrar por acá qué proveedor falló o por qué.
     */
    @ExceptionHandler({
            AiProviderNotConfiguredException.class,
            AiProvidersExhaustedException.class
    })
    public ResponseEntity<ErrorResponse> handleAiUnavailable(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, AiChatOrchestrator.GENERIC_MESSAGE, request.getRequestURI());
    }

    @ExceptionHandler({
            AiProviderAuthException.class,
            AiProviderModelNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleAiProviderConfigurationError(
            AiProviderException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AiProviderRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderRateLimit(
            AiProviderRateLimitException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AiMessageQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleAiMessageQuotaExceeded(
            AiMessageQuotaExceededException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({
            AiProviderTimeoutException.class,
            AiProviderUnavailableException.class
    })
    public ResponseEntity<ErrorResponse> handleAiProviderUnavailable(
            AiProviderException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Respaldo para cualquier {@link AiProviderException} no cubierta por un handler más
     * específico arriba (defensivo — cada subtipo concreto que lanza hoy {@code AiChatClient} ya
     * está manejado explícitamente).
     */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderGenericError(
            AiProviderException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Verbo HTTP no soportado por la ruta pedida (por ejemplo un {@code PUT} contra un ledger
     * inmutable como {@code /api/debts/{id}/payments} o {@code /api/cards/{id}/movements}, que
     * solo aceptan {@code GET} y {@code POST}).
     *
     * <p>Hace falta declararlo explícitamente porque este {@code @RestControllerAdvice} no extiende
     * {@code ResponseEntityExceptionHandler}: sin este handler, el {@code @ExceptionHandler(Exception.class)}
     * genérico de más abajo se tragaba la excepción y devolvía un {@code 500}, haciendo pasar un
     * error del cliente por una falla del servidor.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        String message = "Método " + ex.getMethod() + " no permitido para este recurso";
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, message, request.getRequestURI());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Recurso no encontrado", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        // Se loguea con stack trace: un 500 sin causa registrada es indepurable en producción.
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            String path
    ) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );

        return ResponseEntity.status(status).body(response);
    }
}
