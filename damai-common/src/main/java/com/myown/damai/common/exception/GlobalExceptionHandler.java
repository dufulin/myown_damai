package com.myown.damai.common.exception;

import com.myown.damai.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts backend exceptions to stable API responses and records diagnostic logs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles expected business exceptions.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        LOGGER.warn(
                "business exception handled, code={}, status={}, message={}",
                exception.getCode(),
                exception.getStatus().value(),
                exception.getMessage()
        );
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.failed(exception.getCode(), exception.getMessage()));
    }

    /**
     * Handles request body validation exceptions.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        LOGGER.warn("request validation failed, message={}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failed("VALIDATION_FAILED", message));
    }

    /**
     * Handles request parameter validation exceptions.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        LOGGER.warn("constraint validation failed, message={}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failed("VALIDATION_FAILED", exception.getMessage()));
    }

    /**
     * Handles unexpected exceptions without exposing internal details to API callers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("unexpected server exception handled", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failed("INTERNAL_SERVER_ERROR", "internal server error"));
    }
}
