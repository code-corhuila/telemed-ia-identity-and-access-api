package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    boolean existsByEmail(String email);

    boolean existsByIdentityDocument(String identityDocument);

    boolean existsByRole(Role role);

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID userId);

    User save(User user);

    /** Stamps the database time as the account's last sign-in. */
    void recordLastAccess(UUID userId);

    void updateActive(UUID userId, boolean active);

    /** Counts the active accounts of a role and locks them until the transaction ends. */
    long countActiveByRoleLocking(Role role);

    void updatePasswordHash(
            UUID userId,
            String passwordHash
    );
}