package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserRepository
        extends JpaRepository<UserJpaEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdentityDocument(
            String identityDocument
    );

    Optional<UserJpaEntity> findByEmailIgnoreCase(
            String email
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Transactional
    @Query("""
            update UserJpaEntity user
            set user.passwordHash = :passwordHash
            where user.id = :userId
            """)
    int updatePasswordHashById(
            @Param("userId") UUID userId,
            @Param("passwordHash") String passwordHash
    );
}