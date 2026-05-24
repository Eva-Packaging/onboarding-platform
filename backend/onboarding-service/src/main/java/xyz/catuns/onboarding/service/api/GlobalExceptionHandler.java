package xyz.catuns.onboarding.service.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.catuns.onboarding.service.api.dto.ErrorResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(v ->
                details.put(v.getPropertyPath().toString(), v.getMessage()));
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Constraint violation", details);
    }

    @ExceptionHandler(Throwable.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Throwable ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", Map.of());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code,
                                                        String detail, Map<String, Object> details) {
        ErrorResponse error = ErrorResponse.of(status);
        error.setCode(code);
        error.setDetail(detail);
        error.setCorrelationId(resolveCorrelationId());
        if (!details.isEmpty()) {
            error.setProperty("details", details);
        }
        return ResponseEntity.status(status).body(error);
    }

    private UUID resolveCorrelationId() {
        String mdcValue = MDC.get(MDC_CORRELATION_ID);
        if (mdcValue != null) {
            try {
                return UUID.fromString(mdcValue);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}