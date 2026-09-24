package com.telemed.identityaccess.infrastructure.web;

import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class RegisterPatientController {

    private final RegisterPatientUseCase registerPatientUseCase;

    public RegisterPatientController(
            RegisterPatientUseCase registerPatientUseCase
    ) {
        this.registerPatientUseCase = registerPatientUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterPatientResponse> register(
            @Valid @RequestBody RegisterPatientRequest request
    ) {

        var command = new RegisterPatientUseCase.Command(
                request.fullName(),
                request.email(),
                request.identityDocument(),
                request.password()
        );

        var result = registerPatientUseCase.register(command);

       var response = RegisterPatientResponse.from(
                result.userId(),
                result.role()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}