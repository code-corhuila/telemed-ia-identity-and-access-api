package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.app.config.BootstrapAdminRunner;
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

/** Acceptance: the bootstrap admin logs in and gets role ADMIN, and a restart creates no second one. */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "management.server.port=0",
                "security.bootstrap-admin.full-name=Platform Admin",
                "security.bootstrap-admin.email=Admin@Telemed.test",
                "security.bootstrap-admin.identity-document=ADM-E2E-0001",
                "security.bootstrap-admin.password=" + BootstrapAdminFlowE2ETest.PASSWORD
        }
)
class BootstrapAdminFlowE2ETest {

    static final String PASSWORD = "Admin-Secret-1";

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

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.private-key", () -> pem("PRIVATE KEY", RSA_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("security.jwt.public-key", () -> pem("PUBLIC KEY", RSA_KEY_PAIR.getPublic().getEncoded()));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BootstrapAdminRunner bootstrapAdminRunner;

    @Test
    void shouldLogInTheBootstrapAdminWithRoleAdmin() throws Exception {

        String baseUrl = "http://localhost:" + port + "/api/v1/auth";

        ResponseEntity<String> loginResponse = postJson(
                baseUrl + "/login",
                Map.of("email", "admin@telemed.test", "password", PASSWORD)
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String accessToken = objectMapper.readTree(loginResponse.getBody())
                .get("accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<String> sessionResponse = http.exchange(
                baseUrl + "/session",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class
        );

        JsonNode session = objectMapper.readTree(sessionResponse.getBody());
        assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session.get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void shouldCreateTheAdminOnlyOnceAcrossRestarts() throws Exception {

        bootstrapAdminRunner.run(null);

        Integer admins = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                JOIN roles ON roles.id = users.role_id
                WHERE roles.name = 'ADMIN'
                """,
                Integer.class
        );

        assertThat(admins).isEqualTo(1);
    }

    private ResponseEntity<String> postJson(
            String url,
            Object body
    ) {
        HttpHeaders headers = new HttpHeaders();
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
