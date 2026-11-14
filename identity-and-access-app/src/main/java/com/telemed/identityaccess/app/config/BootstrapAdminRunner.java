package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase;
import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

/**
 * Creates the first ADMIN at startup when BOOTSTRAP_ADMIN_* is set. It never
 * changes an existing account: editing the variables later has no effect.
 */
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminProperties properties;
    private final BootstrapAdminUseCase bootstrapAdmin;

    public BootstrapAdminRunner(
            BootstrapAdminProperties properties,
            BootstrapAdminUseCase bootstrapAdmin
    ) {
        this.properties = properties;
        this.bootstrapAdmin = bootstrapAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (properties.isEmpty()) {
            return;
        }

        List<String> missing = properties.missingVariables();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Bootstrap admin needs all BOOTSTRAP_ADMIN_* variables or none; missing: "
                            + String.join(", ", missing)
            );
        }

        Command command;
        try {
            command = new Command(
                    properties.fullName(),
                    properties.email(),
                    properties.identityDocument(),
                    properties.password()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Bootstrap admin is misconfigured: " + exception.getMessage()
            );
        }

        switch (bootstrapAdmin.bootstrap(command)) {
            case CREATED -> LOGGER.info("bootstrap admin created");
            case ALREADY_PRESENT -> LOGGER.info("bootstrap admin already present");
            case ACCOUNT_IN_USE -> LOGGER.warn(
                    "bootstrap admin skipped: its email or identity document "
                            + "belongs to another account, which is never promoted"
            );
        }
    }
}
