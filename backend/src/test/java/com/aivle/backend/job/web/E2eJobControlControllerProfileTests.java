package com.aivle.backend.job.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.aivle.backend.job.runner.JobRunner;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class E2eJobControlControllerProfileTests {

    @Test
    void controllerIsRegisteredOnlyForE2eWithRunnerEnabled() {
        try (var e2e = context("e2e", true)) {
            assertTrue(e2e.containsBeanDefinition(
                "e2eJobControlController"
            ));
        }
        try (var prod = context("prod", true)) {
            assertFalse(prod.containsBeanDefinition(
                "e2eJobControlController"
            ));
        }
        try (var postgres = context("postgres", true)) {
            assertFalse(postgres.containsBeanDefinition(
                "e2eJobControlController"
            ));
        }
        try (var disabled = context("e2e", false)) {
            assertFalse(disabled.containsBeanDefinition(
                "e2eJobControlController"
            ));
        }
    }

    private AnnotationConfigApplicationContext context(
        String profile,
        boolean runnerEnabled
    ) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "app.jobs.document-processing.enabled",
                    Boolean.toString(runnerEnabled)
                )
            )
        );
        context.registerBean(JobRunner.class, () -> mock(JobRunner.class));
        context.register(E2eJobControlController.class);
        context.refresh();
        return context;
    }
}
