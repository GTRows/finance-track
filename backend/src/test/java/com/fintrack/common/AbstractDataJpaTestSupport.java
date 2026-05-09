package com.fintrack.common;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for {@code @DataJpaTest} suites that need a real Postgres so JSONB columns, partial
 * indexes, and Postgres-specific functions actually behave like production. Uses the Testcontainers
 * <em>singleton container</em> pattern: the container is started once via a static initializer and
 * reused across every subclass for the whole JVM lifetime, with Ryuk handling cleanup at exit.
 * Avoids the per-class start/stop cost of {@code @Testcontainers}, which made CI runs hit the job
 * timeout.
 *
 * <p>Subclasses must annotate themselves with
 * {@code @EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")} so the suite
 * stays green on hosts without Docker.
 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractDataJpaTestSupport {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("fintrack")
                    .withUsername("fintrack")
                    .withPassword("fintrack");

    static {
        if (dockerAvailable()) {
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @SuppressWarnings("unused")
    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
