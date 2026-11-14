package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import java.util.UUID;

import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;

/** Runs in one transaction: the patient and its Idempotency-Key are stored together. */
public class RegisterPatientService implements RegisterPatientUseCase {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final IdempotencyKeyRepositoryPort idempotencyKeys;

    public RegisterPatientService(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            IdempotencyKeyRepositoryPort idempotencyKeys
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.idempotencyKeys = idempotencyKeys;
    }

    @Override
    public Result register(Command command) {

        var previous = idempotencyKeys.findUserId(command.idempotencyKey());
        if (previous.isPresent()) {
            return replay(previous.get(), command);
        }

        if (users.existsByEmail(command.email())) {
            throw new RegistrationException(
                    EMAIL_ALREADY_REGISTERED,
                    "Email is already registered."
            );
        }

        if (users.existsByIdentityDocument(
                command.identityDocument()
        )) {
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

        idempotencyKeys.save(command.idempotencyKey(), savedUser.id());

        return new Result(
                savedUser.id(),
                savedUser.role(),
                false
        );
    }

    private Result replay(UUID userId, Command command) {

        AccountCreationRetry.requireSameRequest(
                users,
                passwordHasher,
                userId,
                Role.PATIENT,
                command.fullName(),
                command.email(),
                command.identityDocument(),
                command.password()
        );

        return new Result(userId, Role.PATIENT, true);
    }
}
