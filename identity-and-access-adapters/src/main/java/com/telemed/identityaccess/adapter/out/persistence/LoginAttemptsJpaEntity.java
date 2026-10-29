package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.domain.model.LoginAttempts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "login_attempts")
public class LoginAttemptsJpaEntity {

    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "window_started_at", nullable = false)
    private OffsetDateTime windowStartedAt;

    @Column(name = "lockout_count", nullable = false)
    private int lockoutCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LoginAttemptsJpaEntity() {
    }

    static LoginAttemptsJpaEntity of(
            String emailHash,
            LoginAttempts attempts
    ) {
        LoginAttemptsJpaEntity entity = new LoginAttemptsJpaEntity();
        entity.emailHash = emailHash;
        entity.failedCount = attempts.failedCount();
        entity.windowStartedAt = utc(attempts.windowStartedAt());
        entity.lockoutCount = attempts.lockoutCount();
        entity.lockedUntil = utc(attempts.lockedUntil());
        entity.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return entity;
    }

    LoginAttempts toDomain() {
        return new LoginAttempts(
                failedCount,
                windowStartedAt.toInstant(),
                lockoutCount,
                lockedUntil == null ? null : lockedUntil.toInstant()
        );
    }

    String getEmailHash() {
        return emailHash;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
