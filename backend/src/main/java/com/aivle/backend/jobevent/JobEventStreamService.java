package com.aivle.backend.jobevent;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class JobEventStreamService {
    static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1_000L;
    private final Map<String, StreamGroup> groups = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String jobId, Supplier<List<JobEventView>> replaySupplier) {
        return subscribe(jobId, replaySupplier, new SseEmitter(STREAM_TIMEOUT_MILLIS));
    }

    SseEmitter subscribe(String jobId, Supplier<List<JobEventView>> replaySupplier,
            SseEmitter emitter) {
        String subscriptionId = UUID.randomUUID().toString();
        emitter.onCompletion(() -> remove(jobId, subscriptionId));
        emitter.onTimeout(() -> remove(jobId, subscriptionId));
        emitter.onError(ignored -> remove(jobId, subscriptionId));
        AtomicReference<RuntimeException> replayFailure = new AtomicReference<>();
        groups.compute(jobId, (ignored, existing) -> {
            StreamGroup group = existing == null ? new StreamGroup() : existing;
            synchronized (group) {
                group.emitters.put(subscriptionId, emitter);
                try {
                    for (JobEventView event : replaySupplier.get()) {
                        if (!sendEvent(emitter, event)) {
                            group.emitters.remove(subscriptionId);
                            break;
                        }
                        if (event.terminal()) {
                            safeComplete(emitter);
                            group.emitters.remove(subscriptionId);
                            break;
                        }
                    }
                } catch (RuntimeException exception) {
                    group.emitters.remove(subscriptionId);
                    safeComplete(emitter);
                    replayFailure.set(exception);
                }
                return group.emitters.isEmpty() ? null : group;
            }
        });
        if (replayFailure.get() != null) throw replayFailure.get();
        return emitter;
    }

    public void publish(JobEventView event) {
        StreamGroup group = groups.get(event.jobId());
        if (group == null) return;
        synchronized (group) {
            List<String> completed = new ArrayList<>();
            group.emitters.forEach((id, emitter) -> {
                if (!sendEvent(emitter, event)) {
                    completed.add(id);
                } else if (event.terminal()) {
                    safeComplete(emitter);
                    completed.add(id);
                }
            });
            completed.forEach(group.emitters::remove);
        }
        removeEmpty(event.jobId(), group);
    }

    @Scheduled(fixedDelayString = "${app.job-events.heartbeat-ms:15000}")
    public void heartbeat() {
        groups.forEach((jobId, group) -> {
            synchronized (group) {
                List<String> failed = new ArrayList<>();
                group.emitters.forEach((id, emitter) -> {
                    try {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    } catch (IOException | IllegalStateException exception) {
                        failed.add(id);
                    }
                });
                failed.forEach(group.emitters::remove);
            }
            removeEmpty(jobId, group);
        });
    }

    @PreDestroy
    public void shutdown() {
        groups.values().forEach(group -> {
            synchronized (group) {
                group.emitters.values().forEach(this::safeComplete);
                group.emitters.clear();
            }
        });
        groups.clear();
    }

    int activeConnections() {
        return groups.values().stream().mapToInt(group -> {
            synchronized (group) {
                return group.emitters.size();
            }
        }).sum();
    }

    private boolean sendEvent(SseEmitter emitter, JobEventView event) {
        try {
            emitter.send(SseEmitter.event()
                .id(Long.toString(event.sequence()))
                .name("job-event")
                .data(event));
            return true;
        } catch (IOException | IllegalStateException exception) {
            return false;
        }
    }

    private void remove(String jobId, String subscriptionId) {
        StreamGroup group = groups.get(jobId);
        if (group == null) return;
        synchronized (group) {
            group.emitters.remove(subscriptionId);
        }
        removeEmpty(jobId, group);
    }

    private void removeEmpty(String jobId, StreamGroup group) {
        groups.computeIfPresent(jobId, (ignored, current) -> {
            if (current != group) return current;
            synchronized (current) {
                return current.emitters.isEmpty() ? null : current;
            }
        });
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // A disconnected async response is already closed and only needs registry cleanup.
        }
    }

    private static final class StreamGroup {
        private final Map<String, SseEmitter> emitters = new LinkedHashMap<>();
    }
}
