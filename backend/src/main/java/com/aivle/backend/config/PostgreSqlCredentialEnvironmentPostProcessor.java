package com.aivle.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.Arrays;

public class PostgreSqlCredentialEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(
        ConfigurableEnvironment environment,
        SpringApplication application
    ) {
        boolean postgresProfile = Arrays.asList(environment.getActiveProfiles())
            .contains("postgres");
        if (postgresProfile
            && !StringUtils.hasText(environment.getProperty("DB_PASSWORD"))) {
            throw new IllegalStateException(
                "DB_PASSWORD is required when the postgres profile is active"
            );
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
