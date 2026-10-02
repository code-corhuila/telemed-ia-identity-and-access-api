package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.port.in.PasswordRecoveryUseCase;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;
import com.telemed.identityaccess.domain.model.User;

public class PasswordRecoveryService
        implements PasswordRecoveryUseCase {

    private final UserRepositoryPort users;
    private final PasswordResetTokenProviderPort tokenProvider;
    private final PasswordResetTokenRepositoryPort resetTokens;

    public PasswordRecoveryService(
            UserRepositoryPort users,
            PasswordResetTokenProviderPort tokenProvider,
            PasswordResetTokenRepositoryPort resetTokens
    ) {
        this.users = users;
        this.tokenProvider = tokenProvider;
        this.resetTokens = resetTokens;
    }

    @Override
    public void requestRecovery(
            String email
    ) {

        User user = users.findByEmail(email)
                .orElse(null);

        if (user == null) {
            return;
        }

        PasswordResetTokenProviderPort
                .IssuedPasswordResetToken issued =
                tokenProvider.issue();

        PasswordResetToken token =
                PasswordResetToken.active(
                        user.id(),
                        issued.tokenHash(),
                        issued.expiresAt()
                );

        resetTokens.save(token);
    }
}