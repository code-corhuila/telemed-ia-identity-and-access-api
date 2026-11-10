
package com.telemed.identityaccess;

import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import com.telemed.identityaccess.application.port.out.IntegrationEventOutboxPort;
import com.telemed.identityaccess.application.port.out.OutboxEventsPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenProviderPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "security.jwt.issuer=telemed-ia-identity-and-access",
        "security.jwt.access-token-ttl=PT1H",
        "security.refresh-token.ttl=P7D",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class IdentityAndAccessApplicationTest {

    @MockBean
    private AccessTokenProviderPort accessTokenProvider;

    @MockBean
    private ServiceTokenProviderPort serviceTokenProvider;

    @MockBean
    private AccessTokenVerifierPort accessTokenVerifier;

    @MockBean
    private ServiceTokenVerifierPort serviceTokenVerifier;

    @MockBean
    private IdempotencyKeyRepositoryPort idempotencyKeys;

    @MockBean
    private UserRepositoryPort userRepository;

    @MockBean
    private AccountDirectoryPort accountDirectory;

    @MockBean
    private PasswordHasherPort passwordHasher;

    @MockBean
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @MockBean
    private PasswordResetTokenRepositoryPort passwordResetTokenRepository;

    @MockBean
    private IntegrationEventOutboxPort integrationEventOutbox;

    @MockBean
    private OutboxEventsPort outboxEvents;

    @MockBean
    private PlatformTransactionManager transactionManager;

    @Test
    void contextLoads() {
    }
}
