package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.ServiceAuthenticationException;
import com.telemed.identityaccess.application.port.in.IssueServiceTokenUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort.IssuedAccessToken;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenProviderPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Norma 5.7: services get their own token from identity, with their own client id and secret. */
class IssueServiceTokenServiceTest {

    private static final Instant EXPIRES = Instant.parse("2026-10-23T15:10:00Z");

    private final List<String> compared = new ArrayList<>();
    private final List<String> issuedFor = new ArrayList<>();

    private final PasswordHasherPort hasher = new PasswordHasherPort() {
        @Override
        public String hash(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            compared.add(passwordHash);
            return passwordHash.equals("hash:" + rawPassword);
        }

        @Override
        public boolean needsRehash(String passwordHash) {
            return false;
        }
    };

    private final ServiceTokenProviderPort tokens = clientId -> {
        issuedFor.add(clientId);
        return new IssuedAccessToken("signed-service-token", EXPIRES);
    };

    private final IssueServiceTokenService service = new IssueServiceTokenService(
            clientId -> Optional.ofNullable(Map.of("document-generation", "hash:right-secret").get(clientId)),
            hasher,
            tokens);

    @Test
    void issuesATokenToARegisteredServiceWithItsSecret() {
        IssueServiceTokenUseCase.Result result = service.issue("document-generation", "right-secret");

        assertThat(result.accessToken()).isEqualTo("signed-service-token");
        assertThat(result.expiresAt()).isEqualTo(EXPIRES);
        assertThat(issuedFor).containsExactly("document-generation");
    }

    @Test
    void refusesAWrongSecret() {
        assertThatThrownBy(() -> service.issue("document-generation", "wrong-secret"))
                .isInstanceOf(ServiceAuthenticationException.class);
        assertThat(issuedFor).isEmpty();
    }

    @Test
    void refusesAnUnknownClientAfterTheSameWorkAsAKnownOne() {
        assertThatThrownBy(() -> service.issue("someone-else", "right-secret"))
                .isInstanceOf(ServiceAuthenticationException.class);

        // A hash is still compared, so timing does not reveal which client ids exist.
        assertThat(compared).hasSize(1);
        assertThat(issuedFor).isEmpty();
    }

    @Test
    void refusesBlankCredentials() {
        assertThatThrownBy(() -> service.issue(" ", "right-secret"))
                .isInstanceOf(ServiceAuthenticationException.class);
        assertThatThrownBy(() -> service.issue("document-generation", null))
                .isInstanceOf(ServiceAuthenticationException.class);
    }
}
