package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.IssueServiceTokenUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A request the caller got wrong is a 4xx in the common envelope, never a 500. */
@WebMvcTest(ServiceTokenController.class)
@ContextConfiguration(classes = {
        ServiceTokenController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class ClientErrorResponsesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IssueServiceTokenUseCase issueServiceToken;

    @Test
    void answersMethodNotAllowedForAnUnsupportedMethod() throws Exception {
        mockMvc.perform(get("/internal/service-token")
                        .header("X-Correlation-Id", "trace-0405"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").value("trace-0405"));
    }

    @Test
    void answersUnsupportedMediaTypeForABodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/internal/service-token")
                        .header("X-Correlation-Id", "trace-0415")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("clientId=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.traceId").value("trace-0415"));
    }

    @Test
    void answersNotFoundForAnUnknownPath() throws Exception {
        mockMvc.perform(get("/api/v1/auth/does-not-exist")
                        .header("X-Correlation-Id", "trace-0404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-0404"));
    }
}
