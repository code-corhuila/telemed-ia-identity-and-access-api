package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.domain.model.LoginAttempts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptsPersistenceAdapterTest {

    // SHA-256 of "patient@example.com".
    private static final String EMAIL_HASH =
            "aadb1f244eee72906a90778d06423094650dd79b18320d67de9a54c2f608c2d9";

    private static final Instant NOW =
            Instant.parse("2026-10-29T12:00:00Z");

    @Mock
    private SpringDataLoginAttemptsRepository repository;

    @Test
    void shouldStoreOnlyTheHashOfTheNormalizedEmail() {

        LoginAttemptsPersistenceAdapter adapter =
                new LoginAttemptsPersistenceAdapter(repository);

        LoginAttempts attempts = LoginAttempts.none(NOW).recordFailure(NOW);

        adapter.save("  Patient@Example.com ", attempts);

        ArgumentCaptor<LoginAttemptsJpaEntity> saved =
                ArgumentCaptor.forClass(LoginAttemptsJpaEntity.class);

        verify(repository).saveAndFlush(saved.capture());

        assertThat(saved.getValue().getEmailHash())
                .isEqualTo(EMAIL_HASH);
        assertThat(saved.getValue().toDomain()).isEqualTo(attempts);
    }

    @Test
    void shouldFindTheAttemptsOfTheSameEmail() {

        LoginAttemptsPersistenceAdapter adapter =
                new LoginAttemptsPersistenceAdapter(repository);

        LoginAttempts attempts = LoginAttempts.none(NOW).recordFailure(NOW);

        when(repository.findById(EMAIL_HASH))
                .thenReturn(Optional.of(LoginAttemptsJpaEntity.of(EMAIL_HASH, attempts)));

        assertThat(adapter.find("patient@example.com")).contains(attempts);
    }
}
