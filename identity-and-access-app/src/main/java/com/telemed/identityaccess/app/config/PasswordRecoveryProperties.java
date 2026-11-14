package com.telemed.identityaccess.app.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * @param resetUrl absolute page of the portal that completes the reset; the
 *                 token is appended as the {@code #token=} fragment
 */
@Validated
@ConfigurationProperties(prefix = "security.password-recovery")
public record PasswordRecoveryProperties(
        @NotBlank String resetUrl
) {

    public PasswordRecoveryProperties {
        if (resetUrl != null && !resetUrl.isBlank()) {
            URI uri = URI.create(resetUrl);

            if (!("https".equals(uri.getScheme())
                    || "http".equals(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException(
                        "Password reset URL must be an absolute "
                                + "http(s) URL without a fragment."
                );
            }
        }
    }
}
