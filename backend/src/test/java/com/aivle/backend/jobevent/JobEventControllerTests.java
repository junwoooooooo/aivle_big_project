package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.security.CurrentUserProvider;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class JobEventControllerTests {
    @Mock JobEventQueryService queries;
    @Mock JobEventStreamService streams;
    @Mock CurrentUserProvider users;

    @Test
    void usesLastEventIdAsReplayCursorForAuthenticatedSse() {
        when(users.currentUserId()).thenReturn(41L);
        when(streams.subscribe(eq("job-reconnect"), any())).thenReturn(new SseEmitter());
        JobEventSseController controller = new JobEventSseController(queries, streams, users);

        var response = controller.stream("job-reconnect", "7");

        verify(queries).verifyOwnership(41L, "job-reconnect");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<List<JobEventView>>> replay = ArgumentCaptor.forClass(Supplier.class);
        verify(streams).subscribe(eq("job-reconnect"), replay.capture());
        replay.getValue().get();
        verify(queries).replay(41L, "job-reconnect", 7L);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
    }
}
