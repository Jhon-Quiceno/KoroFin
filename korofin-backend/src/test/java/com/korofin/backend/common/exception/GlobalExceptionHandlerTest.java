package com.korofin.backend.common.exception;

import com.korofin.backend.expense.exception.DuplicateCategoryException;
import com.korofin.backend.integration.exception.TelegramChatNotLinkedException;
import com.korofin.backend.integration.exception.TelegramImplausibleMovementException;
import com.korofin.backend.integration.exception.TelegramInvalidLinkCodeException;
import com.korofin.backend.integration.exception.TelegramRateLimitExceededException;
import com.korofin.backend.user.exception.EmailAlreadyExistsException;
import com.korofin.backend.user.exception.InvalidCredentialsException;
import com.korofin.backend.user.exception.InvalidRefreshTokenException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

    @Test
    void handleEmailAlreadyExistsReturns409() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/register");

        ResponseEntity<ErrorResponse> response = handler.handleEmailAlreadyExists(
                new EmailAlreadyExistsException("correo ya registrado"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("correo ya registrado");
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().path()).isEqualTo("/api/users/register");
    }

    @Test
    void handleDuplicateCategoryReturns409() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/categories");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateCategory(
                new DuplicateCategoryException("ya existe una categoría con ese nombre y tipo"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("ya existe una categoría con ese nombre y tipo");
    }

    @Test
    void handleInvalidCredentialsReturns401() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/login");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(
                new InvalidCredentialsException("credenciales invalidas"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleInvalidRefreshTokenReturns401() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/refresh");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidRefreshToken(
                new InvalidRefreshTokenException("token invalido"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleNotFoundReturns404ForResourceNotFoundAndEntityNotFound() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/1");

        assertThat(handler.handleNotFound(new ResourceNotFoundException("no existe"), request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleNotFound(new EntityNotFoundException("no existe"), request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleAccessDeniedReturns403() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/profile");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("no autenticado"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleTelegramImplausibleMovementReturns422() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/integrations/telegram/expenses");

        ResponseEntity<ErrorResponse> response = handler.handleTelegramImplausibleMovement(
                new TelegramImplausibleMovementException("no pude identificar un monto"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("no pude identificar un monto");
    }

    @Test
    void handleTelegramInvalidLinkCodeReturns400() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/integrations/telegram/confirm-link");

        ResponseEntity<ErrorResponse> response = handler.handleTelegramInvalidLinkCode(
                new TelegramInvalidLinkCodeException("código inválido o expirado"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleTelegramRateLimitExceededReturns429() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/integrations/telegram/expenses");

        ResponseEntity<ErrorResponse> response = handler.handleTelegramRateLimitExceeded(
                new TelegramRateLimitExceededException("demasiados mensajes"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void handleNotFoundReturns404ForTelegramChatNotLinked() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/integrations/telegram/expenses");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new TelegramChatNotLinkedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleValidationExceptionReturns400WithFirstFieldError() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/register");
        MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = Mockito.mock(BindingResult.class);
        FieldError fieldError = new FieldError("registerRequest", "email", "El correo electrónico no es válido");
        Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);
        Mockito.when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("El correo electrónico no es válido");
    }

    @Test
    void handleGenericExceptionReturns500WithGenericMessage() {
        Mockito.when(request.getRequestURI()).thenReturn("/api/users/register");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
    }
}
