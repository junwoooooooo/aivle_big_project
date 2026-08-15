package com.aivle.backend.jobevent;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ProjectEventStreamService {
    static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1_000L;
    private final Map<Long, StreamGroup> groups = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long projectId, Supplier<List<JobEventView>> replaySupplier) {
        return subscribe(projectId, replaySupplier, new SseEmitter(STREAM_TIMEOUT_MILLIS));
    }

    SseEmitter subscribe(Long projectId, Supplier<List<JobEventView>> replaySupplier,
            SseEmitter emitter) {
        String subscriptionId = UUID.randomUUID().toString();
        emitter.onCompletion(() -> remove(projectId, subscriptionId));
        emitter.onTimeout(() -> remove(projectId, subscriptionId));
        emitter.onError(ignored -> remove(projectId, subscriptionId));
        groups.compute(projectId, (ignored, existing) -> {
            StreamGroup group = existing == null ? new StreamGroup() : existing;
            synchronized (group) {
                group.emitters.put(subscriptionId, emitter);
                try {
                    emitter.send(SseEmitter.event().comment("connected"));
                    for (JobEventView event : replaySupplier.get()) {
                        if (!sendEvent(emitter, event)) {
                            group.emitters.remove(subscriptionId); break;
                        }
                    }
                } catch (IOException | RuntimeException exception) {
                    group.emitters.remove(subscriptionId); safeComplete(emitter);
                }
                return group.emitters.isEmpty() ? null : group;
            }
        });
        return emitter;
    }

    public void publish(JobEventView event) {
        StreamGroup group = groups.get(event.projectId());
        if (group == null) return;
        synchronized (group) {
            List<String> failed = new ArrayList<>();
            group.emitters.forEach((id, emitter) -> {
                if (!sendEvent(emitter, event)) failed.add(id);
            });
            failed.forEach(group.emitters::remove);
        }
        removeEmpty(event.projectId(), group);
    }

    @Scheduled(fixedDelayString = "${app.project-events.heartbeat-ms:15000}")
    public void heartbeat() {
        groups.forEach((projectId, group) -> {
            synchronized (group) {
                List<String> failed = new ArrayList<>();
                group.emitters.forEach((id, emitter) -> {
                    try { emitter.send(SseEmitter.event().comment("heartbeat")); }
                    catch (IOException | IllegalStateException exception) { failed.add(id); }
                });
                failed.forEach(group.emitters::remove);
            }
            removeEmpty(projectId, group);
        });
    }

    @PreDestroy public void shutdown() {
        groups.values().forEach(group -> { synchronized (group) {
            group.emitters.values().forEach(this::safeComplete); group.emitters.clear();
        }}); groups.clear();
    }
    int activeConnections() { return groups.values().stream().mapToInt(group -> {
        synchronized (group) { return group.emitters.size(); }
    }).sum(); }
    private boolean sendEvent(SseEmitter emitter, JobEventView event) {
        try { emitter.send(SseEmitter.event().id(event.eventId()).name("project-event").data(event)); return true; }
        catch (IOException | IllegalStateException exception) { return false; }
    }
    private void remove(Long projectId, String id) { StreamGroup group=groups.get(projectId); if(group==null)return;
        synchronized(group){group.emitters.remove(id);} removeEmpty(projectId,group); }
    private void removeEmpty(Long projectId,StreamGroup group){groups.computeIfPresent(projectId,(ignored,current)->{
        if(current!=group)return current;synchronized(current){return current.emitters.isEmpty()?null:current;}});}
    private void safeComplete(SseEmitter emitter){try{emitter.complete();}catch(RuntimeException ignored){}}
    private static final class StreamGroup { private final Map<String,SseEmitter> emitters=new LinkedHashMap<>(); }
}
