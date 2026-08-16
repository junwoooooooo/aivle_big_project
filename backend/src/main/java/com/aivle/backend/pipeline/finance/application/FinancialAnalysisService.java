package com.aivle.backend.pipeline.finance.application;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.finance.service.FinancialSnapshotAnalysisService;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FinancialAnalysisService {
    private static final String SUBJECT = "FINANCIAL_ANALYSIS_REPORT";
    private static final String USER_DOCUMENT_SUBJECT = "USER_DOCUMENT_INPUT";
    private static final Set<String> REPORT_FIELDS = Set.of("headline", "findings", "cautions",
        "recommendedActions", "disclaimer", "source", "providerStatus", "safeFailureReason");
    private final FinancialService finance;
    private final FinancialSnapshotAnalysisService calculation;
    private final TaskRunService taskRuns;
    private final TaskRunRepository runRepository;
    private final TaskResultRepository resultRepository;
    private final CanonicalInputHasher hasher;
    private final JobEventPublisher events;
    private final ProjectEvidenceArtifactRepository artifacts;
    private final ObjectMapper mapper;

    public FinancialAnalysisService(FinancialService finance, FinancialSnapshotAnalysisService calculation,
            TaskRunService taskRuns, TaskRunRepository runRepository, TaskResultRepository resultRepository,
            CanonicalInputHasher hasher, JobEventPublisher events,
            ProjectEvidenceArtifactRepository artifacts, ObjectMapper mapper) {
        this.finance = finance; this.calculation = calculation; this.taskRuns = taskRuns;
        this.runRepository = runRepository; this.resultRepository = resultRepository;
        this.hasher = hasher; this.events = events; this.artifacts = artifacts; this.mapper = mapper;
    }

    @Transactional
    public AnalysisActionResponse start(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        SnapshotView snapshot = finance.currentSnapshot(ownerId, projectId);
        JsonNode deterministic = mapper.valueToTree(calculation.analyze(snapshot.snapshot()));
        ObjectNode input = mapper.createObjectNode();
        input.put("snapshotId", snapshot.snapshotId());
        input.put("snapshotHash", snapshot.snapshotHash());
        input.put("sourceMarketResearchVersionId", snapshot.sourceMarketResearchVersionId());
        input.put("sourceBusinessModelVersionId", snapshot.sourceBusinessModelVersionId());
        input.set("deterministicResult", deterministic);
        String json = mapper.writeValueAsString(input);
        String hash = hasher.hash(TaskType.FINANCE_ANALYSIS_REPORT, "1.0", "ko-KR", json);
        String subjectId = USER_DOCUMENT_SUBJECT.equals(snapshot.snapshot().path("sourceMode").asText())
            ? USER_DOCUMENT_SUBJECT : snapshot.snapshotId();
        var created = taskRuns.createWithDisposition(ownerId, projectId, TaskType.FINANCE_ANALYSIS_REPORT,
            SUBJECT, subjectId, json, hash, requiredKey(idempotencyKey),
            correlationId == null || correlationId.isBlank() ? requiredKey(idempotencyKey) : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "QUEUED",
            "job.finance.analysis.queued", JobEvent.Status.QUEUED, null);
        TaskRun task = created.taskRun();
        return new AnalysisActionResponse(task.getId(), task.getId(), task.getState().name(),
            snapshot.snapshotId(), snapshot.snapshotHash());
    }

    @Transactional(readOnly = true)
    public AnalysisView current(Long ownerId, Long projectId) {
        SnapshotView snapshot = finance.currentSnapshot(ownerId, projectId);
        String sourceDocumentName = resolveSourceDocumentName(projectId, snapshot);
        String subjectId = USER_DOCUMENT_SUBJECT.equals(snapshot.snapshot().path("sourceMode").asText())
            ? USER_DOCUMENT_SUBJECT : snapshot.snapshotId();
        TaskRun task = runRepository
            .findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, SUBJECT, subjectId)
            .filter(value -> snapshot.snapshotId().equals(
                mapper.readTree(value.getInputSnapshot()).path("snapshotId").asText())).orElse(null);
        if (task == null) return new AnalysisView(null, null, "NOT_STARTED", false, null,
            snapshot.snapshotId(), snapshot.snapshotHash(), null, false, snapshot.stale(),
            null, sourceDocumentName);
        JsonNode result = task.getFinalResultId() == null ? null : resultRepository.findById(task.getFinalResultId())
            .map(TaskResult::getResultJson).map(mapper::readTree).orElse(null);
        boolean fallback = result != null && "SYSTEM_CALCULATION_FALLBACK".equals(
            result.path("report").path("source").asText());
        return new AnalysisView(task.getId(), task.getId(), task.getState().name(), task.isRetryable(),
            task.getLastErrorCode(), snapshot.snapshotId(), snapshot.snapshotHash(), result, fallback,
            snapshot.stale(), task.getFinishedAt(), sourceDocumentName);
    }

    private String resolveSourceDocumentName(Long projectId, SnapshotView snapshot) {
        String artifactId = snapshot.snapshot().path("sourceDocumentArtifactId").asText(null);
        if (artifactId == null || artifactId.isBlank()) return null;
        return artifacts.findByIdAndProjectIdAndDeletedAtIsNull(artifactId, projectId)
            .map(ProjectEvidenceArtifact::getOriginalFilename)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<ImportReplay> replayImport(Long ownerId, Long projectId,
            String idempotencyKey, String sourceDocumentHash) {
        String key = requiredKey(idempotencyKey);
        String scope = TaskType.FINANCE_ANALYSIS_REPORT.name() + ":" + SUBJECT + ":" + USER_DOCUMENT_SUBJECT;
        TaskRun task = runRepository.findByProjectIdAndIdempotencyScopeAndIdempotencyKey(
            projectId, scope, key).orElse(null);
        if (task == null) return Optional.empty();
        taskRuns.getOwned(ownerId, projectId, task.getId());
        String snapshotId = mapper.readTree(task.getInputSnapshot()).path("snapshotId").asText();
        SnapshotView snapshot = finance.snapshot(ownerId, projectId, snapshotId);
        if (!sourceDocumentHash.equals(snapshot.snapshot().path("sourceDocumentHash").asText())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        AnalysisActionResponse action = new AnalysisActionResponse(task.getId(), task.getId(),
            task.getState().name(), snapshot.snapshotId(), snapshot.snapshotHash());
        return Optional.of(new ImportReplay(snapshot, action));
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        validateReport(response.result());
        ObjectNode output = deterministic(context);
        output.set("report", response.result().deepCopy());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(output), response.canonicalInputHash(), "1.0");
    }

    @Transactional
    public void completeFallback(TaskRunService.Claim claim, TaskRunWorkerContext context, String safeReason) {
        ObjectNode output = deterministic(context);
        ObjectNode report = (ObjectNode) output.path("report");
        report.put("source", "SYSTEM_CALCULATION_FALLBACK");
        report.put("providerStatus", "FAILED");
        report.put("safeFailureReason", safeReason);
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(output), context.inputHash(), "1.0");
    }

    private ObjectNode deterministic(TaskRunWorkerContext context) {
        JsonNode value = mapper.readTree(context.inputSnapshot()).path("deterministicResult").deepCopy();
        if (!value.isObject()) throw new IllegalStateException("deterministic finance result missing");
        return (ObjectNode) value;
    }

    private void validateReport(JsonNode report) {
        if (report == null || !report.isObject() || !Set.copyOf(report.propertyNames()).equals(REPORT_FIELDS)
                || !"AI_GENERATED_REPORT".equals(report.path("source").asText())
                || !"SUCCEEDED".equals(report.path("providerStatus").asText())
                || !report.path("safeFailureReason").isNull()
                || !report.path("findings").isArray() || report.path("findings").isEmpty()
                || !report.path("cautions").isArray() || report.path("cautions").isEmpty()
                || !report.path("recommendedActions").isArray() || report.path("recommendedActions").isEmpty()) {
            throw new IllegalStateException("finance report contract invalid");
        }
    }

    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new com.aivle.backend.common.exception.BusinessException(
                com.aivle.backend.common.exception.ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }

    public void publish(Long projectId, String taskRunId, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId, stage, key,
            status, key, Map.of(), code));
    }

    public record ImportReplay(SnapshotView snapshot, AnalysisActionResponse action) {}
}
