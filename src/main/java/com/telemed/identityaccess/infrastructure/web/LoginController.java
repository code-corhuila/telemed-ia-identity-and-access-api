package com.telemed.identityaccess.infrastructure.web;

import com.telemed.identityaccess.application.port.in.LoginUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final LoginUseCase loginUseCase;

    public LoginController(
            LoginUseCase loginUseCase
    ) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        LoginUseCase.Command command =
                new LoginUseCase.Command(
                        request.email(),
                        request.password()
                );

        LoginUseCase.Result result =
                loginUseCase.login(command);

        LoginResponse response =
                LoginResponse.from(result);

        return ResponseEntity.ok(response);
    }
}