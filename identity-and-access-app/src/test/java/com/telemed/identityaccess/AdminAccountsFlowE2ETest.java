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

import static org.assertj.core.api.Assertions.assertThat;

/** Acceptance (HU-05 AC4, AC5): the administrator browses the real accounts; a patient cannot. */
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
                "security.bootstrap-admin.email=admin.e2e@telemed.test",
                "security.bootstrap-admin.identity-document=ADM-E2E-LIST",
                "security.bootstrap-admin.password=" + AdminAccountsFlowE2ETest.PASSWORD
        }
)
class AdminAccountsFlowE2ETest {

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
        register("Ana Patient", "ana.list@telemed.test", "E2E-LIST-1");
        register("Bruno Patient", "bruno.list@telemed.test", "E2E-LIST-2");
    }

    @Test
    void shouldLetTheAdminBrowseTheAccounts() throws Exception {

        String admin = login("admin.e2e@telemed.test", PASSWORD);

        JsonNode patients = getJson(admin, "/api/v1/auth/admin/accounts?role=PATIENT&limit=1&page=2");
        assertThat(patients.get("meta").get("page").asInt()).isEqualTo(2);
        assertThat(patients.get("meta").get("limit").asInt()).isEqualTo(1);
        assertThat(patients.get("meta").get("total").asInt()).isEqualTo(2);
        assertThat(patients.get("meta").get("totalPages").asInt()).isEqualTo(2);
        // Newest first: Bruno registered after Ana, so Ana is on the second page.
        assertThat(patients.get("data").get(0).get("email").asText()).isEqualTo("ana.list@telemed.test");

        JsonNode found = getJson(admin, "/api/v1/auth/admin/accounts?q=BRUNO");
        assertThat(found.get("meta").get("total").asInt()).isEqualTo(1);
        assertThat(found.get("data").get(0).get("fullName").asText()).isEqualTo("Bruno Patient");
        assertThat(found.get("data").get(0).has("passwordHash")).isFalse();

        JsonNode admins = getJson(admin, "/api/v1/auth/admin/accounts?role=ADMIN&active=true");
        String adminId = admins.get("data").get(0).get("id").asText();
        assertThat(admins.get("data").get(0).get("lastAccess").isNull()).isFalse();

        JsonNode detail = getJson(admin, "/api/v1/auth/admin/accounts/" + adminId);
        assertThat(detail.get("email").asText()).isEqualTo("admin.e2e@telemed.test");
        assertThat(detail.get("registrationDate").asText()).endsWith("Z");
    }

    @Test
    void shouldForbidAPatient() throws Exception {

        String patient = login("ana.list@telemed.test", "Patient-Secret-1");

        assertThat(get(patient, "/api/v1/auth/admin/accounts").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
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
