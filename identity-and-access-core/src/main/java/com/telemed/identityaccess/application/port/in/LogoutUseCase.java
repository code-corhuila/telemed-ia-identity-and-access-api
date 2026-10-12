package com.telemed.identityaccess.application.port.in;

public interface LogoutUseCase {

    void logout(String refreshToken);
}