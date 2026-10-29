package com.telemed.identityaccess.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Failed-login counter of one account (STRIDE S, telemed-ia-docs#152).
 *
 * Five failures inside a 15-minute window lock the account for a growing
 * delay (1, 5, then 15 minutes). The lock always ends by itself, so it can
 * never be used to keep a patient out for good.
 */
public record LoginAttempts(
        int failedCount,
        Instant windowStartedAt,
        int lockoutCount,
        Instant lockedUntil
) {

    static final int MAX_FAILURES = 5;

    static final Duration WINDOW = Duration.ofMinutes(15);

    static final List<Duration> LOCK_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15)
    );

    public LoginAttempts {

        if (failedCount < 0 || lockoutCount < 0) {
            throw new IllegalArgumentException(
                    "Login attempt counters must not be negative."
            );
        }

        if (windowStartedAt == null) {
            throw new IllegalArgumentException(
                    "Login attempt window start is required."
            );
        }
    }

    public static LoginAttempts none(Instant now) {
        return new LoginAttempts(0, now, 0, null);
    }

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public Duration retryAfter(Instant now) {
        return lockedAt(now)
                ? Duration.between(now, lockedUntil)
                : Duration.ZERO;
    }

    public boolean hasHistory() {
        return failedCount > 0 || lockoutCount > 0;
    }

    public LoginAttempts recordFailure(Instant now) {

        boolean windowPassed =
                !now.isBefore(windowStartedAt.plus(WINDOW));

        int failures = windowPassed ? 1 : failedCount + 1;

        if (failures < MAX_FAILURES) {
            return new LoginAttempts(
                    failures,
                    windowPassed ? now : windowStartedAt,
                    lockoutCount,
                    lockedUntil
            );
        }

        Duration delay = LOCK_DELAYS.get(
                Math.min(lockoutCount, LOCK_DELAYS.size() - 1)
        );

        return new LoginAttempts(0, now, lockoutCount + 1, now.plus(delay));
    }
}
