
package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.domain.model.PasswordResetToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 120
    )
    private String tokenHash;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    protected PasswordResetTokenJpaEntity() {
    }

    private PasswordResetTokenJpaEntity(
            UUID userId,
            String tokenHash,
            OffsetDateTime expiresAt,
            boolean used
    ) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.used = used;
    }

    public static PasswordResetTokenJpaEntity fromDomain(
            PasswordResetToken token
    ) {
        return new PasswordResetTokenJpaEntity(
                token.userId(),
                token.tokenHash(),
                OffsetDateTime.ofInstant(
                        token.expiresAt(),
                        ZoneOffset.UTC
                ),
                token.used()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }
}
