package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LogoutUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthRoutes.BASE)
public class LogoutController {

    private final LogoutUseCase logoutUseCase;

    public LogoutController(
            LogoutUseCase logoutUseCase
    ) {
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping(AuthRoutes.LOGOUT)
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request
    ) {

        logoutUseCase.logout(
                request.refreshToken()
        );

        return ResponseEntity.noContent()
                .build();
    }
}