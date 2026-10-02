package com.telemed.identityaccess;

import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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
    private AccessTokenVerifierPort accessTokenVerifier;

    @MockBean
    private UserRepositoryPort userRepository;

    @MockBean
    private PasswordHasherPort passwordHasher;

    @MockBean
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @MockBean
    private PasswordResetTokenRepositoryPort passwordResetTokenRepository;

    @Test
    void contextLoads() {
    }
}