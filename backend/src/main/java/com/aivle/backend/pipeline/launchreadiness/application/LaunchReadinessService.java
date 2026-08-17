package com.aivle.backend.pipeline.launchreadiness.application;

import static com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.launchreadiness.domain.*;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.*;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class LaunchReadinessService {
    private static final String SUBJECT = "LAUNCH_READINESS_INPUT";
    private static final Set<String> RESULT_FIELDS = Set.of("decision", "score", "summary", "dimensions",
        "risks", "gates", "actions", "externalEvidence", "quality");
    private final ProjectRepository projects;
    private final ProjectEvidenceArtifactService artifacts;
    private final LaunchReadinessDocumentService documents;
    private final LaunchReadinessInputSnapshotRepository snapshots;
    private final LaunchReadinessReportRepository reports;
    private final TaskRunRepository runs;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final SnapshotHasher snapshotHasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    public byte[] template(ModuleType type) { return documents.template(type); }

    @Transactional
    public AnalysisActionResponse start(Long ownerId, Long projectId, ModuleType type, MultipartFile file,
            String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        var fingerprint = artifacts.fingerprint(file);
        final Map<String, String> parsed;
        try { parsed = documents.parse(type, file); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
        ObjectNode snapshotBody = snapshotBody(projectId, type, file.getOriginalFilename(),
            fingerprint.sha256(), parsed);
        String snapshotHash = snapshotHasher.hash(snapshotBody);
        TaskType taskType = taskType(type); String key = requiredKey(idempotencyKey);
        TaskRun replay = runs
            .findFirstByProjectIdAndTaskTypeAndIdempotencyKeyAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, taskType, key).orElse(null);
        if (replay != null) {
            JsonNode replayInput = mapper.readTree(replay.getInputSnapshot());
            if (!"START".equals(replayInput.path("operation").asText())
                    || !snapshotHash.equals(replayInput.path("inputSnapshotHash").asText())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return new AnalysisActionResponse(replay.getId(), replay.getId(), replay.getState().name(),
                replayInput.path("inputSnapshotId").asText(), replayInput.path("inputSnapshotHash").asText());
        }
        var artifact = artifacts.upload(ownerId, projectId, file);
        String snapshotId = UUID.randomUUID().toString();
        snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .ifPresent(LaunchReadinessInputSnapshot::supersede);
        reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, type)
            .ifPresent(LaunchReadinessReport::supersede);
        snapshots.save(LaunchReadinessInputSnapshot.create(snapshotId, projectId, type, artifact.artifactId(),
            artifact.sha256(), artifact.originalFilename(), mapper.writeValueAsString(parsed), snapshotHash,
            null, null, null, null, null, 1, ownerId, Instant.now()));

        ObjectNode input = taskInput(snapshotId, snapshotHash, snapshotBody, "START", 1);
        String json = mapper.writeValueAsString(input);
        var created = taskRuns.createWithDisposition(ownerId, projectId, taskType, SUBJECT, String.valueOf(projectId),
            json, inputHasher.hash(taskType, "1.0", "ko-KR", json), key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 1);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "DOCUMENT_ACCEPTED",
            "job.launch-readiness.document.accepted", JobEvent.Status.QUEUED, null);
        TaskRun task = created.taskRun();
        return new AnalysisActionResponse(task.getId(), task.getId(), task.getState().name(), snapshotId, snapshotHash);
    }

    @Transactional
    public AnalysisActionResponse retry(Long ownerId, Long projectId, ModuleType type,
            String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        String key = requiredKey(idempotencyKey); TaskType taskType = taskType(type);
        TaskRun replay = runs
            .findFirstByProjectIdAndTaskTypeAndIdempotencyKeyAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, taskType, key).orElse(null);
        if (replay != null) {
            JsonNode replayInput = mapper.readTree(replay.getInputSnapshot());
            LaunchReadinessInputSnapshot currentSnapshot = snapshots
                .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
                .orElse(null);
            if (!"RETRY".equals(replayInput.path("operation").asText())
                    || currentSnapshot == null
                    || !currentSnapshot.getId().equals(replayInput.path("inputSnapshotId").asText())
                    || !currentSnapshot.getSnapshotHash().equals(replayInput.path("inputSnapshotHash").asText()))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return action(replay, replayInput);
        }
        LaunchReadinessInputSnapshot previous = currentSnapshot(projectId, type);
        TaskRun latest = latestFor(projectId, type, previous);
        if (latest == null || latest.getState() != TaskRunState.FAILED || previous.getAttempt() >= 3)
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        int attempt = previous.getAttempt() + 1;
        String snapshotId = UUID.randomUUID().toString();
        previous.supersede();
        reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, type)
            .ifPresent(LaunchReadinessReport::supersede);
        LaunchReadinessInputSnapshot next = snapshots.save(LaunchReadinessInputSnapshot.create(snapshotId,
            projectId, type, previous.getSourceDocumentArtifactId(), previous.getSourceDocumentHash(),
            previous.getSourceDocumentName(), previous.getParsedInputJson(), previous.getSnapshotHash(),
            null, null, null, null, null, attempt, ownerId, Instant.now()));
        ObjectNode snapshotBody = snapshotBody(projectId, type, next.getSourceDocumentName(),
            next.getSourceDocumentHash(), mapper.readValue(next.getParsedInputJson(), Map.class));
        ObjectNode input = taskInput(snapshotId, next.getSnapshotHash(), snapshotBody, "RETRY", attempt);
        String json = mapper.writeValueAsString(input);
        TaskRun task = taskRuns.createWithDisposition(ownerId, projectId, taskType, SUBJECT, String.valueOf(projectId),
            json, inputHasher.hash(taskType, "1.0", "ko-KR", json), key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 1).taskRun();
        publish(projectId, task.getId(), "QUEUED", "job.launch-readiness.queued", JobEvent.Status.QUEUED, null);
        return action(task, input);
    }

    @Transactional
    public ProfessionalAnalysisView current(Long ownerId, Long projectId, ModuleType type) {
        requireOwned(ownerId, projectId);
        LaunchReadinessInputSnapshot snapshot = snapshots
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .orElse(null);
        if (snapshot == null) return empty(type);
        TaskRun latest = latestFor(projectId, type, snapshot);
        LaunchReadinessReport report = reports
            .findFirstByProjectIdAndModuleTypeAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(
                projectId, type, snapshot.getId())
            .orElse(null);
        boolean reportMatches = report != null && report.getInputSnapshotId().equals(snapshot.getId());
        boolean stale = snapshot.isStale() || (report != null && report.isStale());
        String status = stale ? "STALE" : latest == null ? (reportMatches ? "SUCCEEDED" : "NOT_STARTED") : latest.getState().name();
        boolean retryAvailable = !stale && latest != null && latest.getState() == TaskRunState.FAILED
            && snapshot.getAttempt() < 3;
        return new ProfessionalAnalysisView(type.name(), status, retryAvailable,
            latest == null ? null : latest.getLastErrorCode(), latest == null ? null : latest.getId(),
            latest == null ? null : latest.getId(), snapshot.getId(), snapshot.getSourceDocumentName(),
            mapper.readTree(snapshot.getParsedInputJson()), snapshot.getSourceDocumentHash(), snapshot.getSnapshotHash(),
            reportMatches ? report.getId() : null,
            reportMatches ? report.getResultHash() : null,
            reportMatches ? mapper.readTree(report.getAnalysisJson()) : null,
            reportMatches ? mapper.readTree(report.getQualityJson()) : null,
            reportMatches ? mapper.readTree(report.getExternalEvidenceJson()) : null,
            reportMatches ? report.getCompletedAt() : null, reportMatches && !stale, stale,
            retryAvailable, snapshot.getStaleReason(),
            "PROFESSIONAL_INPUT", null);
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        validate(response.result());
        JsonNode input = mapper.readTree(context.inputSnapshot());
        ModuleType type = ModuleType.valueOf(input.path("moduleType").asText());
        if (reports.findByTaskRunIdAndDeletedAtIsNull(context.taskRunId()).isPresent()) return;
        LaunchReadinessInputSnapshot source = snapshots
            .findByIdAndProjectIdAndDeletedAtIsNull(input.path("inputSnapshotId").asText(), context.projectId())
            .orElseThrow(() -> new IllegalStateException("launch readiness input missing"));
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        LaunchReadinessInputSnapshot current = snapshots
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(context.projectId(), type)
            .orElse(null);
        boolean exact = current != null && current.getId().equals(source.getId())
            && current.getSnapshotHash().equals(input.path("inputSnapshotHash").asText());
        if (exact) reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(context.projectId(), type)
            .ifPresent(LaunchReadinessReport::supersede);
        else source.markStale("DOCUMENT_SUPERSEDED");
        reports.save(LaunchReadinessReport.create(UUID.randomUUID().toString(), context.projectId(), type,
            source.getId(), context.taskRunId(), mapper.writeValueAsString(response.result()),
            mapper.writeValueAsString(response.result().path("quality")),
            mapper.writeValueAsString(response.result().path("externalEvidence")),
            snapshotHasher.hash(response.result()), exact, !exact, context.ownerId(), Instant.now()));
    }

    @Transactional
    public boolean currentInput(TaskRunWorkerContext context) {
        JsonNode input = mapper.readTree(context.inputSnapshot());
        ModuleType type = ModuleType.valueOf(input.path("moduleType").asText());
        LaunchReadinessInputSnapshot source = snapshots
            .findByIdAndProjectIdAndDeletedAtIsNull(input.path("inputSnapshotId").asText(), context.projectId())
            .orElse(null);
        boolean exact = source != null && snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(context.projectId(), type)
            .filter(value -> value.getId().equals(input.path("inputSnapshotId").asText())
                && value.getSnapshotHash().equals(input.path("inputSnapshotHash").asText())).isPresent();
        if (!exact && source != null) markStale(source, type, "DOCUMENT_SUPERSEDED");
        return exact;
    }

    @Transactional public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, false);
    }
    @Transactional public void reject(TaskRunService.Claim claim, String reason) {
        taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "null", "1.0", reason);
    }
    public void publish(Long projectId, String taskRunId, String stage, String key, JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId, stage, key, status, key, Map.of(), code));
    }
    private ObjectNode snapshotBody(Long projectId, ModuleType type, String documentName,
            String documentHash, Map<String, String> parsed) {
        ObjectNode body = mapper.createObjectNode();
        body.put("contract", "launch-readiness-professional-input-v2");
        body.put("schemaVersion", "2.0"); body.put("projectId", projectId);
        body.put("moduleType", type.name()); body.put("sourceMode", "USER_DOCUMENT_INPUT");
        body.put("sourceDocumentName", documentName); body.put("sourceDocumentHash", documentHash);
        body.set("professionalInput", mapper.valueToTree(parsed));
        return body;
    }
    private ObjectNode taskInput(String snapshotId, String snapshotHash, ObjectNode snapshotBody,
            String operation, int attempt) {
        ObjectNode input = snapshotBody.deepCopy();
        input.put("inputSnapshotId", snapshotId); input.put("inputSnapshotHash", snapshotHash);
        input.put("operation", operation); input.put("attempt", attempt);
        return input;
    }
    private void markStale(LaunchReadinessInputSnapshot snapshot, ModuleType type, String reason) {
        snapshot.markStale(reason);
        reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(
            snapshot.getProjectId(), type).filter(value -> value.getInputSnapshotId().equals(snapshot.getId()))
            .ifPresent(LaunchReadinessReport::markStale);
    }
    private LaunchReadinessInputSnapshot currentSnapshot(Long projectId, ModuleType type) {
        return snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            projectId, type).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private TaskRun latestFor(Long projectId, ModuleType type, LaunchReadinessInputSnapshot snapshot) {
        TaskRun latest = runs.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, taskType(type)).orElse(null);
        if (latest == null) return null;
        JsonNode input = mapper.readTree(latest.getInputSnapshot());
        return snapshot.getId().equals(input.path("inputSnapshotId").asText()) ? latest : null;
    }
    private AnalysisActionResponse action(TaskRun task, JsonNode input) {
        return new AnalysisActionResponse(task.getId(), task.getId(), task.getState().name(),
            input.path("inputSnapshotId").asText(), input.path("inputSnapshotHash").asText());
    }
    private void validate(JsonNode result) {
        if (result == null || !result.isObject() || !Set.copyOf(result.propertyNames()).equals(RESULT_FIELDS)
                || !Set.of("READY", "CONDITIONAL", "REVISE").contains(result.path("decision").asText())
                || !result.path("score").canConvertToInt() || result.path("score").asInt() < 0 || result.path("score").asInt() > 100
                || result.path("summary").asText().isBlank() || !result.path("dimensions").isArray()
                || result.path("dimensions").size() < 4 || !result.path("risks").isArray() || result.path("risks").size() < 3
                || !result.path("gates").isArray() || result.path("gates").size() < 4
                || !result.path("actions").isArray() || result.path("actions").size() < 3
                || !result.path("externalEvidence").isArray() || !result.path("quality").isObject()) {
            throw new IllegalStateException("professional readiness result contract invalid");
        }
    }
    private ProfessionalAnalysisView empty(ModuleType type) {
        return new ProfessionalAnalysisView(type.name(), "NOT_STARTED", false, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, false, false,
            false, null, "PROFESSIONAL_INPUT", null);
    }
    private TaskType taskType(ModuleType type) { return switch (type) {
        case TECHNOLOGY -> TaskType.LAUNCH_TECHNOLOGY_READINESS;
        case OPERATIONS -> TaskType.LAUNCH_OPERATIONS_READINESS;
        case LAUNCH -> TaskType.LAUNCH_READINESS;
    }; }
    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
