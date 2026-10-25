package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Acceptance: professional management creates a professional account once, even when it retries. */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "security.account-provisioning.clients=professional-management",
                "management.server.port=0"
        }
)
class InternalAccountsFlowE2ETest {

    private static final String CLIENT_SECRET = "e2e-client-secret";
    private static final String PASSWORD = "Doctor-Secret-1";

    private static final KeyPair RSA_KEY_PAIR =
            generateRsaKeyPair();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_identity_test")
                    .withUsername("telemed_test")
                    .withPassword("telemed_test");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);

        String hash = new BCryptPasswordEncoder().encode(CLIENT_SECRET);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.private-key", () -> pem("PRIVATE KEY", RSA_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("security.jwt.public-key", () -> pem("PUBLIC KEY", RSA_KEY_PAIR.getPublic().getEncoded()));
        registry.add("security.service-clients",
                () -> "professional-management:" + hash + ",document-generation:" + hash);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateTheProfessionalAccountOnceAndLetItLogIn() throws Exception {

        String token = serviceToken("professional-management");
        Map<String, String> body = Map.of(
                "fullName", "E2E Doctor",
                "email", "e2e.doctor@telemed.test",
                "identityDocument", "E2E-DOC-PRO-1",
                "password", PASSWORD,
                "role", "PROFESSIONAL"
        );

        ResponseEntity<String> created = provision(token, "e2e-register-professional-1", body);
        ResponseEntity<String> retried = provision(token, "e2e-register-professional-1", body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);

        String accountId = objectMapper.readTree(created.getBody()).get("id").asText();
        assertThat(objectMapper.readTree(retried.getBody()).get("id").asText()).isEqualTo(accountId);
        assertThat(UUID.fromString(accountId)).isNotNull();

        Integer accounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                "e2e.doctor@telemed.test"
        );
        assertThat(accounts).isEqualTo(1);

        ResponseEntity<String> login = postJson(
                "http://localhost:" + port + "/api/v1/auth/login",
                Map.of("email", "e2e.doctor@telemed.test", "password", PASSWORD),
                new HttpHeaders()
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(objectMapper.readTree(login.getBody()).get("accessToken").asText());
        JsonNode session = objectMapper.readTree(http.exchange(
                "http://localhost:" + port + "/api/v1/auth/session",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer),
                String.class
        ).getBody());

        assertThat(session.get("userId").asText()).isEqualTo(accountId);
        assertThat(session.get("role").asText()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void shouldForbidAServiceThatIsNotAllowedToCreateAccounts() {

        ResponseEntity<String> response = provision(
                serviceToken("document-generation"),
                "e2e-register-professional-2",
                Map.of(
                        "fullName", "E2E Doctor Two",
                        "email", "e2e.doctor2@telemed.test",
                        "identityDocument", "E2E-DOC-PRO-2",
                        "password", PASSWORD,
                        "role", "PROFESSIONAL"
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String serviceToken(String clientId) {
        try {
            ResponseEntity<String> response = postJson(
                    "http://localhost:" + port + "/internal/service-token",
                    Map.of("clientId", clientId, "clientSecret", CLIENT_SECRET),
                    new HttpHeaders()
            );
            return objectMapper.readTree(response.getBody()).get("accessToken").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not get a service token.", exception);
        }
    }

    private ResponseEntity<String> provision(
            String token,
            String idempotencyKey,
            Object body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return postJson("http://localhost:" + port + "/internal/accounts", body, headers);
    }

    private ResponseEntity<String> postJson(
            String url,
            Object body,
            HttpHeaders headers
    ) {
        headers.setContentType(MediaType.APPLICATION_JSON);

        return http.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate RSA test keys.",
                    exception
            );
        }
    }

    private static String pem(
            String label,
            byte[] encoded
    ) {
        String body = Base64.getMimeEncoder(
                64,
                new byte[]{'\n'}
        ).encodeToString(encoded);

        return "-----BEGIN " + label + "-----\n"
                + body
                + "\n-----END " + label + "-----";
    }
}
