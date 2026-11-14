package com.telemed.identityaccess.application.port.out;

public interface PasswordResetTokenHasherPort {

    String hash(String token);
}