package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "security.password-reset-token.ttl=PT30M",
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

    private static final KeyPair RSA_KEY_PAIR =
            generateRsaKeyPair();

    @Container
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
                    )
                    .withInitScript(
                            "e2e/identity-schema.sql"
                    );

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {

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
        // Both E2E scenarios use the same deterministic token provider.
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