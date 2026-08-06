package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LegacyPipelineSurfaceConditionTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(LegacyControllerConfiguration.class);

    @Test
    void legacySurfaceIsDisabledByDefaultAndRequiresExplicitOptIn() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("legacyControllerMarker"));

        contextRunner
            .withPropertyValues("app.legacy-pipeline.enabled=true")
            .run(context -> assertThat(context).hasBean("legacyControllerMarker"));
    }

    @Configuration(proxyBeanMethods = false)
    @LegacyPipelineSurface
    static class LegacyControllerConfiguration {
        @Bean
        String legacyControllerMarker() {
            return "legacy";
        }
    }
}
