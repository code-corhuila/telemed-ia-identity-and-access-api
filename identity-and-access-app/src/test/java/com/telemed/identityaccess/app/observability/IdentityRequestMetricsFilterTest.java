package com.telemed.identityaccess.app.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/** Week 11 metric catalog (CP4): traffic, errors and latency of the identity API by route template. */
class IdentityRequestMetricsFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IdentityRequestMetricsFilter filter = new IdentityRequestMetricsFilter(registry);

    @Test
    void countsEachRequestByRouteTemplateMethodAndOutcome() throws Exception {
        serve("POST", "/api/v1/auth/login", "/api/v1/auth/login", 200);
        serve("POST", "/api/v1/auth/login", "/api/v1/auth/login", 401);
        serve("POST", "/api/v1/auth/password-recovery", "/api/v1/auth/password-recovery", 503);

        assertThat(count("/api/v1/auth/login", "POST", "success")).isEqualTo(1.0);
        assertThat(count("/api/v1/auth/login", "POST", "client_error")).isEqualTo(1.0);
        assertThat(count("/api/v1/auth/password-recovery", "POST", "error")).isEqualTo(1.0);
        assertThat(registry.get("identity.request.duration").tags("route", "/api/v1/auth/login").timer().count())
                .isEqualTo(2);
    }

    @Test
    void labelsTheTemplateNeverTheRawPath() throws Exception {
        serve("PATCH", "/api/v1/admin/accounts/11111111-1111-4111-8111-111111111111/status",
                "/api/v1/admin/accounts/{accountId}/status", 204);

        assertThat(count("/api/v1/admin/accounts/{accountId}/status", "PATCH", "success")).isEqualTo(1.0);
        assertThat(registry.getMeters()).noneMatch(meter ->
                String.valueOf(meter.getId().getTag("route")).contains("11111111"));
    }

    @Test
    void groupsUnknownPathsUnderOneSeries() throws Exception {
        serve("GET", "/api/v1/auth/does-not-exist", null, 404);

        assertThat(count("unmatched", "GET", "client_error")).isEqualTo(1.0);
    }

    @Test
    void leavesActuatorRequestsOut() throws Exception {
        serve("GET", "/actuator/health", "/actuator/health", 200);

        assertThat(registry.find("identity.requests").counters()).isEmpty();
    }

    private void serve(String method, String path, String template, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            if (template != null) {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, template);
            }
            ((MockHttpServletResponse) res).setStatus(status);
        };
        filter.doFilter(request, response, chain);
    }

    private double count(String route, String method, String outcome) {
        return registry.get("identity.requests")
                .tags("route", route, "method", method, "outcome", outcome)
                .counter()
                .count();
    }
}
