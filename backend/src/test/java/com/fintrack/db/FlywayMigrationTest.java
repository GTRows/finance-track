package com.fintrack.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fintrack")
            .withUsername("fintrack")
            .withPassword("fintrack");

    @Test
    void allMigrationsApplyCleanly() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();

        MigrationInfo[] pending = flyway.info().pending();
        assertThat(pending).isEmpty();

        flyway.validate();
    }
}
