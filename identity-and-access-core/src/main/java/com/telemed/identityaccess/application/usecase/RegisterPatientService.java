package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.User;


import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;

public class RegisterPatientService implements RegisterPatientUseCase {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;

    public RegisterPatientService(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Result register(Command command) {

        if (users.existsByEmail(command.email())) {
            throw new RegistrationException(
                    EMAIL_ALREADY_REGISTERED,
                    "Email is already registered."
            );
        }

        if (users.existsByIdentityDocument(command.identityDocument())) {
            throw new RegistrationException(
                    IDENTITY_DOCUMENT_ALREADY_REGISTERED,
                    "Identity document is already registered."
            );
        }

        String passwordHash =
                passwordHasher.hash(command.password());

        User user = User.registerPatient(
                command.fullName(),
                command.email(),
                command.identityDocument(),
                passwordHash
        );

        User savedUser = users.save(user);

        return new Result(
                savedUser.id(),
                savedUser.role()
        );
    }
}