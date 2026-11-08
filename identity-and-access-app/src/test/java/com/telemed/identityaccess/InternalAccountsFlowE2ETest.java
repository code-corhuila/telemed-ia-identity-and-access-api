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
                "security.account-provisioning.clients=professional-management,workflow",
                "security.account-status.clients=workflow",
                "security.notification-recipients.clients=notifications",
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
                () -> "professional-management:" + hash + ",document-generation:" + hash + ",notifications:" + hash + ",workflow:" + hash);
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

        // A patient's id is never answered: only professional accounts are visible here.
        String patientId = objectMapper.readTree(postJson(
                "http://localhost:" + port + "/api/v1/auth/register",
                Map.of("fullName", "E2E Patient", "email", "e2e.lookup.patient@telemed.test",
                        "identityDocument", "E2E-DOC-PAT-9", "password", PASSWORD),
                withAnyIdempotencyKey()
        ).getBody()).get("userId").asText();

        HttpHeaders service = new HttpHeaders();
        service.setBearerAuth(token);
        JsonNode lookup = objectMapper.readTree(http.exchange(
                "http://localhost:" + port + "/internal/accounts?ids=" + accountId + "," + patientId,
                HttpMethod.GET,
                new HttpEntity<>(null, service),
                String.class
        ).getBody());

        assertThat(lookup.get("data")).hasSize(1);
        assertThat(lookup.get("data").get(0).get("fullName").asText()).isEqualTo("E2E Doctor");
    }

    /** The onboarding saga creates the account inactive, so nobody signs in before the saga succeeds. */
    @Test
    void shouldCreateAnInactiveProfessionalAccountThatCannotLogIn() throws Exception {

        ResponseEntity<String> created = provision(
                serviceToken("professional-management"),
                "e2e-register-professional-4",
                Map.of(
                        "fullName", "E2E Doctor Four",
                        "email", "e2e.doctor4@telemed.test",
                        "identityDocument", "E2E-DOC-PRO-4",
                        "password", PASSWORD,
                        "role", "PROFESSIONAL",
                        "active", false
                )
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM users WHERE id = ?",
                Boolean.class,
                UUID.fromString(objectMapper.readTree(created.getBody()).get("id").asText())
        );
        assertThat(active).isFalse();

        ResponseEntity<String> login = postJson(
                "http://localhost:" + port + "/api/v1/auth/login",
                Map.of("email", "e2e.doctor4@telemed.test", "password", PASSWORD),
                new HttpHeaders()
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    @Test
    void shouldAnswerTheEmailOfAnActivePatientToNotificationsOnly() throws Exception {

        String patientId = objectMapper.readTree(postJson(
                "http://localhost:" + port + "/api/v1/auth/register",
                Map.of("fullName", "E2E Recipient", "email", "e2e.recipient@telemed.test",
                        "identityDocument", "E2E-DOC-PAT-10", "password", PASSWORD),
                withAnyIdempotencyKey()
        ).getBody()).get("userId").asText();

        String professionalId = objectMapper.readTree(provision(
                serviceToken("professional-management"),
                "e2e-register-professional-3",
                Map.of("fullName", "E2E Doctor Three", "email", "e2e.doctor3@telemed.test",
                        "identityDocument", "E2E-DOC-PRO-3", "password", PASSWORD, "role", "PROFESSIONAL")
        ).getBody()).get("id").asText();

        String notifications = serviceToken("notifications");

        ResponseEntity<String> recipient = recipient(notifications, patientId);
        assertThat(recipient.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(recipient.getBody());
        assertThat(body.get("id").asText()).isEqualTo(patientId);
        assertThat(body.get("email").asText()).isEqualTo("e2e.recipient@telemed.test");

        assertThat(recipient(notifications, professionalId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(recipient(serviceToken("professional-management"), patientId).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** The saga activates the account last, and deactivates it to compensate: that also ends its sessions. */
    @Test
    void shouldLetTheWorkflowActivateAndDeactivateAProfessionalAccount() throws Exception {

        String workflow = serviceToken("workflow");
        String accountId = objectMapper.readTree(provision(workflow, "e2e-saga-professional-5", Map.of(
                "fullName", "E2E Doctor Five", "email", "e2e.doctor5@telemed.test",
                "identityDocument", "E2E-DOC-PRO-5", "password", PASSWORD, "role", "PROFESSIONAL",
                "active", false
        )).getBody()).get("id").asText();
        Map<String, String> credentials = Map.of("email", "e2e.doctor5@telemed.test", "password", PASSWORD);

        assertThat(accountStatus(workflow, accountId, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(accountStatus(workflow, accountId, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> login = postJson("http://localhost:" + port + "/api/v1/auth/login",
                credentials, new HttpHeaders());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(accountStatus(workflow, accountId, false).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(postJson("http://localhost:" + port + "/api/v1/auth/login", credentials, new HttpHeaders())
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Integer openSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false",
                Integer.class,
                UUID.fromString(accountId)
        );
        assertThat(openSessions).isZero();

        assertThat(accountStatus(serviceToken("professional-management"), accountId, true).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(accountStatus(workflow, UUID.randomUUID().toString(), true).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> accountStatus(String token, String accountId, boolean active) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + "/internal/accounts/" + accountId + "/status",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("active", active), headers),
                String.class
        );
    }

    private ResponseEntity<String> recipient(String token, String accountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(
                "http://localhost:" + port + "/internal/notification-recipients/" + accountId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class
        );
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

    private static HttpHeaders withAnyIdempotencyKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString()); // POST /register requires one
        return headers;
    }
}
