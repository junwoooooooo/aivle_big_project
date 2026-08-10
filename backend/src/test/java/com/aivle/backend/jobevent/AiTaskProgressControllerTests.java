package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.integration.ai.AiServerProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiTaskProgressControllerTests {
    @Test
    void invalidServiceTokenIsRejectedWithoutCallingService() {
        AiTaskProgressService service = mock(AiTaskProgressService.class);
        AiServerProperties properties = new AiServerProperties("http://ai", Duration.ofSeconds(1),
            Duration.ofSeconds(1), Duration.ofMinutes(1), "secret");
        var controller = new AiTaskProgressController(properties, service);
        var request = new AiTaskProgressController.ProgressRequest("run", "attempt", "correlation", 1,
            "PLANNING", "STARTED", "RUNNING", "safe", null, null, null, null, Instant.now());
        assertThat(controller.progress("Bearer wrong", request).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(service);
    }
}
