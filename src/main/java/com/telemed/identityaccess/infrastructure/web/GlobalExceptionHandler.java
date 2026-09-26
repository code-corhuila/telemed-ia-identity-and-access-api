package com.telemed.identityaccess.infrastructure.web;

import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationException(
            RegistrationException exception
    ) {

        if (exception.reason()
                == RegistrationException.Reason.INVALID_REGISTRATION) {

            ApiErrorResponse response = new ApiErrorResponse(
                    "INVALID_REGISTRATION",
                    "Registration data is invalid.",
                    Map.of()
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        ApiErrorResponse response = new ApiErrorResponse(
                "REGISTRATION_CONFLICT",
                "Registration cannot be completed with the provided data.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
                AuthenticationException exception
        ) {

        return switch (exception.reason()) {

                case INVALID_CREDENTIALS -> {

                log.warn(
                        "Authentication attempt rejected. reason={}",
                        exception.reason()
                );

                ApiErrorResponse response = new ApiErrorResponse(
                        "INVALID_CREDENTIALS",
                        "Invalid email or password.",
                        Map.of()
                );

                yield ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(response);
                }
        };
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception
    ) {

        log.error(
                "Unexpected error while processing request.",
                exception
        );

        ApiErrorResponse response = new ApiErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}