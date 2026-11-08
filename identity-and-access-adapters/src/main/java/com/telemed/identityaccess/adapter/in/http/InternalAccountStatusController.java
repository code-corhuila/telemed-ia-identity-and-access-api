package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.ChangeProfessionalAccountStatusUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service only: the gateway does not route /internal. ServiceCallerFilter lets only the
 * services in security.account-status.clients (the workflow) through.
 */
@RestController
public class InternalAccountStatusController {

    private final ChangeProfessionalAccountStatusUseCase changeStatus;

    public InternalAccountStatusController(ChangeProfessionalAccountStatusUseCase changeStatus) {
        this.changeStatus = changeStatus;
    }

    public record AccountStatusRequest(
            @NotNull(message = "active is required.") Boolean active
    ) {
    }

    @PutMapping(InternalAccountsController.PATH + "/{id}/status")
    public ResponseEntity<Void> change(
            @PathVariable String id,
            @Valid @RequestBody AccountStatusRequest request
    ) {
        UUID accountId;
        try {
            accountId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryParametersException(Map.of("id", "id must be an account id (UUID)."));
        }
        changeStatus.change(accountId, request.active());
        return ResponseEntity.noContent().build();
    }
}
