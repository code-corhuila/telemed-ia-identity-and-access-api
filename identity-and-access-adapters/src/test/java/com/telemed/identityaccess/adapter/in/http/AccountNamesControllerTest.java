package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccountNamesNotAllowedException;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase.AccountName;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort.VerifiedAccessToken;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEC-013: GET /api/v1/auth/accounts/names for professionals and administrators. */
@WebMvcTest(AccountNamesController.class)
@ContextConfiguration(classes = {
        AccountNamesController.class,
        AuthFilter.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class AccountNamesControllerTest {

    private static final String PATH = "/api/v1/auth/accounts/names";

    private static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LookupAccountNamesUseCase lookupAccountNames;

    @MockBean
    private AccessTokenVerifierPort accessTokens;

    @BeforeEach
    void tokens() {
        Instant expiresAt = Instant.parse("2026-11-11T18:00:00Z");
        when(accessTokens.verify("doctor-token"))
                .thenReturn(new VerifiedAccessToken(DOCTOR_ID, Role.PROFESSIONAL, "jti-doctor", expiresAt));
        when(accessTokens.verify("patient-token"))
                .thenReturn(new VerifiedAccessToken(PATIENT_ID, Role.PATIENT, "jti-patient", expiresAt));
    }

    @Test
    void answersTheNamesFoundForTheCallerRole() throws Exception {
        when(lookupAccountNames.lookup(Role.PROFESSIONAL, Set.of(PATIENT_ID, OTHER_PATIENT_ID)))
                .thenReturn(List.of(new AccountName(PATIENT_ID, "Laura Gómez")));

        mockMvc.perform(get(PATH)
                        .header("Authorization", "Bearer doctor-token")
                        .param("ids", PATIENT_ID + ", " + OTHER_PATIENT_ID + "," + PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].fullName").value("Laura Gómez"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(get(PATH).param("ids", PATIENT_ID.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        verify(lookupAccountNames, never()).lookup(any(), any());
    }

    @Test
    void forbidsTheRolesTheUseCaseRefuses() throws Exception {
        when(lookupAccountNames.lookup(Role.PATIENT, Set.of(DOCTOR_ID)))
                .thenThrow(new AccountNamesNotAllowedException());

        mockMvc.perform(get(PATH)
                        .header("Authorization", "Bearer patient-token")
                        .param("ids", DOCTOR_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsAMissingEmptyMalformedOrTooLongList() throws Exception {
        String fiftyOne = IntStream.range(0, 51)
                .mapToObj(index -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));

        for (String ids : new String[] {null, "", " , ", "not-a-uuid", PATIENT_ID + ",nope", fiftyOne}) {
            var request = get(PATH).header("Authorization", "Bearer doctor-token");
            if (ids != null) {
                request = request.param("ids", ids);
            }
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("ids"));
        }

        verify(lookupAccountNames, never()).lookup(any(), any());
    }
}
