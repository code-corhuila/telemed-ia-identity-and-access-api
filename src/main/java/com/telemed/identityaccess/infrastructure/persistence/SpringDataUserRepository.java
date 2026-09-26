package com.telemed.identityaccess.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataUserRepository
        extends JpaRepository<UserJpaEntity, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdentityDocument(String identityDocument);

    Optional<UserJpaEntity> findByEmailIgnoreCase(String email);
}