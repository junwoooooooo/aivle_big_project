package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class JobEventStreamServiceTests {
    @Test
    void completionCallbackRemovesEmitter() throws Exception {
        JobEventStreamService service = new JobEventStreamService();
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        service.subscribe("job-complete", List::of, emitter);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).onCompletion(callback.capture());
        callback.getValue().run();

        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void timeoutCallbackRemovesEmitter() {
        JobEventStreamService service = new JobEventStreamService();
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        service.subscribe("job-timeout", List::of, emitter);

        verify(emitter).onTimeout(callback.capture());
        callback.getValue().run();

        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void errorCallbackRemovesEmitter() {
        JobEventStreamService service = new JobEventStreamService();
        SseEmitter emitter = mock(SseEmitter.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.function.Consumer<Throwable>> callback =
            ArgumentCaptor.forClass(java.util.function.Consumer.class);
        service.subscribe("job-error", List::of, emitter);

        verify(emitter).onError(callback.capture());
        callback.getValue().accept(new IOException("client disconnected"));

        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void heartbeatBrokenPipeRemovesOnlyTheFailedEmitter() throws Exception {
        JobEventStreamService service = new JobEventStreamService();
        SseEmitter broken = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(broken)
            .send(any(SseEmitter.SseEventBuilder.class));
        service.subscribe("job-broken", List::of, broken);
        service.subscribe("job-healthy", List::of, healthy);

        service.heartbeat();
        service.publish(event("job-broken", 1, "RUNNING"));
        service.publish(event("job-healthy", 1, "RUNNING"));

        assertThat(service.activeConnections()).isEqualTo(1);
        verify(broken, never()).completeWithError(any());
        verify(healthy, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void terminalEventIsSentBeforeTheStreamCompletes() throws Exception {
        JobEventStreamService service = new JobEventStreamService();
        SseEmitter emitter = mock(SseEmitter.class);
        service.subscribe("job-terminal", List::of, emitter);

        service.publish(event("job-terminal", 1, "COMPLETED"));
        service.publish(event("job-terminal", 2, "COMPLETED"));

        InOrder order = inOrder(emitter);
        order.verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        order.verify(emitter).complete();
        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void cleansUpEveryEmitterOnBackendShutdown() {
        JobEventStreamService service = new JobEventStreamService();
        service.subscribe("job-one", List::of);
        service.subscribe("job-two", List::of);

        assertThat(service.activeConnections()).isEqualTo(2);
        service.heartbeat();
        service.shutdown();

        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void completesAndRemovesEmitterWhenInitialReplayFailsWithoutPropagating() {
        JobEventStreamService service = new JobEventStreamService();
        SseEmitter emitter = mock(SseEmitter.class);

        service.subscribe("failed-job", () -> {
            throw new IllegalStateException("replay failed");
        }, emitter);

        verify(emitter).complete();
        assertThat(service.activeConnections()).isZero();
    }

    private JobEventView event(String jobId, long sequence, String status) {
        return new JobEventView(
            Long.toString(sequence), jobId, 1L, null, "IDEA_INTAKE", "STATUS_CHANGED",
            status, "job.idea.status", null, null, sequence, "2026-08-05T00:00:00Z");
    }
}
