package com.telemed.identityaccess.application.port.in;

public interface PasswordRecoveryUseCase {

    void requestRecovery(
            String email
    );
}