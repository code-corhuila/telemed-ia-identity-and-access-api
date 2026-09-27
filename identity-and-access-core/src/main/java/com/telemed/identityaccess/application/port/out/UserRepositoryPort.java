package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {

    boolean existsByEmail(String email);

    boolean existsByIdentityDocument(String identityDocument);

    Optional<User> findByEmail(String email);

    User save(User user);
    
    void updatePasswordHash(
        Long userId,
        String passwordHash
);
}