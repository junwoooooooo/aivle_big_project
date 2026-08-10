package com.aivle.backend.jobevent;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record JobEventView(
        String eventId,
        String jobId,
        Long projectId,
        String taskRunId,
        String stage,
        String eventType,
        String status,
        String messageKey,
        JsonNode messageParams,
        String technicalCode,
        long sequence,
        String occurredAt) {

    static JobEventView from(JobEvent event, ObjectMapper mapper) {
        return new JobEventView(
            Long.toString(event.getId()),
            event.getJobId(),
            event.getProject().getId(),
            event.getTaskRun() == null ? null : event.getTaskRun().getId(),
            event.getStage(),
            event.getEventType(),
            event.getStatus().name(),
            event.getMessageKey(),
            mapper.readTree(event.getMessageParamsJson()),
            event.getTechnicalCode(),
            event.getSequence(),
            utc(event.getOccurredAt())
        );
    }

    private static String utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }

    public boolean terminal() {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "NEEDS_INPUT".equals(status);
    }
}
