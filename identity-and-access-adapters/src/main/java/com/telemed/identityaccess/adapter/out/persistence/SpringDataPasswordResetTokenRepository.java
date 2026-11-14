
package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataPasswordResetTokenRepository
        extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    @Query("""
            SELECT token
            FROM PasswordResetTokenJpaEntity token
            WHERE token.tokenHash = :tokenHash
              AND token.supersededAt IS NULL
            """)
    Optional<PasswordResetTokenJpaEntity> findByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            UPDATE PasswordResetTokenJpaEntity token
            SET token.used = true
            WHERE token.tokenHash = :tokenHash
              AND token.used = false
              AND token.supersededAt IS NULL
              AND token.expiresAt > CURRENT_TIMESTAMP
            """)
    int markUsedByTokenHash(
            @Param("tokenHash") String tokenHash
    );
}
