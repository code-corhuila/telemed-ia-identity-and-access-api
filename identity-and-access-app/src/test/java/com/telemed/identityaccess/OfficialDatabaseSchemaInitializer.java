package com.telemed.identityaccess;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/** Initializes the ephemeral E2E database from the owning DB repository. */
final class OfficialDatabaseSchemaInitializer {

    private static final String MIGRATIONS_DIR_ENV = "IDENTITY_DB_MIGRATIONS_DIR";
    private static final String CHANGELOG = "changelog/changelog-master.yaml";

    private OfficialDatabaseSchemaInitializer() {
    }

    static void migrate(PostgreSQLContainer<?> postgres) {
        String configuredDir = System.getenv(MIGRATIONS_DIR_ENV);
        if (configuredDir == null || configuredDir.isBlank()) {
            throw new IllegalStateException(
                    MIGRATIONS_DIR_ENV + " is required for official-schema E2E tests."
            );
        }

        Path root = Path.of(configuredDir).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(CHANGELOG))) {
            throw new IllegalStateException("Missing official DB changelog in " + root);
        }

        try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(root)) {

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDatabaseChangeLogTableName("databasechangelog_identity_and_access");
            database.setDatabaseChangeLogLockTableName("databasechangeloglock_identity_and_access");

            try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not apply official Identity & Access migrations for E2E tests.",
                    exception
            );
        }
    }
}
