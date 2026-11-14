package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.EXPIRED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@ContextConfiguration(classes = {
        SessionController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class,
        AuthFilter.class,
        ApiErrorWriter.class
})
class SessionControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "44444444-4444-4444-8444-444444444444"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessTokenVerifierPort verifier;

    @Test
    void shouldReturnAuthenticatedSessionForValidBearerToken()
            throws Exception {

        when(verifier.verify("valid-rs256-token"))
                .thenReturn(
                        new AccessTokenVerifierPort.VerifiedAccessToken(
                                USER_ID,
                                Role.PROFESSIONAL,
                                "token-id-123",
                                Instant.parse("2026-09-30T23:00:00Z")
                        )
                );

        mockMvc.perform(
                        get("/api/v1/auth/session")
                                .header(
                                        "Authorization",
                                        "Bearer valid-rs256-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.userId")
                                .value(USER_ID.toString())
                )
                .andExpect(
                        jsonPath("$.role")
                                .value("PROFESSIONAL")
                );

        verify(verifier)
                .verify("valid-rs256-token");
    }

    @Test
    void shouldRejectSessionWithoutBearerToken()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/auth/session")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error")
                                .value("UNAUTHORIZED")
                )
                .andExpect(
                        jsonPath("$.traceId")
                                .isNotEmpty()
                );

        verify(verifier, never())
                .verify(any());
    }

    @Test
    void shouldRejectExpiredAccessToken()
            throws Exception {

        when(verifier.verify("expired-token"))
                .thenThrow(
                        new AccessTokenVerificationException(
                                EXPIRED,
                                "Access token has expired."
                        )
                );

        mockMvc.perform(
                        get("/api/v1/auth/session")
                                .header(
                                        "Authorization",
                                        "Bearer expired-token"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error")
                                .value("UNAUTHORIZED")
                );

        verify(verifier)
                .verify("expired-token");
    }

    @Test
    void shouldRejectBlankBearerToken()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/auth/session")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                )
                )
                .andExpect(status().isUnauthorized());

        verify(verifier, never())
                .verify(any());
    }
}