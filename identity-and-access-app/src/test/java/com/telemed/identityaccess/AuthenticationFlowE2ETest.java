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

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "management.server.port=0"
        }
)
class AuthenticationFlowE2ETest {

    private static final String PASSWORD =
            "Secret123!";

    private static final KeyPair RSA_KEY_PAIR =
            generateRsaKeyPair();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:16.4-alpine"
            )
                    .withDatabaseName(
                            "telemed_identity_test"
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

    @Test
    void shouldCompleteAuthenticationFlow()
            throws Exception {

        String baseUrl =
                "http://localhost:"
                        + port
                        + "/api/v1/auth";

        String email =
                "e2e.patient@example.com";

        String identityDocument =
                "E2E-DOC-100";

        /*
         * 1. REGISTER
         */
        ResponseEntity<String> registerResponse =
                postJson(
                        baseUrl + "/register",
                        Map.of(
                                "fullName",
                                "E2E Patient",
                                "email",
                                email,
                                "identityDocument",
                                identityDocument,
                                "password",
                                PASSWORD
                        )
                );

        assertThat(
                registerResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.CREATED
        );

        JsonNode registered =
                objectMapper.readTree(
                        registerResponse.getBody()
                );

        String userId =
                registered.get("userId")
                        .asText();

        assertThat(
                registered.get("role")
                        .asText()
        ).isEqualTo(
                "PATIENT"
        );

        /*
         * 2. LOGIN
         */
        ResponseEntity<String> loginResponse =
                postJson(
                        baseUrl + "/login",
                        Map.of(
                                "email",
                                email,
                                "password",
                                PASSWORD
                        )
                );

        assertThat(
                loginResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.OK
        );

        JsonNode login =
                objectMapper.readTree(
                        loginResponse.getBody()
                );

        String firstAccessToken =
                login.get("accessToken")
                        .asText();

        String firstRefreshToken =
                login.get("refreshToken")
                        .asText();

        assertThat(firstAccessToken)
                .isNotBlank();

        assertThat(firstRefreshToken)
                .isNotBlank();

        /*
         * 3. SESSION WITH FIRST ACCESS TOKEN
         */
        ResponseEntity<String> firstSessionResponse =
                getWithBearer(
                        baseUrl + "/session",
                        firstAccessToken
                );

        assertThat(
                firstSessionResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.OK
        );

        JsonNode firstSession =
                objectMapper.readTree(
                        firstSessionResponse.getBody()
                );

        assertThat(
                firstSession.get("userId")
                        .asText()
        ).isEqualTo(
                userId
        );

        assertThat(
                firstSession.get("role")
                        .asText()
        ).isEqualTo(
                "PATIENT"
        );

        /*
         * 4. REFRESH / ROTATION
         */
        ResponseEntity<String> refreshResponse =
                postJson(
                        baseUrl + "/refresh",
                        Map.of(
                                "refreshToken",
                                firstRefreshToken
                        )
                );

        assertThat(
                refreshResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.OK
        );

        JsonNode refresh =
                objectMapper.readTree(
                        refreshResponse.getBody()
                );

        String secondAccessToken =
                refresh.get("accessToken")
                        .asText();

        String secondRefreshToken =
                refresh.get("refreshToken")
                        .asText();

        assertThat(secondAccessToken)
                .isNotBlank();

        assertThat(secondRefreshToken)
                .isNotBlank();

        assertThat(secondRefreshToken)
                .isNotEqualTo(
                        firstRefreshToken
                );

        /*
         * 5. OLD REFRESH TOKEN MUST NOT BE REUSABLE
         */
        ResponseEntity<String> reusedRefreshResponse =
                postJson(
                        baseUrl + "/refresh",
                        Map.of(
                                "refreshToken",
                                firstRefreshToken
                        )
                );

        assertThat(
                reusedRefreshResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.UNAUTHORIZED
        );

        JsonNode reusedRefreshError =
                objectMapper.readTree(
                        reusedRefreshResponse.getBody()
                );

        assertThat(
                reusedRefreshError.get("error")
                        .asText()
        ).isEqualTo(
                "INVALID_REFRESH_TOKEN"
        );

        /*
         * 6. SESSION WITH ROTATED ACCESS TOKEN
         */
        ResponseEntity<String> secondSessionResponse =
                getWithBearer(
                        baseUrl + "/session",
                        secondAccessToken
                );

        assertThat(
                secondSessionResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.OK
        );

        JsonNode secondSession =
                objectMapper.readTree(
                        secondSessionResponse.getBody()
                );

        assertThat(
                secondSession.get("userId")
                        .asText()
        ).isEqualTo(
                userId
        );

        /*
         * 7. LOGOUT
         */
        ResponseEntity<String> logoutResponse =
                postJson(
                        baseUrl + "/logout",
                        Map.of(
                                "refreshToken",
                                secondRefreshToken
                        )
                );

        assertThat(
                logoutResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.NO_CONTENT
        );

        /*
         * 8. LOGGED-OUT REFRESH TOKEN MUST NOT WORK
         */
        ResponseEntity<String> afterLogoutResponse =
                postJson(
                        baseUrl + "/refresh",
                        Map.of(
                                "refreshToken",
                                secondRefreshToken
                        )
                );

        assertThat(
                afterLogoutResponse.getStatusCode()
        ).isEqualTo(
                HttpStatus.UNAUTHORIZED
        );

        JsonNode afterLogoutError =
                objectMapper.readTree(
                        afterLogoutResponse.getBody()
                );

        assertThat(
                afterLogoutError.get("error")
                        .asText()
        ).isEqualTo(
                "INVALID_REFRESH_TOKEN"
        );
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

    private ResponseEntity<String> getWithBearer(
            String url,
            String accessToken
    ) {

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(
                accessToken
        );

        return http.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(
                        null,
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
}