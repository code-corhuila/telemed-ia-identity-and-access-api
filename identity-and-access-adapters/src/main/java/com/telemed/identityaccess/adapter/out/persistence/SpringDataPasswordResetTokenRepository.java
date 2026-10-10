
package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataPasswordResetTokenRepository
        extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    Optional<PasswordResetTokenJpaEntity> findByTokenHash(
            String tokenHash
    );

    @Modifying
    @Query("""
            update PasswordResetTokenJpaEntity token
               set token.used = true
             where token.tokenHash = :tokenHash
               and token.used = false
            """)
    int markUsedByTokenHash(
            @Param("tokenHash") String tokenHash
    );
}
