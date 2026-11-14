package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.PasswordRecoveryUseCase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthRoutes.BASE)
public class PasswordRecoveryController {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryController.class);

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

        // Neither the e-mail nor whether it is registered: the answer is the same for both.
        LOG.atInfo()
                .addKeyValue("event", "password_recovery.requested")
                .log("password recovery requested");

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        PasswordRecoveryResponse.generic()
                );
    }
}