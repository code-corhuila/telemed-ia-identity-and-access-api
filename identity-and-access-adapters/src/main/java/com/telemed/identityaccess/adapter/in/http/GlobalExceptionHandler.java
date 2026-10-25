package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.telemed.identityaccess.application.exception.RefreshSessionException;
import com.telemed.identityaccess.application.exception.LogoutException;
import com.telemed.identityaccess.application.exception.PasswordResetException;
import com.telemed.identityaccess.application.exception.ServiceAuthenticationException;
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

    @ExceptionHandler(InvalidQueryParametersException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidQueryParameters(
            InvalidQueryParametersException exception,
            HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        "VALIDATION_ERROR",
                        "Request validation failed.",
                        exception.fieldErrors(),
                        getTraceId(request)
                ));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(
            HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        "NOT_FOUND",
                        "Account not found.",
                        Map.of(),
                        getTraceId(request)
                ));
    }

    @ExceptionHandler(AccountProvisioningException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountProvisioningException(
            AccountProvisioningException exception,
            HttpServletRequest request
    ) {

        if (exception.reason()
                == AccountProvisioningException.Reason.INVALID_IDEMPOTENCY_KEY) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiErrorResponse.of(
                            "VALIDATION_ERROR",
                            "Request validation failed.",
                            Map.of("Idempotency-Key", exception.getMessage()),
                            getTraceId(request)
                    ));
        }

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorResponse.of(
                        "IDEMPOTENCY_KEY_REUSED",
                        exception.getMessage(),
                        Map.of(),
                        getTraceId(request)
                ));
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

    @ExceptionHandler(ServiceAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceAuthenticationException(
            HttpServletRequest request
    ) {

        log.warn("Service token request rejected.");

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        "INVALID_CLIENT",
                        "Invalid service client credentials.",
                        Map.of(),
                        getTraceId(request)
                ));
    }

    @ExceptionHandler(PasswordResetException.class)
public ResponseEntity<ApiErrorResponse> handlePasswordResetException(
        PasswordResetException exception,
        HttpServletRequest request
) {

    String error = switch (exception.reason()) {
        case INVALID_RESET_TOKEN ->
                "INVALID_RESET_TOKEN";

        case INVALID_NEW_PASSWORD ->
                "INVALID_NEW_PASSWORD";
    };

    HttpStatus status = switch (exception.reason()) {
        case INVALID_RESET_TOKEN ->
                HttpStatus.UNAUTHORIZED;

        case INVALID_NEW_PASSWORD ->
                HttpStatus.BAD_REQUEST;
    };

    ApiErrorResponse response =
            ApiErrorResponse.of(
                    error,
                    exception.getMessage(),
                    Map.of(),
                    getTraceId(request)
            );

    return ResponseEntity
            .status(status)
            .body(response);
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

    // A body that is not valid JSON (or not the expected shape) is the caller's error, not ours.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpServletRequest request
    ) {

        ApiErrorResponse response = ApiErrorResponse.of(
                "VALIDATION_ERROR",
                "Malformed request body.",
                Map.of(),
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