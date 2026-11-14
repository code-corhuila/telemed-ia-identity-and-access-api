package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

public class BootstrapAdminService implements BootstrapAdminUseCase {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;

    public BootstrapAdminService(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Outcome bootstrap(Command command) {

        if (users.existsByRole(Role.ADMIN)) {
            return Outcome.ALREADY_PRESENT;
        }

        if (users.existsByEmail(command.email())
                || users.existsByIdentityDocument(command.identityDocument())) {
            return Outcome.ACCOUNT_IN_USE;
        }

        User admin = User.registerAdmin(
                command.fullName(),
                command.email(),
                command.identityDocument(),
                passwordHasher.hash(command.password())
        );

        try {
            users.save(admin);

        } catch (RegistrationException exception) {
            // Another instance that started at the same time may have won the unique email.
            return users.existsByRole(Role.ADMIN)
                    ? Outcome.ALREADY_PRESENT
                    : Outcome.ACCOUNT_IN_USE;
        }

        return Outcome.CREATED;
    }
}
