package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.IssueServiceTokenUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Service-to-service only. Kept out of /api/v1/auth on purpose: the gateway publishes
 * that prefix and does not route /internal, so secrets cannot be guessed from outside.
 */
@RestController
public class ServiceTokenController {

    public static final String PATH = "/internal/service-token";

    private final IssueServiceTokenUseCase issueServiceToken;

    public ServiceTokenController(IssueServiceTokenUseCase issueServiceToken) {
        this.issueServiceToken = issueServiceToken;
    }

    public record ServiceTokenRequest(
            @NotBlank(message = "Client id is required.") String clientId,
            @NotBlank(message = "Client secret is required.") String clientSecret
    ) {
        /** The secret never reaches a log line. */
        @Override
        public String toString() {
            return "ServiceTokenRequest[clientId=" + clientId + "]";
        }
    }

    public record ServiceTokenResponse(String accessToken, String tokenType, Instant expiresAt) {
    }

    @PostMapping(PATH)
    public ResponseEntity<ServiceTokenResponse> issue(@Valid @RequestBody ServiceTokenRequest request) {
        IssueServiceTokenUseCase.Result result = issueServiceToken.issue(request.clientId(), request.clientSecret());
        return ResponseEntity.ok(new ServiceTokenResponse(result.accessToken(), "Bearer", result.expiresAt()));
    }
}
