package com.aivle.backend.pipeline.conceptportfolio.selection.application;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConceptPortfolioSelectionTaskFactory {
    public static final TaskType TYPE = TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    public ConceptPortfolioSelectionTaskFactory(TaskRunService taskRuns, CanonicalInputHasher inputHasher,
            JobEventPublisher events, ObjectMapper mapper) {
        this.taskRuns = taskRuns; this.inputHasher = inputHasher; this.events = events; this.mapper = mapper;
    }

    public TaskRun create(Long ownerId, ConceptPortfolioSelection selection, String action,
            JsonNode input, String idempotencyKey, String correlationId) {
        String json = mapper.writeValueAsString(input);
        String hash = inputHasher.hash(TYPE, "1.0", "ko-KR", json);
        TaskRunService.CreateResult creation = taskRuns.createWithDisposition(ownerId, selection.getProjectId(), TYPE,
            "CONCEPT_PORTFOLIO_SELECTION", selection.getId().toString(), json, hash,
            idempotencyKey, blank(correlationId) ? UUID.randomUUID().toString() : correlationId, 2);
        TaskRun task = creation.taskRun();
        if (creation.createdNew()) {
            selection.attachTask(task.getId(), action);
            events.publish(new JobEventPublisher.Command(selection.getProjectId(), task.getId(), task.getId(),
                "QUEUED", "job.concept-portfolio.selection.queued", JobEvent.Status.QUEUED,
                "job.concept-portfolio.selection.queued", Map.of("action", action), null));
        } else if (!task.getId().equals(selection.getActiveTaskRunId())) {
            throw new IllegalStateException("Selection action replay authority mismatch");
        }
        return task;
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
