package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import java.util.Locale;

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

        String normalizedEmail = command.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String normalizedDocument = command.identityDocument().trim();

        if (users.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "Email is already registered."
            );
        }

        if (users.existsByIdentityDocument(normalizedDocument)) {
            throw new IllegalArgumentException(
                    "Identity document is already registered."
            );
        }

        String passwordHash =
                passwordHasher.hash(command.password());

        User user = new User(
                null,
                command.fullName().trim(),
                normalizedEmail,
                normalizedDocument,
                passwordHash,
                Role.PATIENT,
                true,
                false
        );

        User savedUser = users.save(user);

        return new Result(
                savedUser.id(),
                savedUser.role()
        );
    }
}