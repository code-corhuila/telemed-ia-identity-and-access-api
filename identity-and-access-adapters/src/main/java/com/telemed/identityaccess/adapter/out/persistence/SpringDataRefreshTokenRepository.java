package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataRefreshTokenRepository
        extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(
            String tokenHash
    );

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity token
            set token.revoked = true
            where token.tokenHash = :tokenHash
              and token.revoked = false
            """)
    int revokeByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity token
            set token.revoked = true
            where token.userId = :userId
              and token.revoked = false
            """)
    int revokeAllByUserId(
            @Param("userId") UUID userId
    );
}