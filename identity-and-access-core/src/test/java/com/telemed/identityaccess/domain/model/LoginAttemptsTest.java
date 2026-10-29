package com.telemed.identityaccess.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    private static final Instant NOW =
            Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void shouldLockForOneMinuteOnTheFifthFailureInsideTheWindow() {

        LoginAttempts attempts = failures(LoginAttempts.none(NOW), 4, NOW);

        assertThat(attempts.lockedAt(NOW)).isFalse();

        LoginAttempts locked = attempts.recordFailure(NOW);

        assertThat(locked.lockedAt(NOW)).isTrue();
        assertThat(locked.retryAfter(NOW)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldGrowTheDelayToFiveAndThenFifteenMinutes() {

        LoginAttempts attempts = LoginAttempts.none(NOW);
        Instant at = NOW;

        for (Duration expected : new Duration[] {
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofMinutes(15)
        }) {
            attempts = failures(attempts, 5, at);

            assertThat(attempts.retryAfter(at)).isEqualTo(expected);

            at = at.plus(expected);
        }
    }

    @Test
    void shouldEndTheLockByItself() {

        LoginAttempts locked = failures(LoginAttempts.none(NOW), 5, NOW);

        assertThat(locked.lockedAt(NOW.plusSeconds(59))).isTrue();
        assertThat(locked.lockedAt(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void shouldRestartTheCountWhenTheWindowHasPassed() {

        LoginAttempts attempts = failures(LoginAttempts.none(NOW), 4, NOW);
        Instant later = NOW.plus(Duration.ofMinutes(15));

        LoginAttempts afterWindow = attempts.recordFailure(later);

        assertThat(afterWindow.failedCount()).isEqualTo(1);
        assertThat(afterWindow.lockedAt(later)).isFalse();
    }

    @Test
    void shouldReportHistoryOnlyAfterAFailure() {

        assertThat(LoginAttempts.none(NOW).hasHistory()).isFalse();
        assertThat(LoginAttempts.none(NOW).recordFailure(NOW).hasHistory()).isTrue();
    }

    private static LoginAttempts failures(
            LoginAttempts attempts,
            int count,
            Instant at
    ) {
        for (int i = 0; i < count; i++) {
            attempts = attempts.recordFailure(at);
        }
        return attempts;
    }
}
