package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.event.PasswordRecoveryRequested;
import com.telemed.identityaccess.application.port.in.PasswordRecoveryUseCase;
import com.telemed.identityaccess.application.port.out.IntegrationEventOutboxPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;
import com.telemed.identityaccess.domain.model.User;

import java.util.UUID;

public class PasswordRecoveryService
        implements PasswordRecoveryUseCase {

    private final UserRepositoryPort users;
    private final PasswordResetTokenProviderPort tokenProvider;
    private final PasswordResetTokenRepositoryPort resetTokens;
    private final IntegrationEventOutboxPort outbox;
    private final String resetUrl;

    public PasswordRecoveryService(
            UserRepositoryPort users,
            PasswordResetTokenProviderPort tokenProvider,
            PasswordResetTokenRepositoryPort resetTokens,
            IntegrationEventOutboxPort outbox,
            String resetUrl
    ) {
        if (resetUrl == null || resetUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Password reset URL is required."
            );
        }

        this.users = users;
        this.tokenProvider = tokenProvider;
        this.resetTokens = resetTokens;
        this.outbox = outbox;
        this.resetUrl = resetUrl;
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

        UUID recoveryTokenId =
                resetTokens.save(token);

        // The token travels in the URL fragment, which browsers never send
        // to a server, so it does not end up in access logs or Referer.
        outbox.record(
                new PasswordRecoveryRequested(
                        user.id(),
                        user.email(),
                        recoveryTokenId,
                        issued.expiresAt(),
                        resetUrl + "#token=" + issued.value()
                )
        );
    }
}
