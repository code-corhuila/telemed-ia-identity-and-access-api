package com.telemed.identityaccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative test for telemed-ia-docs#152 (STRIDE S, risk 6), acceptance criterion 1:
 * after 5 failed POST /api/v1/auth/login for one email, the next attempt is rejected
 * with 429 even with the right password and from another IP.
 * It fails until the per-account lock exists in auth-service.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "management.server.port=0"
        }
)
class LoginAccountLockE2ETest {

    private static final String PASSWORD = "Secret123!";
    private static final KeyPair RSA_KEY_PAIR = generateRsaKeyPair();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.private-key",
                () -> pem("PRIVATE KEY", RSA_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("security.jwt.public-key",
                () -> pem("PUBLIC KEY", RSA_KEY_PAIR.getPublic().getEncoded()));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Test
    void loginShouldBeLockedAfterFiveFailedAttemptsForTheSameEmail() {
        String auth = "http://localhost:" + port + "/api/v1/auth";
        String email = "lock.patient@example.com";

        assertThat(post(auth + "/register", Map.of(
                "fullName", "Lock Patient",
                "email", email,
                "identityDocument", "LOCK-DOC-152",
                "password", PASSWORD), "10.0.0.1").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(post(auth + "/login",
                    Map.of("email", email, "password", "Wrong-" + attempt),
                    "10.0.0." + attempt).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> sixth = post(auth + "/login",
                Map.of("email", email, "password", PASSWORD), "10.0.0.99");

        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth.getBody()).contains("traceId").doesNotContain("accessToken");
        assertThat(sixth.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    private ResponseEntity<String> post(String url, Object body, String clientIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);
        return http.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate RSA test keys.", exception);
        }
    }

    private static String pem(String label, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----";
    }
}
