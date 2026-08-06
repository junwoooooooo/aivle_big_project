package com.aivle.backend.postgres;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgreSqlIntegrationTestSupport {
    public static final String POSTGRES_IMAGE = "postgres:17.10-alpine";

    protected static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("aivle_phase2")
            .withUsername("aivle")
            .withPassword("aivle-test-only")
            .withEnv("TZ", "UTC")
            .withEnv("PGTZ", "UTC")
            .withEnv("LANG", "C.UTF-8");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.datasource.hikari.connection-init-sql",
            () -> "SET TIME ZONE 'UTC'");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.jobs.document-processing.enabled", () -> false);
        registry.add("app.ai.enabled", () -> false);
    }
}
