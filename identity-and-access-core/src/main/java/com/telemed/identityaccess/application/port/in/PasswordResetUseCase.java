package com.telemed.identityaccess.application.port.in;

public interface PasswordResetUseCase {

    void reset(
            String resetToken,
            String newPassword
    );
}