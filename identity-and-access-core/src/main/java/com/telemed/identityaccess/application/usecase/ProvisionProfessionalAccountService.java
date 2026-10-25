package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.IDEMPOTENCY_KEY_REUSED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;

/** Runs in one transaction: the account and its Idempotency-Key are stored together. */
public class ProvisionProfessionalAccountService
        implements ProvisionProfessionalAccountUseCase {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final IdempotencyKeyRepositoryPort idempotencyKeys;

    public ProvisionProfessionalAccountService(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            IdempotencyKeyRepositoryPort idempotencyKeys
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.idempotencyKeys = idempotencyKeys;
    }

    @Override
    public Result provision(Command command) {

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

        if (users.existsByIdentityDocument(command.identityDocument())) {
            throw new RegistrationException(
                    IDENTITY_DOCUMENT_ALREADY_REGISTERED,
                    "Identity document is already registered."
            );
        }

        User saved = users.save(User.registerProfessional(
                command.fullName(),
                command.email(),
                command.identityDocument(),
                passwordHasher.hash(command.password())
        ));

        idempotencyKeys.save(command.idempotencyKey(), saved.id());

        return new Result(saved.id(), false);
    }

    private Result replay(UUID userId, Command command) {

        User account = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency-Key points to a missing account."
                ));

        boolean sameRequest = account.role() == Role.PROFESSIONAL
                && account.fullName().equals(command.fullName())
                && account.email().equalsIgnoreCase(command.email())
                && account.identityDocument().equals(command.identityDocument())
                && passwordHasher.matches(command.password(), account.passwordHash());

        if (!sameRequest) {
            throw new AccountProvisioningException(
                    IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used for a different request."
            );
        }

        return new Result(userId, true);
    }
}
