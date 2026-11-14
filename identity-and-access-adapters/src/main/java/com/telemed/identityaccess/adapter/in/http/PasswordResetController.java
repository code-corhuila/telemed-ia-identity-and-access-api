package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.PasswordResetUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthRoutes.BASE)
public class PasswordResetController {

    private final PasswordResetUseCase passwordResetUseCase;

    public PasswordResetController(
            PasswordResetUseCase passwordResetUseCase
    ) {
        this.passwordResetUseCase = passwordResetUseCase;
    }

    @PostMapping(AuthRoutes.PASSWORD_RESET)
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {

        passwordResetUseCase.reset(
                request.token(),
                request.newPassword()
        );

        return ResponseEntity.noContent()
                .build();
    }
}