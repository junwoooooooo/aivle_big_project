package com.aivle.backend.postgres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@Tag("postgres")
class DockerAvailabilityPolicyTests {
    @Test
    void continuousIntegrationRequiresDocker() {
        boolean continuousIntegration =
            "true".equalsIgnoreCase(System.getenv("CI"));
        if (!continuousIntegration) {
            assumeFalse(continuousIntegration, "Docker is mandatory only in CI");
        }
        assertThat(DockerClientFactory.instance().isDockerAvailable()).isTrue();
    }
}
