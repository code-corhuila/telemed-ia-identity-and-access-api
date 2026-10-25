package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only, like /internal/service-token: the gateway does not route /internal.
 * ServiceCallerFilter lets only the services in security.account-provisioning.clients through.
 */
@RestController
public class InternalAccountsController {

    public static final String PATH = "/internal/accounts";

    private final ProvisionProfessionalAccountUseCase provisionAccount;

    public InternalAccountsController(
            ProvisionProfessionalAccountUseCase provisionAccount
    ) {
        this.provisionAccount = provisionAccount;
    }

    public record ProvisionAccountRequest(
            @NotBlank(message = "Full name is required.")
            @Size(max = 150, message = "Full name must not exceed 150 characters.")
            String fullName,

            @NotBlank(message = "Email is required.")
            @Email(message = "Email format is invalid.")
            @Size(max = 180, message = "Email must not exceed 180 characters.")
            String email,

            @NotBlank(message = "Identity document is required.")
            @Size(max = 50, message = "Identity document must not exceed 50 characters.")
            String identityDocument,

            @NotBlank(message = "Password is required.")
            String password,

            @NotBlank(message = "Role is required.")
            @Pattern(regexp = "PROFESSIONAL", message = "Only PROFESSIONAL accounts can be provisioned.")
            String role
    ) {
        @Override
        public String toString() {
            return "ProvisionAccountRequest[fullName=[REDACTED], email=[REDACTED], "
                    + "identityDocument=[REDACTED], password=[REDACTED], role=" + role + "]";
        }
    }

    public record ProvisionAccountResponse(UUID id) {
    }

    @PostMapping(PATH)
    public ResponseEntity<ProvisionAccountResponse> provision(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProvisionAccountRequest request
    ) {
        var result = provisionAccount.provision(new ProvisionProfessionalAccountUseCase.Command(
                idempotencyKey,
                request.fullName(),
                request.email(),
                request.identityDocument(),
                request.password()
        ));

        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new ProvisionAccountResponse(result.userId()));
    }
}
