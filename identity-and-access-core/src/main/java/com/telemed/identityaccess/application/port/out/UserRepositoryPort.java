package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    boolean existsByEmail(String email);

    boolean existsByIdentityDocument(String identityDocument);

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID userId);

    User save(User user);

    void updatePasswordHash(
            UUID userId,
            String passwordHash
    );
}