package com.telemed.identityaccess.adapter.in.http;

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

    // A retry with the same key gets the same account back (200) instead of a duplicate-email 409.
    @PostMapping("/register")
    public ResponseEntity<RegisterPatientResponse> register(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RegisterPatientRequest request
    ) {

        var command = new RegisterPatientUseCase.Command(
                idempotencyKey,
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
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }
}