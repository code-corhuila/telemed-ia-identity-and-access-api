package com.telemed.identityaccess;

import com.telemed.identityaccess.adapter.out.persistence.SpringDataRoleRepository;
import com.telemed.identityaccess.adapter.out.persistence.SpringDataUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class IdentityAndAccessApplicationTest {

    @MockBean
    private SpringDataUserRepository userRepository;

    @MockBean
    private SpringDataRoleRepository roleRepository;

    @Test
    void contextLoads() {
    }
}