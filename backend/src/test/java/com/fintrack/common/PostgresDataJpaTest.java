package com.fintrack.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Meta-annotation that combines {@link DataJpaTest} with {@link AutoConfigureTestDatabase}{@code
 * (replace = NONE)} so subclasses of {@link AbstractDataJpaTestSupport} keep the Testcontainers
 * Postgres datasource instead of falling back to an embedded H2 that is not on the classpath.
 *
 * <p>Use this on every {@code *RepositoryDataJpaTest}; do not annotate with raw
 * {@code @DataJpaTest} alone, or Spring will try to replace the datasource and fail to bootstrap on
 * hosts that have Docker available (e.g. CI).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public @interface PostgresDataJpaTest {}
