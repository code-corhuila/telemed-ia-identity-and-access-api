package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "spring.liquibase.enabled=false",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "security.password-reset-token.ttl=PT30M",
                "security.password-recovery.reset-url="
                        + PasswordRecoveryFlowE2ETest.RESET_URL,
                "management.server.port=0"
        }
)
@Import(
        PasswordRecoveryFlowE2ETest
                .ResetTokenTestConfiguration.class
)
class PasswordRecoveryFlowE2ETest {

    private static final String OLD_PASSWORD =
            "Secret123!";

    private static final String NEW_PASSWORD =
            "NewSecret456!";

    private static final String RESET_TOKEN =
            "e2e-password-reset-token";

    static final String RESET_URL =
            "https://portal.e2e.test/reset-password";

    private static final KeyPair RSA_KEY_PAIR =
            generateRsaKeyPair();

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:16.4-alpine"
            )
                    .withDatabaseName(
                            "telemed_identity_recovery_test"
                    )
                    .withUsername(
                            "telemed_test"
                    )
                    .withPassword(
                            "telemed_test"
                    );

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "security.jwt.private-key",
                () -> privateKeyPem(
                        RSA_KEY_PAIR
                )
        );
        registry.add(
                "security.jwt.public-key",
                () -> publicKeyPem(
                        RSA_KEY_PAIR
                )
        );
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearFixedTokenBetweenTests() {
        // Recovery E2E scenarios share a deterministic token provider.
        // Reset only this synthetic token, in the isolated Testcontainers DB.
        jdbcTemplate.update(
                "DELETE FROM password_reset_tokens WHERE token_hash = ?",
                sha256(RESET_TOKEN)
        );
    }

    @Test
    void shouldCompletePasswordRecoveryFlow()
            throws Exception {
        String baseUrl =
                "http://localhost:"
                        + port
                        + "/api/v1/auth";
        String email =
                "recovery.e2e@example.com";
        /*
         * 1. REGISTER PATIENT
         */
        ResponseEntity<String> registerResponse =
                postJson(
                        baseUrl + "/register",
                        Map.of(
                                "fullName",
                                "Recovery E2E Patient",
                                "email",
                                email,
                                "identityDocument",
                                "RECOVERY-E2E-100",
                                "password",
                                OLD_PASSWORD
                        )
                );
        assertThat(
                registerResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.CREATED
        );
        /*
         * 2. LOGIN WITH ORIGINAL PASSWORD
         */
        ResponseEntity<String> initialLoginResponse =
                postJson(
                        baseUrl + "/login",
                        Map.of(
                                "email",
                                email,
                                "password",
                                OLD_PASSWORD
                        )
                );
        assertThat(
                initialLoginResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.OK
        );
        /*
         * 3. REQUEST PASSWORD RECOVERY
         */
        ResponseEntity<String> recoveryResponse =
                postJson(
                        baseUrl + "/password-recovery",
                        Map.of(
                                "email",
                                email
                        )
                );
        assertThat(
                recoveryResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.ACCEPTED
        );
        JsonNode recoveryBody =
                objectMapper.readTree(
                        recoveryResponse.getBody()
                );
        assertThat(
                recoveryBody.get("message")
                        .asText()
        ).isEqualTo(
                "If the email is registered, "
                        + "password recovery instructions will be sent."
        );
        /*
         * 4. RESET PASSWORD
         */
        ResponseEntity<String> resetResponse =
                postJson(
                        baseUrl + "/password-reset",
                        Map.of(
                                "token",
                                RESET_TOKEN,
                                "newPassword",
                                NEW_PASSWORD
                        )
                );
        assertThat(
                resetResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.NO_CONTENT
        );
        /*
         * 5. OLD PASSWORD MUST NO LONGER WORK
         */
        ResponseEntity<String> oldPasswordLoginResponse =
                postJson(
                        baseUrl + "/login",
                        Map.of(
                                "email",
                                email,
                                "password",
                                OLD_PASSWORD
                        )
                );
        assertThat(
                oldPasswordLoginResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.UNAUTHORIZED
        );
        JsonNode oldPasswordError =
                objectMapper.readTree(
                        oldPasswordLoginResponse.getBody()
                );
        assertThat(
                oldPasswordError.get("error")
                        .asText()
        ).isEqualTo(
                "INVALID_CREDENTIALS"
        );
        /*
         * 6. NEW PASSWORD MUST WORK
         */
        ResponseEntity<String> newPasswordLoginResponse =
                postJson(
                        baseUrl + "/login",
                        Map.of(
                                "email",
                                email,
                                "password",
                                NEW_PASSWORD
                        )
                );
        assertThat(
                newPasswordLoginResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.OK
        );
        JsonNode newLogin =
                objectMapper.readTree(
                        newPasswordLoginResponse.getBody()
                );
        assertThat(
                newLogin.get("accessToken")
                        .asText()
        ).isNotBlank();
        assertThat(
                newLogin.get("refreshToken")
                        .asText()
        ).isNotBlank();
        /*
         * 7. RESET TOKEN MUST NOT BE REUSABLE
         */
        ResponseEntity<String> reusedResetResponse =
                postJson(
                        baseUrl + "/password-reset",
                        Map.of(
                                "token",
                                RESET_TOKEN,
                                "newPassword",
                                "AnotherSecret789!"
                        )
                );
        assertThat(
                reusedResetResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.UNAUTHORIZED
        );
        JsonNode reusedResetError =
                objectMapper.readTree(
                        reusedResetResponse.getBody()
                );
        assertThat(
                reusedResetError.get("error")
                        .asText()
        ).isEqualTo(
                "INVALID_RESET_TOKEN"
        );
    }

    @Test
    void shouldRejectExpiredResetTokenWithoutChangingPassword()
            throws Exception {
        String baseUrl = "http://localhost:" + port + "/api/v1/auth";
        String email = "expired.recovery.e2e@example.com";
        ResponseEntity<String> registered = postJson(
                baseUrl + "/register",
                Map.of(
                        "fullName", "Expired Recovery Patient",
                        "email", email,
                        "identityDocument", "RECOVERY-EXPIRED-101",
                        "password", OLD_PASSWORD
                )
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> requested = postJson(
                baseUrl + "/password-recovery",
                Map.of("email", email)
        );
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int rows = jdbcTemplate.update(
                "UPDATE password_reset_tokens SET expires_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                sha256(RESET_TOKEN)
        );
        assertThat(rows).isEqualTo(1);
        ResponseEntity<String> rejected = postJson(
                baseUrl + "/password-reset",
                Map.of("token", RESET_TOKEN, "newPassword", NEW_PASSWORD)
        );
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode error = objectMapper.readTree(rejected.getBody());
        assertThat(error.path("error").asText()).isEqualTo("INVALID_RESET_TOKEN");
        ResponseEntity<String> originalLogin = postJson(
                baseUrl + "/login",
                Map.of("email", email, "password", OLD_PASSWORD)
        );
        assertThat(originalLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldExposeOfficialPasswordResetLifecycleSchema() {
        String idColumnType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                AND table_name = 'password_reset_tokens'
                AND column_name = 'id'
                """,
                String.class
        );
        assertThat(idColumnType)
                .as("Password reset token ID must use the official UUID type")
                .isEqualTo("uuid");
        String supersededAtType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                AND table_name = 'password_reset_tokens'
                AND column_name = 'superseded_at'
                """,
                String.class
        );
        assertThat(supersededAtType)
                .as("Official schema must expose superseded_at as TIMESTAMPTZ")
                .isEqualTo("timestamp with time zone");
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.databasechangelog_identity_and_access
                WHERE id IN (
                        'ddl-alter-003-migrate-password-reset-token-id-to-uuid',
                        'ddl-alter-004-record-reset-supersession'
                )
                """,
                Integer.class
        );
        assertThat(appliedMigrations)
                .as("Official UUID and supersession migrations must be applied")
                .isEqualTo(2);
    }

    @Test
    void shouldRollbackPasswordChangeWhenResetTokenCannotBeConsumed() {
        String baseUrl = "http://localhost:" + port + "/api/v1/auth";
        String email = "atomic.reset.e2e@example.com";

        ResponseEntity<String> registered = postJson(
                baseUrl + "/register",
                Map.of(
                        "fullName", "Atomic Reset Patient",
                        "email", email,
                        "identityDocument", "RECOVERY-ATOMIC-102",
                        "password", OLD_PASSWORD
                )
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> requested = postJson(
                baseUrl + "/password-recovery",
                Map.of("email", email)
        );
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Integer existingTokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?",
                Integer.class,
                sha256(RESET_TOKEN)
        );
        assertThat(existingTokenCount).isEqualTo(1);

        // Simulate a database failure while consuming the recovery token.
        // This trigger exists only inside the isolated Testcontainers database.
        try {
            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION e2e_reject_reset_token_use()
                    RETURNS trigger AS $$
                    BEGIN
                        IF NEW.used AND NOT OLD.used THEN
                            RAISE EXCEPTION 'Simulated reset token update failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);

            jdbcTemplate.execute("""
                    CREATE TRIGGER e2e_fail_reset_use
                    BEFORE UPDATE OF used ON password_reset_tokens
                    FOR EACH ROW
                    EXECUTE FUNCTION e2e_reject_reset_token_use()
                    """);

            ResponseEntity<String> failedReset = postJson(
                    baseUrl + "/password-reset",
                    Map.of("token", RESET_TOKEN, "newPassword", NEW_PASSWORD)
            );
            assertThat(failedReset.getStatusCode())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            // The reset must be atomic: a failed token update cannot
            // leave the user with a newly persisted password hash.
            ResponseEntity<String> originalLogin = postJson(
                    baseUrl + "/login",
                    Map.of("email", email, "password", OLD_PASSWORD)
            );
            assertThat(originalLogin.getStatusCode())
                    .as("Failed reset must preserve the original password")
                    .isEqualTo(HttpStatus.OK);

            ResponseEntity<String> newPasswordLogin = postJson(
                    baseUrl + "/login",
                    Map.of("email", email, "password", NEW_PASSWORD)
            );
            assertThat(newPasswordLogin.getStatusCode())
                    .as("Failed reset must not activate the new password")
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            // Cleanup must run even when the RED assertion fails.
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS e2e_fail_reset_use
                    ON password_reset_tokens
                    """);
            jdbcTemplate.execute("""
                    DROP FUNCTION IF EXISTS e2e_reject_reset_token_use()
                    """);
        }
    }

    @Test
    void shouldRejectSupersededResetTokenWithoutChangingPassword()
            throws Exception {
        String baseUrl = "http://localhost:" + port + "/api/v1/auth";
        String email = "superseded.reset.e2e@example.com";
        String replacementToken = "e2e-replacement-password-reset-token";

        ResponseEntity<String> registered = postJson(
                baseUrl + "/register",
                Map.of(
                        "fullName", "Superseded Reset Patient",
                        "email", email,
                        "identityDocument", "RECOVERY-SUPERSEDED-103",
                        "password", OLD_PASSWORD
                )
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> requested = postJson(
                baseUrl + "/password-recovery",
                Map.of("email", email)
        );
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Simulate issuance of a different token using the official DB trigger.
        // The production test provider is deterministic, so a second HTTP
        // request would reuse the same token hash and violate its UNIQUE key.
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                SELECT user_id, ?, ?
                FROM password_reset_tokens
                WHERE token_hash = ?
                """,
                sha256(replacementToken),
                java.sql.Timestamp.from(
                        Instant.now().plus(Duration.ofMinutes(30))
                ),
                sha256(RESET_TOKEN)
        );
        assertThat(inserted).isEqualTo(1);

        Integer supersededCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM password_reset_tokens
                WHERE token_hash = ?
                  AND used = FALSE
                  AND superseded_at IS NOT NULL
                """,
                Integer.class,
                sha256(RESET_TOKEN)
        );
        assertThat(supersededCount)
                .as("The official DB trigger must supersede the older token")
                .isEqualTo(1);

        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM password_reset_tokens
                WHERE token_hash = ?
                  AND used = FALSE
                  AND superseded_at IS NULL
                """,
                Integer.class,
                sha256(replacementToken)
        );
        assertThat(activeCount)
                .as("The replacement token must be active")
                .isEqualTo(1);

        ResponseEntity<String> rejected = postJson(
                baseUrl + "/password-reset",
                Map.of("token", RESET_TOKEN, "newPassword", NEW_PASSWORD)
        );
        assertThat(rejected.getStatusCode())
                .as("Superseded reset tokens must be rejected as invalid")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        JsonNode error = objectMapper.readTree(rejected.getBody());
        assertThat(error.path("error").asText())
                .isEqualTo("INVALID_RESET_TOKEN");

        ResponseEntity<String> oldPasswordLogin = postJson(
                baseUrl + "/login",
                Map.of("email", email, "password", OLD_PASSWORD)
        );
        assertThat(oldPasswordLogin.getStatusCode())
                .as("An invalidated recovery token must not change the password")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> replacementReset = postJson(
                baseUrl + "/password-reset",
                Map.of("token", replacementToken, "newPassword", NEW_PASSWORD)
        );
        assertThat(replacementReset.getStatusCode())
                .as("The active replacement token must still work")
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> newPasswordLogin = postJson(
                baseUrl + "/login",
                Map.of("email", email, "password", NEW_PASSWORD)
        );
        assertThat(newPasswordLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAllowOnlyOneConcurrentPasswordReset()
            throws Exception {
        String baseUrl =
                "http://localhost:" + port + "/api/v1/auth";
        String email = "concurrent.reset.e2e@example.com";
        String firstPassword = "ConcurrentFirst456!";
        String secondPassword = "ConcurrentSecond789!";

        ResponseEntity<String> registered = postJson(
                baseUrl + "/register",
                Map.of(
                        "fullName", "Concurrent Reset Patient",
                        "email", email,
                        "identityDocument", "RECOVERY-CONCURRENT-104",
                        "password", OLD_PASSWORD
                )
        );
        assertThat(registered.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> requested = postJson(
                baseUrl + "/password-recovery",
                Map.of("email", email)
        );
        assertThat(requested.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ResponseEntity<String> firstResponse;
        ResponseEntity<String> secondResponse;

        try {
            Future<ResponseEntity<String>> first = executor.submit(
                    () -> concurrentReset(
                            baseUrl,
                            firstPassword,
                            ready,
                            start
                    )
            );
            Future<ResponseEntity<String>> second = executor.submit(
                    () -> concurrentReset(
                            baseUrl,
                            secondPassword,
                            ready,
                            start
                    )
            );

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("Both reset requests must be ready")
                    .isTrue();

            start.countDown();

            firstResponse = first.get(45, TimeUnit.SECONDS);
            secondResponse = second.get(45, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(new Object[]{
                firstResponse.getStatusCode(),
                secondResponse.getStatusCode()
        }).containsExactlyInAnyOrder(
                HttpStatus.NO_CONTENT,
                HttpStatus.UNAUTHORIZED
        );

        boolean firstSucceeded =
                firstResponse.getStatusCode().equals(HttpStatus.NO_CONTENT);

        String winningPassword =
                firstSucceeded ? firstPassword : secondPassword;
        String losingPassword =
                firstSucceeded ? secondPassword : firstPassword;

        ResponseEntity<String> rejected =
                firstSucceeded ? secondResponse : firstResponse;
        JsonNode error = objectMapper.readTree(rejected.getBody());
        assertThat(error.path("error").asText())
                .isEqualTo("INVALID_RESET_TOKEN");

        Integer consumedTokens = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM password_reset_tokens
                WHERE token_hash = ?
                  AND used = TRUE
                """,
                Integer.class,
                sha256(RESET_TOKEN)
        );
        assertThat(consumedTokens)
                .as("Exactly one reset token must be consumed")
                .isEqualTo(1);

        ResponseEntity<String> winningLogin = postJson(
                baseUrl + "/login",
                Map.of("email", email, "password", winningPassword)
        );
        assertThat(winningLogin.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> losingLogin = postJson(
                baseUrl + "/login",
                Map.of("email", email, "password", losingPassword)
        );
        assertThat(losingLogin.getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> originalLogin = postJson(
                baseUrl + "/login",
                Map.of("email", email, "password", OLD_PASSWORD)
        );
        assertThat(originalLogin.getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> concurrentReset(
            String baseUrl,
            String newPassword,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();

        if (!start.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Concurrent password reset did not start."
            );
        }

        return postJson(
                baseUrl + "/password-reset",
                Map.of(
                        "token", RESET_TOKEN,
                        "newPassword", newPassword
                )
        );
    }

    @Test
    void shouldRecordRecoveryEventInOutboxWithIssuedToken()
            throws Exception {
        String baseUrl =
                "http://localhost:" + port + "/api/v1/auth";
        String email =
                "recovery.outbox@example.com";

        registerPatient(baseUrl, email, "RECOVERY-OUTBOX-100");

        ResponseEntity<String> recoveryResponse =
                postJson(
                        baseUrl + "/password-recovery",
                        Map.of("email", email)
                );

        assertThat(recoveryResponse.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT e.event_type,
                               e.aggregate_id,
                               e.payload::text AS payload,
                               e.published_at,
                               u.id AS user_id,
                               t.id AS token_id
                        FROM identity_outbox_event e
                        JOIN users u ON u.id = e.aggregate_id
                        JOIN password_reset_tokens t ON t.token_hash = ?
                        WHERE u.email = ?
                        """,
                        sha256(RESET_TOKEN),
                        email
                );

        assertThat(row.get("event_type"))
                .isEqualTo("PasswordRecoveryRequested");
        assertThat(row.get("aggregate_id"))
                .isEqualTo(row.get("user_id"));
        assertThat(row.get("published_at")).isNull();

        JsonNode payload =
                objectMapper.readTree(
                        (String) row.get("payload")
                );

        assertThat(payload.get("email").asText())
                .isEqualTo(email);
        assertThat(payload.get("recoveryTokenId").asText())
                .isEqualTo(row.get("token_id").toString());
        assertThat(payload.get("resetUrl").asText())
                .isEqualTo(RESET_URL + "#token=" + RESET_TOKEN);
        assertThat(payload.has("tokenHash")).isFalse();
    }

    @Test
    void shouldNotRecordRecoveryEventForUnknownEmail() {
        String baseUrl =
                "http://localhost:" + port + "/api/v1/auth";
        String email =
                "unknown.outbox@example.com";

        ResponseEntity<String> recoveryResponse =
                postJson(
                        baseUrl + "/password-recovery",
                        Map.of("email", email)
                );

        assertThat(recoveryResponse.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        Integer events =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM identity_outbox_event
                        WHERE payload ->> 'email' = ?
                        """,
                        Integer.class,
                        email
                );

        assertThat(events).isZero();
    }

    @Test
    void shouldNotKeepResetTokenWhenOutboxEventCannotBeRecorded() {
        String baseUrl =
                "http://localhost:" + port + "/api/v1/auth";
        String email =
                "recovery.outbox.failure@example.com";

        registerPatient(baseUrl, email, "RECOVERY-OUTBOX-200");

        jdbcTemplate.execute(
                """
                CREATE FUNCTION e2e_reject_outbox_event()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'E2E outbox failure';
                END
                $$
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TRIGGER e2e_reject_outbox_event
                BEFORE INSERT ON identity_outbox_event
                FOR EACH ROW
                EXECUTE FUNCTION e2e_reject_outbox_event()
                """
        );

        try {
            ResponseEntity<String> recoveryResponse =
                    postJson(
                            baseUrl + "/password-recovery",
                            Map.of("email", email)
                    );

            assertThat(recoveryResponse.getStatusCode())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS e2e_reject_outbox_event "
                            + "ON identity_outbox_event"
            );
            jdbcTemplate.execute(
                    "DROP FUNCTION IF EXISTS e2e_reject_outbox_event()"
            );
        }

        Integer tokens =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM password_reset_tokens
                        WHERE token_hash = ?
                        """,
                        Integer.class,
                        sha256(RESET_TOKEN)
                );

        assertThat(tokens).isZero();
    }

    private void registerPatient(
            String baseUrl,
            String email,
            String identityDocument
    ) {
        ResponseEntity<String> registerResponse =
                postJson(
                        baseUrl + "/register",
                        Map.of(
                                "fullName", "Recovery Outbox Patient",
                                "email", email,
                                "identityDocument", identityDocument,
                                "password", OLD_PASSWORD
                        )
                );

        assertThat(registerResponse.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> postJson(
            String url,
            Object body
    ) {
        HttpHeaders headers =
                new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_JSON
        );
        return http.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(
                        body,
                        headers
                ),
                String.class
        );
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance(
                            "RSA"
                    );
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate RSA test keys.",
                    exception
            );

        }
    }

    private static String privateKeyPem(
            KeyPair keyPair
    ) {
        return pem(
                "PRIVATE KEY",
                keyPair.getPrivate()
                        .getEncoded()
        );
    }

    private static String publicKeyPem(
            KeyPair keyPair
    ) {
        return pem(
                "PUBLIC KEY",
                keyPair.getPublic()
                        .getEncoded()
        );
    }

    private static String pem(
            String label,
            byte[] encoded
    ) {
        String body =
                Base64.getMimeEncoder(
                                64,
                                new byte[]{'\n'}
                        )
                        .encodeToString(
                                encoded
                        );
        return "-----BEGIN "
                + label
                + "-----\n"
                + body
                + "\n-----END "
                + label
                + "-----";
    }

    private static String sha256(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );
            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    value.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to hash E2E reset token.",
                    exception
            );

        }
    }

    @TestConfiguration
    static class ResetTokenTestConfiguration {
        @Bean
        @Primary
        PasswordResetTokenProviderPort
        deterministicPasswordResetTokenProvider() {
            return () ->
                    new PasswordResetTokenProviderPort
                            .IssuedPasswordResetToken(
                            RESET_TOKEN,
                            sha256(
                                    RESET_TOKEN
                            ),
                            Instant.now()
                                    .plus(
                                            Duration.ofMinutes(30)
                                    )
                    );

        }
    }

}
