package com.training.fraudcase.api;

import com.training.fraudcase.api.dto.ApiError;
import com.training.fraudcase.application.CaseNotFoundException;
import com.training.fraudcase.application.IntakeValidationException;
import com.training.fraudcase.domain.CaseStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<ApiError> notFound(CaseNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("not_found", e.getMessage()));
    }

    @ExceptionHandler(IntakeValidationException.class)
    public ResponseEntity<ApiError> intakeInvalid(IntakeValidationException e) {
        return ResponseEntity.badRequest().body(new ApiError("intake_invalid", e.problems(), java.time.Instant.now()));
    }

    @ExceptionHandler(CaseStateException.class)
    public ResponseEntity<ApiError> badState(CaseStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("illegal_state", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getAllErrors().stream()
                .map(err -> err instanceof FieldError fe ? fe.getField() + ": " + fe.getDefaultMessage()
                        : err.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", details, java.time.Instant.now()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of("not_found", "Resource not found"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException e) {
        log.warn("data integrity violation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError.of("data_conflict", "The request conflicts with stored case data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError(Exception e) {
        log.error("unhandled exception: {}", e.toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiError.of("internal_error", "Unexpected processing error"));
    }
}
