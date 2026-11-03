package com.telemed.identityaccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Week 11 metric catalog (CP4): Prometheus scrapes identity on the management port only. */
// Spring Boot turns metrics export off in tests unless asked for.
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "management.server.port=0",
                // Tracing is on here too: keep the spans off the platform collector, absent in tests.
                "management.otlp.tracing.export.enabled=false"
        }
)
class MetricsEndpointE2ETest {

    private static final KeyPair RSA_KEY_PAIR = generateRsaKeyPair();

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_identity_metrics_test")
                    .withUsername("telemed_test")
                    .withPassword("telemed_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.private-key", () -> pem("PRIVATE KEY", RSA_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("security.jwt.public-key", () -> pem("PUBLIC KEY", RSA_KEY_PAIR.getPublic().getEncoded()));
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Test
    void exposesTheWeek11MetricsOnTheManagementPort() throws InterruptedException {
        login("nobody@example.com", "WrongPassword123!");

        ResponseEntity<String> scrape = scrapeOnceRecorded("identity_requests_total{");

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .containsPattern("identity_requests_total\\{[^}]*outcome=\"client_error\"[^}]*route=\"/api/v1/auth/login\"")
                .contains("identity_request_duration_seconds_bucket{")
                .contains("identity_outbox_pending_events ")
                .contains("identity_outbox_oldest_pending_age_seconds ")
                // Saturation: the connection pool to identity-db.
                .contains("hikaricp_connections_active{");
    }

    @Test
    void publishesTheBucketsTheSloRulesRead() throws InterruptedException {
        login("nobody@example.com", "WrongPassword123!");

        // The latency SLO counts requests answered within 0.5 s and 1 s.
        assertThat(scrapeOnceRecorded("identity_request_duration_seconds_bucket{").getBody())
                .containsPattern("identity_request_duration_seconds_bucket\\{[^}]*le=\"0.5\"")
                .containsPattern("identity_request_duration_seconds_bucket\\{[^}]*le=\"1.0\"");
    }

    @Test
    void showsHowLongTheOldestEventHasWaitedForTheRelay() {
        jdbc.update("""
                        INSERT INTO identity_outbox_event (id, event_type, aggregate_id, payload, occurred_at)
                        VALUES (?, 'PasswordRecoveryRequested', ?, CAST('{}' AS jsonb), now() - interval '2 minutes')
                        """,
                UUID.randomUUID(), UUID.randomUUID());

        String body = scrape().getBody();

        assertThat(gauge(body, "identity_outbox_pending_events")).isGreaterThanOrEqualTo(1.0);
        assertThat(gauge(body, "identity_outbox_oldest_pending_age_seconds")).isGreaterThanOrEqualTo(120.0);
    }

    @Test
    void keepsTheMetricsOffTheApiPort() {
        ResponseEntity<String> response =
                http.getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    private void login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        http.postForEntity("http://localhost:" + port + "/api/v1/auth/login",
                new HttpEntity<>("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", headers),
                String.class);
    }

    private ResponseEntity<String> scrape() {
        return http.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class);
    }

    /**
     * The filter records a request after its response is sent, so a fast scrape can arrive
     * first; Prometheus scrapes every few seconds and never notices, a test can.
     */
    private ResponseEntity<String> scrapeOnceRecorded(String series) throws InterruptedException {
        ResponseEntity<String> scrape = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            scrape = scrape();
            if (scrape.getBody() != null && scrape.getBody().contains(series)) {
                return scrape;
            }
            Thread.sleep(100);
        }
        return scrape;
    }

    private static double gauge(String body, String name) {
        Matcher matcher = Pattern.compile("(?m)^" + name + " (\\S+)$").matcher(body);
        assertThat(matcher.find()).as(name).isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
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
}
