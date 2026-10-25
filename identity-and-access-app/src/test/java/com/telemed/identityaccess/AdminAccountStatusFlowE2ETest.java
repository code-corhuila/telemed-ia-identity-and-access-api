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
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

/** Acceptance (FR-012): a blocked account can neither sign in nor refresh its session, until restored. */
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
                "security.bootstrap-admin.email=admin.status@telemed.test",
                "security.bootstrap-admin.identity-document=ADM-E2E-STATUS",
                "security.bootstrap-admin.password=" + AdminAccountStatusFlowE2ETest.ADMIN_PASSWORD
        }
)
class AdminAccountStatusFlowE2ETest {

    static final String ADMIN_PASSWORD = "Admin-Secret-1";
    private static final String PATIENT_PASSWORD = "Patient-Secret-1";

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

    @Test
    void shouldBlockAndRestoreTheAccessOfAnAccount() throws Exception {

        String patientId = objectMapper.readTree(postJson("/api/v1/auth/register", Map.of(
                "fullName", "Carla Patient",
                "email", "carla.status@telemed.test",
                "identityDocument", "E2E-STATUS-1",
                "password", PATIENT_PASSWORD
        ), null).getBody()).get("userId").asText();

        JsonNode patientSession = login("carla.status@telemed.test", PATIENT_PASSWORD);
        String admin = login("admin.status@telemed.test", ADMIN_PASSWORD).get("accessToken").asText();

        ResponseEntity<String> blocked = changeStatus(admin, patientId, false);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(blocked.getBody()).get("active").asBoolean()).isFalse();

        assertThat(postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", patientSession.get("refreshToken").asText()), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postJson("/api/v1/auth/login",
                Map.of("email", "carla.status@telemed.test", "password", PATIENT_PASSWORD), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(changeStatus(admin, patientId, true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("carla.status@telemed.test", PATIENT_PASSWORD).get("accessToken").asText()).isNotBlank();
    }

    @Test
    void shouldNeverLetTheAdminBlockThemselves() throws Exception {

        JsonNode session = login("admin.status@telemed.test", ADMIN_PASSWORD);
        String admin = session.get("accessToken").asText();
        String adminId = session.get("userId").asText();

        ResponseEntity<String> response = changeStatus(admin, adminId, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(response.getBody()).get("error").asText())
                .isEqualTo("ACCOUNT_STATUS_CONFLICT");
    }

    @Test
    void shouldBlockAnotherAdminWhileOneStaysActive() throws Exception {

        // Public registration never creates admins, so the second one is inserted directly.
        String otherAdminId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (id, full_name, email, identity_document, role_id, password_hash, verified, active)
                SELECT gen_random_uuid(), 'Second Admin', 'second.admin@telemed.test', 'ADM-E2E-SECOND',
                       roles.id, '$2a$10$notusedfortheloginxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', true, true
                FROM roles WHERE roles.name = 'ADMIN'
                RETURNING id
                """,
                String.class
        );
        String admin = login("admin.status@telemed.test", ADMIN_PASSWORD).get("accessToken").asText();

        // Counts the active admins with FOR UPDATE: two are active, so one may be blocked.
        ResponseEntity<String> response = changeStatus(admin, otherAdminId, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()).get("active").asBoolean()).isFalse();
    }

    private JsonNode login(String email, String password) throws Exception {
        ResponseEntity<String> response = postJson(
                "/api/v1/auth/login",
                Map.of("email", email, "password", password),
                null
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> changeStatus(String token, String accountId, boolean active) {
        // HttpURLConnection, the default, cannot send PATCH.
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + "/api/v1/auth/admin/accounts/" + accountId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", active), headers),
                String.class
        );
    }

    private ResponseEntity<String> postJson(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(
                "http://localhost:" + port + path,
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
