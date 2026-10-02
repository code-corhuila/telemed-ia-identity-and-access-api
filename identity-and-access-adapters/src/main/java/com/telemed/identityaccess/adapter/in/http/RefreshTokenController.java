package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.RefreshSessionUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthRoutes.BASE)
public class RefreshTokenController {

    private final RefreshSessionUseCase refreshSessionUseCase;

    public RefreshTokenController(
            RefreshSessionUseCase refreshSessionUseCase
    ) {
        this.refreshSessionUseCase = refreshSessionUseCase;
    }

    @PostMapping(AuthRoutes.REFRESH)
    public ResponseEntity<RefreshTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {

        RefreshSessionUseCase.Result result =
                refreshSessionUseCase.refresh(
                        request.refreshToken()
                );

        return ResponseEntity.ok(
                RefreshTokenResponse.from(result)
        );
    }
}