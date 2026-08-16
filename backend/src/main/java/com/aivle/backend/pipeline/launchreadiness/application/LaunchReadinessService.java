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
        TaskType taskType = taskType(type); String key = requiredKey(idempotencyKey);
        TaskRun replay = runs
            .findFirstByProjectIdAndTaskTypeAndIdempotencyKeyAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, taskType, key).orElse(null);
        if (replay != null) {
            JsonNode replayInput = mapper.readTree(replay.getInputSnapshot());
            return new AnalysisActionResponse(replay.getId(), replay.getId(), replay.getState().name(),
                replayInput.path("inputSnapshotId").asText(), replayInput.path("inputSnapshotHash").asText());
        }
        var artifact = artifacts.upload(ownerId, projectId, file);
        // 보안 업로드 검증 이후 파싱하며, 파싱 실패 시 artifact 저장도 같은 트랜잭션에서 되돌린다.
        final Map<String, String> parsed;
        try { parsed = documents.parse(type, file); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
        ObjectNode snapshotBody = mapper.createObjectNode();
        snapshotBody.put("contract", "launch-readiness-professional-input-v1");
        snapshotBody.put("schemaVersion", "1.0");
        snapshotBody.put("projectId", projectId); snapshotBody.put("moduleType", type.name());
        snapshotBody.put("sourceMode", "USER_DOCUMENT_INPUT");
        snapshotBody.put("sourceDocumentArtifactId", artifact.artifactId());
        snapshotBody.put("sourceDocumentHash", artifact.sha256());
        snapshotBody.set("professionalInput", mapper.valueToTree(parsed));
        String snapshotHash = snapshotHasher.hash(snapshotBody);
        String snapshotId = UUID.randomUUID().toString();
        snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .ifPresent(LaunchReadinessInputSnapshot::supersede);
        reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, type)
            .ifPresent(LaunchReadinessReport::supersede);
        snapshots.save(LaunchReadinessInputSnapshot.create(snapshotId, projectId, type, artifact.artifactId(),
            artifact.sha256(), artifact.originalFilename(), mapper.writeValueAsString(parsed), snapshotHash,
            ownerId, Instant.now()));

        ObjectNode input = mapper.createObjectNode();
        input.put("projectId", projectId); input.put("moduleType", type.name());
        input.put("inputSnapshotId", snapshotId); input.put("inputSnapshotHash", snapshotHash);
        input.put("sourceDocumentHash", artifact.sha256());
        input.set("professionalInput", mapper.valueToTree(parsed));
        String json = mapper.writeValueAsString(input);
        var created = taskRuns.createWithDisposition(ownerId, projectId, taskType, SUBJECT, snapshotId,
            json, inputHasher.hash(taskType, "1.0", "ko-KR", json), key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "DOCUMENT_ACCEPTED",
            "job.launch-readiness.document.accepted", JobEvent.Status.QUEUED, null);
        TaskRun task = created.taskRun();
        return new AnalysisActionResponse(task.getId(), task.getId(), task.getState().name(), snapshotId, snapshotHash);
    }

    @Transactional(readOnly = true)
    public ProfessionalAnalysisView current(Long ownerId, Long projectId, ModuleType type) {
        requireOwned(ownerId, projectId);
        LaunchReadinessInputSnapshot snapshot = snapshots
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .orElse(null);
        TaskRun latest = runs.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, taskType(type)).orElse(null);
        LaunchReadinessReport report = reports
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, type)
            .orElse(null);
        if (snapshot == null) return empty(type);
        boolean reportMatches = report != null && report.getInputSnapshotId().equals(snapshot.getId());
        String status = latest == null ? (reportMatches ? "SUCCEEDED" : "NOT_STARTED") : latest.getState().name();
        return new ProfessionalAnalysisView(type.name(), status, latest != null && latest.isRetryable(),
            latest == null ? null : latest.getLastErrorCode(), latest == null ? null : latest.getId(),
            latest == null ? null : latest.getId(), snapshot.getId(), snapshot.getSourceDocumentName(),
            mapper.readTree(snapshot.getParsedInputJson()), snapshot.getSourceDocumentHash(), snapshot.getSnapshotHash(),
            reportMatches ? report.getId() : null,
            reportMatches ? report.getResultHash() : null,
            reportMatches ? mapper.readTree(report.getAnalysisJson()) : null,
            reportMatches ? mapper.readTree(report.getQualityJson()) : null,
            reportMatches ? mapper.readTree(report.getExternalEvidenceJson()) : null,
            reportMatches ? report.getCompletedAt() : null, reportMatches, report != null && !reportMatches);
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        validate(response.result());
        JsonNode input = mapper.readTree(context.inputSnapshot());
        ModuleType type = ModuleType.valueOf(input.path("moduleType").asText());
        LaunchReadinessInputSnapshot current = snapshots
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(context.projectId(), type)
            .orElseThrow(StaleInputException::new);
        if (!current.getId().equals(input.path("inputSnapshotId").asText())
                || !current.getSnapshotHash().equals(input.path("inputSnapshotHash").asText())) throw new StaleInputException();
        if (reports.findByTaskRunIdAndDeletedAtIsNull(context.taskRunId()).isPresent()) return;
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(context.projectId(), type)
            .ifPresent(LaunchReadinessReport::supersede);
        reports.save(LaunchReadinessReport.create(UUID.randomUUID().toString(), context.projectId(), type,
            current.getId(), context.taskRunId(), mapper.writeValueAsString(response.result()),
            mapper.writeValueAsString(response.result().path("quality")),
            mapper.writeValueAsString(response.result().path("externalEvidence")),
            snapshotHasher.hash(response.result()), context.ownerId(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public boolean currentInput(TaskRunWorkerContext context) {
        JsonNode input = mapper.readTree(context.inputSnapshot());
        ModuleType type = ModuleType.valueOf(input.path("moduleType").asText());
        return snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(context.projectId(), type)
            .filter(value -> value.getId().equals(input.path("inputSnapshotId").asText())
                && value.getSnapshotHash().equals(input.path("inputSnapshotHash").asText())).isPresent();
    }

    @Transactional public void fail(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
    }
    @Transactional public void reject(TaskRunService.Claim claim, String reason) {
        taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "null", "1.0", reason);
    }
    public void publish(Long projectId, String taskRunId, String stage, String key, JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId, stage, key, status, key, Map.of(), code));
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
            null, null, null, null, null, null, null, null, null, null, null, false, false);
    }
    private TaskType taskType(ModuleType type) { return type == ModuleType.TECHNOLOGY
        ? TaskType.LAUNCH_TECHNOLOGY_READINESS : TaskType.LAUNCH_OPERATIONS_READINESS; }
    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    public static final class StaleInputException extends RuntimeException {}
}
