package com.aivle.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AiServerTestControllerProfileTests {

    @Test
    void controllerIsNotRegisteredInProd() {
        try (
            AnnotationConfigApplicationContext context =
                context("prod")
        ) {
            assertFalse(
                context.containsBeanDefinition(
                    "aiServerTestController"
                )
            );
        }
    }

    @Test
    void controllerIsRegisteredInLocal() {
        try (
            AnnotationConfigApplicationContext context =
                context("local")
        ) {
            assertTrue(
                context.containsBeanDefinition(
                    "aiServerTestController"
                )
            );
        }
    }

    private AnnotationConfigApplicationContext context(
        String profile
    ) {
        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.registerBean(
            AiServerHealthClient.class,
            () -> mock(AiServerHealthClient.class)
        );
        context.registerBean(
            AiServerMarketingClient.class,
            () -> mock(AiServerMarketingClient.class)
        );
        context.register(AiServerTestController.class);
        context.refresh();
        return context;
    }
}
