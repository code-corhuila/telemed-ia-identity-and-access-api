package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.LoginAttempts;

import java.util.Optional;

/** Failed-login counter keyed by the login email; the adapter decides how the email is stored. */
public interface LoginAttemptsRepositoryPort {

    Optional<LoginAttempts> find(String email);

    void save(String email, LoginAttempts attempts);
}
