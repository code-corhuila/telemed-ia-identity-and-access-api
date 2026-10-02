package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.telemed.identityaccess.application.exception.RefreshSessionException;
import com.telemed.identityaccess.application.exception.LogoutException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationException(
            RegistrationException exception,
            HttpServletRequest request
    ) {

        String traceId = getTraceId(request);

        if (exception.reason()
                == RegistrationException.Reason.INVALID_REGISTRATION) {

            ApiErrorResponse response = ApiErrorResponse.of(
                    "INVALID_REGISTRATION",
                    "Registration data is invalid.",
                    Map.of(),
                    traceId
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        ApiErrorResponse response = ApiErrorResponse.of(
                "REGISTRATION_CONFLICT",
                "Registration cannot be completed with the provided data.",
                Map.of(),
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request
    ) {

        String traceId = getTraceId(request);

        return switch (exception.reason()) {

            case INVALID_CREDENTIALS -> {

                log.warn(
                        "Authentication attempt rejected. reason={}",
                        exception.reason()
                );

                ApiErrorResponse response = ApiErrorResponse.of(
                        "INVALID_CREDENTIALS",
                        "Invalid email or password.",
                        Map.of(),
                        traceId
                );

                yield ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(response);
            }
        };
    }

    @ExceptionHandler(RefreshSessionException.class)
public ResponseEntity<ApiErrorResponse> handleRefreshSessionException(
        RefreshSessionException exception,
        HttpServletRequest request
) {

    ApiErrorResponse response = ApiErrorResponse.of(
            "INVALID_REFRESH_TOKEN",
            "Refresh token is invalid or expired.",
            Map.of(),
            getTraceId(request)
    );

    return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(response);
}

@ExceptionHandler(LogoutException.class)
public ResponseEntity<ApiErrorResponse> handleLogoutException(
        LogoutException exception,
        HttpServletRequest request
) {

    ApiErrorResponse response = ApiErrorResponse.of(
            "INVALID_REFRESH_TOKEN",
            "Refresh token is invalid.",
            Map.of(),
            getTraceId(request)
    );

    return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(response);
}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
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

        ApiErrorResponse response = ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "Request validation failed.",
                fieldErrors,
                getTraceId(request)
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MissingAuthenticatedPrincipalException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingAuthenticatedPrincipal(
            MissingAuthenticatedPrincipalException exception,
            HttpServletRequest request
    ) {

        log.warn(
                "Authenticated request reached controller without principal."
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                "UNAUTHORIZED",
                "Authentication is required.",
                Map.of(),
                getTraceId(request)
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {

        log.error(
                "Unexpected error while processing request.",
                exception
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                Map.of(),
                getTraceId(request)
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    private String getTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(
                CorrelationContext.REQUEST_ATTRIBUTE
        );

        return traceId instanceof String
                ? (String) traceId
                : null;
    }
}