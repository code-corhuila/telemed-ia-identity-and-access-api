package com.telemed.identityaccess.infrastructure.web;

import com.telemed.identityaccess.application.exception.RegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationException(
            RegistrationException exception
    ) {

        HttpStatus status = switch (exception.reason()) {
            case EMAIL_ALREADY_REGISTERED,
                 IDENTITY_DOCUMENT_ALREADY_REGISTERED -> HttpStatus.CONFLICT;

            case INVALID_REGISTRATION -> HttpStatus.BAD_REQUEST;
        };

        ApiErrorResponse response = new ApiErrorResponse(
                exception.reason().name(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fieldErrors.putIfAbsent(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_REQUEST",
                "Request validation failed.",
                fieldErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}