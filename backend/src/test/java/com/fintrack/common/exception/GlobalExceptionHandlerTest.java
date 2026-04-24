package com.fintrack.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.LoginRateLimitException;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/things");
        request.setRequestURI("/api/v1/things");
        MDC.put("requestId", "req-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private ErrorResponse body(ResponseEntity<ErrorResponse> res) {
        return Objects.requireNonNull(res.getBody());
    }

    @Test
    void notFoundMapsTo404WithMessage() {
        ResponseEntity<ErrorResponse> res =
                handler.handleNotFound(new ResourceNotFoundException("Portfolio missing"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(res).error()).isEqualTo("Portfolio missing");
        assertThat(body(res).code()).isEqualTo("NOT_FOUND");
        assertThat(body(res).path()).isEqualTo("/api/v1/things");
        assertThat(body(res).requestId()).isEqualTo("req-123");
        assertThat(body(res).timestamp()).isNotNull();
    }

    @Test
    void businessRuleUsesCodeFromException() {
        ResponseEntity<ErrorResponse> res =
                handler.handleBusinessRule(
                        new BusinessRuleException("Cannot proceed", "ALLOCATION_SUM"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).code()).isEqualTo("ALLOCATION_SUM");
        assertThat(body(res).error()).isEqualTo("Cannot proceed");
    }

    @Test
    void validationAggregatesFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "target");
        binding.addError(new FieldError("target", "amount", "must be positive"));
        binding.addError(new FieldError("target", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ErrorResponse> res = handler.handleValidation(ex, request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body(res).error())
                .contains("amount: must be positive")
                .contains("name: must not be blank");
    }

    @Test
    void badCredentialsMapsTo401WithFixedMessage() {
        ResponseEntity<ErrorResponse> res =
                handler.handleBadCredentials(
                        new BadCredentialsException("internal: wrong pw"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(res).error()).isEqualTo("Invalid credentials");
        assertThat(body(res).code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginRateLimitMapsTo429() {
        ResponseEntity<ErrorResponse> res =
                handler.handleLoginRateLimit(
                        new LoginRateLimitException("Too many attempts"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body(res).code()).isEqualTo("LOGIN_RATE_LIMITED");
        assertThat(body(res).error()).isEqualTo("Too many attempts");
    }

    @Test
    void accessDeniedMapsTo403WithFixedMessage() {
        ResponseEntity<ErrorResponse> res =
                handler.handleAccessDenied(new AccessDeniedException("internal detail"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(res).error()).isEqualTo("Access denied");
        assertThat(body(res).code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void illegalArgumentMapsTo400WithMessage() {
        ResponseEntity<ErrorResponse> res =
                handler.handleIllegalArgument(new IllegalArgumentException("bad input"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).error()).isEqualTo("bad input");
        assertThat(body(res).code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void unhandledExceptionMapsTo500WithGenericMessage() {
        ResponseEntity<ErrorResponse> res =
                handler.handleGeneral(new RuntimeException("internal detail"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(res).error()).isEqualTo("Internal server error");
        assertThat(body(res).code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void missingMdcRequestIdProducesNullRequestId() {
        MDC.clear();

        ResponseEntity<ErrorResponse> res =
                handler.handleNotFound(new ResourceNotFoundException("x"), request);

        assertThat(body(res).requestId()).isNull();
    }
}
