package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ProjectEventStreamServiceTests {
    @Test
    void terminalJobEventDoesNotCloseProjectStream() throws Exception {
        ProjectEventStreamService service = new ProjectEventStreamService();
        SseEmitter emitter = mock(SseEmitter.class);
        service.subscribe(41L, List::of, emitter);

        service.publish(event("job-one", 301L, "COMPLETED"));
        service.publish(event("job-two", 302L, "RUNNING"));

        assertThat(service.activeConnections()).isEqualTo(1);
        verify(emitter, never()).complete();
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    private JobEventView event(String jobId, long eventId, String status) {
        return new JobEventView(Long.toString(eventId), jobId, 41L, null, "CONCEPT_PORTFOLIO",
            "STATUS_CHANGED", status, "job.status", null, null, 1L,
            "2026-08-11T00:00:00Z");
    }
}
