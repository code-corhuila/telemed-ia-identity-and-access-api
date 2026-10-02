package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.PasswordResetException;
import com.telemed.identityaccess.application.port.in.PasswordResetUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenHasherPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;

import java.time.Clock;

public class PasswordResetService
        implements PasswordResetUseCase {

    private final PasswordResetTokenRepositoryPort resetTokens;
    private final PasswordResetTokenHasherPort tokenHasher;
    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public PasswordResetService(
            PasswordResetTokenRepositoryPort resetTokens,
            PasswordResetTokenHasherPort tokenHasher,
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            Clock clock
    ) {
        this.resetTokens = resetTokens;
        this.tokenHasher = tokenHasher;
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    public void reset(
            String resetToken,
            String newPassword
    ) {

        if (resetToken == null
                || resetToken.isBlank()) {

            throw PasswordResetException
                    .invalidResetToken();
        }

        if (newPassword == null
                || newPassword.isBlank()) {

            throw PasswordResetException
                    .invalidNewPassword();
        }

        String tokenHash =
                tokenHasher.hash(
                        resetToken
                );

        PasswordResetToken storedToken =
                resetTokens.findByTokenHash(
                                tokenHash
                        )
                        .orElseThrow(
                                PasswordResetException
                                        ::invalidResetToken
                        );

        if (storedToken.used()) {
            throw PasswordResetException
                    .invalidResetToken();
        }

        if (!storedToken.expiresAt()
                .isAfter(clock.instant())) {

            throw PasswordResetException
                    .invalidResetToken();
        }

        String newPasswordHash =
                passwordHasher.hash(
                        newPassword
                );

        users.updatePasswordHash(
                storedToken.userId(),
                newPasswordHash
        );

        resetTokens.markUsedByTokenHash(
                tokenHash
        );
    }
}