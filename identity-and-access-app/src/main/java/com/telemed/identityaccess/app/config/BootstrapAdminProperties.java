package com.telemed.identityaccess.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The first administrator (BOOTSTRAP_ADMIN_*). All four values, or none.
 */
@ConfigurationProperties(prefix = "security.bootstrap-admin")
public record BootstrapAdminProperties(
        String fullName,
        String email,
        String identityDocument,
        String password
) {

    boolean isEmpty() {
        return missingVariables().size() == 4;
    }

    List<String> missingVariables() {
        List<String> missing = new ArrayList<>();
        addIfBlank(missing, fullName, "BOOTSTRAP_ADMIN_FULL_NAME");
        addIfBlank(missing, email, "BOOTSTRAP_ADMIN_EMAIL");
        addIfBlank(missing, identityDocument, "BOOTSTRAP_ADMIN_IDENTITY_DOCUMENT");
        addIfBlank(missing, password, "BOOTSTRAP_ADMIN_PASSWORD");
        return missing;
    }

    private static void addIfBlank(
            List<String> missing,
            String value,
            String variable
    ) {
        if (value == null || value.isBlank()) {
            missing.add(variable);
        }
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[fullName=%s, email=%s, identityDocument=%s, password=***]"
                .formatted(fullName, email, identityDocument);
    }
}
