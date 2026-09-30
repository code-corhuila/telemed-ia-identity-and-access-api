package com.telemed.identityaccess;

import com.telemed.identityaccess.adapter.out.persistence.SpringDataRoleRepository;
import com.telemed.identityaccess.adapter.out.persistence.SpringDataUserRepository;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "security.jwt.issuer=telemed-ia-identity-and-access",
        "security.jwt.access-token-ttl=PT1H",
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class IdentityAndAccessApplicationTest {

    @MockBean
    private SpringDataUserRepository userRepository;

    @MockBean
    private SpringDataRoleRepository roleRepository;

    @MockBean
    private AccessTokenProviderPort accessTokenProvider;

    @Test
    void contextLoads() {
    }
}