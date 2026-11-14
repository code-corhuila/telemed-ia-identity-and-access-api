package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

/** Acceptance (DEC-013): an administrator reads the names of several real accounts; a patient cannot. */
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
                "security.bootstrap-admin.email=admin.names@telemed.test",
                "security.bootstrap-admin.identity-document=ADM-E2E-NAMES",
                "security.bootstrap-admin.password=" + AccountNamesFlowE2ETest.PASSWORD
        }
)
class AccountNamesFlowE2ETest {

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

    @BeforeEach
    void patients() {
        register("Ana Patient", "ana.names@telemed.test", "E2E-NAMES-1");
        register("Bruno Patient", "bruno.names@telemed.test", "E2E-NAMES-2");
    }

    @Test
    void shouldGiveTheAdministratorTheNamesOfTheAccounts() throws Exception {

        String admin = login("admin.names@telemed.test", PASSWORD);
        JsonNode patients = getJson(admin, "/api/v1/auth/admin/accounts?role=PATIENT&q=names");
        String ana = idOf(patients, "Ana Patient");
        String bruno = idOf(patients, "Bruno Patient");
        String unknown = UUID.randomUUID().toString();

        JsonNode names = getJson(admin, "/api/v1/auth/accounts/names?ids=" + ana + "," + bruno + "," + unknown);

        assertThat(names.get("data")).hasSize(2);
        assertThat(names.get("data").findValuesAsText("fullName"))
                .containsExactlyInAnyOrder("Ana Patient", "Bruno Patient");
        assertThat(names.get("data").get(0).has("email")).isFalse();
    }

    @Test
    void shouldForbidAPatient() throws Exception {

        String patient = login("ana.names@telemed.test", "Patient-Secret-1");

        assertThat(get(patient, "/api/v1/auth/accounts/names?ids=" + UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static String idOf(JsonNode page, String fullName) {
        for (JsonNode account : page.get("data")) {
            if (fullName.equals(account.get("fullName").asText())) {
                return account.get("id").asText();
            }
        }
        throw new AssertionError(fullName + " is not in the page");
    }

    private void register(String fullName, String email, String document) {
        // A second run of this hook finds the account already registered (409), which is fine.
        postJson("/api/v1/auth/register", Map.of(
                "fullName", fullName,
                "email", email,
                "identityDocument", document,
                "password", "Patient-Secret-1"
        ));
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> response = postJson(
                "/api/v1/auth/login",
                Map.of("email", email, "password", password)
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("accessToken").asText();
    }

    private JsonNode getJson(String token, String path) throws Exception {
        ResponseEntity<String> response = get(token, path);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> get(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(
                "http://localhost:" + port + path,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class
        );
    }

    private ResponseEntity<String> postJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString()); // POST /register requires one
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
