package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.PasswordResetToken;

public interface PasswordResetTokenRepositoryPort {

    void save(
            PasswordResetToken passwordResetToken
    );
}