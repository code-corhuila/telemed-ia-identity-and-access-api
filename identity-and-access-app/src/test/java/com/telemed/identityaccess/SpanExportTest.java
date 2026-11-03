package com.telemed.identityaccess;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 11 traces (CP3): identity's request spans go to the platform collector (and from there to
 * Jaeger) with the ids the logs and the response carry, so a trace_id found in a log line opens
 * the same request in Jaeger.
 */
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
@Import(ExportedSpans.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "management.server.port=0",
                "management.otlp.tracing.export.enabled=false"
        }
)
class SpanExportTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CALLER_SPAN = "00f067aa0ba902b7";
    private static final KeyPair RSA_KEY_PAIR = generateRsaKeyPair();

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_identity_span_test")
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
    private InMemorySpanExporter spans;
    @Autowired
    private ObjectProvider<SdkTracerProvider> tracerProvider;

    @LocalServerPort
    private int port;

    @BeforeEach
    void forgetEarlierSpans() {
        spans.reset();
    }

    @Test
    void theRequestSpanContinuesTheCallersTraceWithTheIdsOfTheLogsAndTheResponse(CapturedOutput output)
            throws InterruptedException {
        ResponseEntity<String> response = login("00-" + TRACE_ID + "-" + CALLER_SPAN + "-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        List<SpanData> request = ExportedSpans.await(spans, tracerProvider,
                span -> span.getTraceId().equals(TRACE_ID) && span.getParentSpanContext().isRemote());
        assertThat(request).singleElement().satisfies(span -> {
            assertThat(span.getParentSpanId()).isEqualTo(CALLER_SPAN);
            assertThat(response.getHeaders().getFirst("traceparent"))
                    .isEqualTo("00-" + TRACE_ID + "-" + span.getSpanId() + "-01");
            assertThat(output.getOut()).contains("\"span_id\":\"" + span.getSpanId() + "\"");
        });
    }

    @Test
    void aRequestWithoutTraceStartsOneThatIsExportedToo() throws InterruptedException {
        ResponseEntity<String> response = login(null);

        String traceId = response.getHeaders().getFirst("traceparent").split("-")[1];
        assertThat(ExportedSpans.await(spans, tracerProvider, span -> span.getTraceId().equals(traceId)))
                .isNotEmpty();
    }

    // Wrong credentials: answered 401 after a read of identity-db, inside the request span.
    private ResponseEntity<String> login(String traceparent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (traceparent != null) {
            headers.set("traceparent", traceparent);
        }
        return http.postForEntity("http://localhost:" + port + "/api/v1/auth/login",
                new HttpEntity<>("{\"email\":\"nobody@example.com\",\"password\":\"WrongPassword123!\"}", headers),
                String.class);
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
