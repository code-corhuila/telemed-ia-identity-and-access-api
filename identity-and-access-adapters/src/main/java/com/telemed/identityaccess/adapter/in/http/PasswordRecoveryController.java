package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.PasswordRecoveryUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthRoutes.BASE)
public class PasswordRecoveryController {

    private final PasswordRecoveryUseCase passwordRecoveryUseCase;

    public PasswordRecoveryController(
            PasswordRecoveryUseCase passwordRecoveryUseCase
    ) {
        this.passwordRecoveryUseCase =
                passwordRecoveryUseCase;
    }

    @PostMapping(AuthRoutes.PASSWORD_RECOVERY)
    public ResponseEntity<PasswordRecoveryResponse> requestRecovery(
            @Valid @RequestBody PasswordRecoveryRequest request
    ) {

        passwordRecoveryUseCase.requestRecovery(
                request.email()
        );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        PasswordRecoveryResponse.generic()
                );
    }
}