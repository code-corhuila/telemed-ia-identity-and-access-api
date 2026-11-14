package com.telemed.identityaccess.application.port.out;

public interface RefreshTokenHasherPort {

    String hash(String refreshToken);
}