package com.training.fraudcase.api;

import com.training.fraudcase.api.dto.ApiError;
import com.training.fraudcase.application.CaseNotFoundException;
import com.training.fraudcase.application.IntakeValidationException;
import com.training.fraudcase.domain.CaseStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

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
}
