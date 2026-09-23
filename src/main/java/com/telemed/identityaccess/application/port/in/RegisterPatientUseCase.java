package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.domain.model.Role;

public interface RegisterPatientUseCase {

    Result register(Command command);

    record Command(
            String fullName,
            String email,
            String identityDocument,
            String password
    ) {
    }

    record Result(
            Long userId,
            Role role
    ) {
    }
}