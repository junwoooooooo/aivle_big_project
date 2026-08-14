package com.aivle.backend.pipeline.techops.application;

import static com.aivle.backend.pipeline.techops.api.TechOpsApiModels.*;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.techops.application.TechOpsAdvisorySourceResolver.Sources;
import com.aivle.backend.pipeline.techops.domain.TechOpsAdvisoryReport;
import com.aivle.backend.pipeline.techops.repository.TechOpsAdvisoryReportRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class TechOpsAdvisoryService {
    public static final String SUBJECT = "TECH_OPS_ADVISORY_REPORT";
    private final TechOpsAdvisorySourceResolver sources;
    private final TechOpsAdvisoryReportRepository reports;
    private final TaskRunRepository runs;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final TechOpsAdvisoryResultContract contract;
    private final ProjectRepository projects;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    @Transactional
    public AdvisoryActionResponse start(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        Sources source = sources.resolve(ownerId, projectId);
        ObjectNode input = mapper.createObjectNode();
        input.put("projectId", projectId);
        input.put("techOpsInputSnapshotId", source.snapshot().getId());
        input.put("sourceMarketSeedSnapshotId", source.seed().getId());
        input.put("sourceMarketResearchVersionId", source.market().getId());
        input.put("sourceBusinessModelVersionId", source.businessModel().getId());
        input.put("sourcePortfolioSelectionId", source.selection().getId());
        input.put("selectedConceptId", source.concept().getId());
        input.put("selectedConceptHash", source.selection().getSelectedConceptHash());
        input.set("conceptHandoff", mapper.readTree(source.concept().getCandidateSnapshotJson()));
        input.set("legalHandoff", source.legal() == null ? mapper.nullNode()
            : mapper.readTree(source.legal().getReportJson()));
        input.set("marketResult", mapper.readTree(source.market().getResultJson()));
        input.set("businessModelResult", mapper.readTree(source.businessModel().getResultJson()));
        input.set("techOpsInputSnapshot", mapper.readTree(source.snapshot().getSnapshotJson()));
        String json = mapper.writeValueAsString(input);
        String key = requiredKey(idempotencyKey);
        String hash = hasher.hash(TaskType.TECH_OPS_ADVISORY, "1.0", "ko-KR", json);
        var created = taskRuns.createWithDisposition(ownerId, projectId, TaskType.TECH_OPS_ADVISORY,
            SUBJECT, source.snapshot().getId(), json, hash, key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "QUEUED",
            "job.tech-ops.advisory.queued", JobEvent.Status.QUEUED, null);
        TaskRun task = created.taskRun();
        return new AdvisoryActionResponse(task.getId(), task.getId(), task.getState().name(),
            source.snapshot().getId(), source.seed().getId(), source.market().getId(), source.businessModel().getId());
    }

    @Transactional(readOnly = true)
    public AdvisoryView current(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new com.aivle.backend.common.exception.BusinessException(
                com.aivle.backend.common.exception.ErrorCode.PROJECT_NOT_FOUND));
        TechOpsAdvisoryReport report = reports
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        TaskRun latest = runs.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, TaskType.TECH_OPS_ADVISORY).orElse(null);
        if (report != null) {
            TaskRun task = latest == null ? runs.findById(report.getTaskRunId()).orElse(null) : latest;
            return view(report, task, stale(ownerId, projectId, report));
        }
        TaskRun task = latest;
        return task == null ? new AdvisoryView(null, null, "NOT_STARTED", false, null, false,
            null, null, null, null, null, null, null, "1.0", null, null)
            : new AdvisoryView(null, task.getId(), task.getState().name(), task.isRetryable(),
                task.getLastErrorCode(), false, task.getSubjectId(), null, null, null,
                null, null, null, "1.0", null, task.getCreatedAt());
    }

    @Transactional
    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        contract.validate(response.result());
        JsonNode input = input(context);
        Sources current = sources.resolve(context.ownerId(), context.projectId());
        if (!matches(current, input)) throw new StaleSourceException();
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (reports.findByTaskRunIdAndDeletedAtIsNull(claim.taskRunId()).isPresent()) return;
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        reports.save(TechOpsAdvisoryReport.create(context.projectId(), context.taskRunId(),
            input.path("techOpsInputSnapshotId").asText(), input.path("sourceMarketSeedSnapshotId").asText(),
            input.path("sourceMarketResearchVersionId").asLong(), input.path("sourceBusinessModelVersionId").asLong(),
            input.path("sourcePortfolioSelectionId").asLong(), input.path("selectedConceptId").asText(),
            input.path("selectedConceptHash").asText(), "1.0", mapper.writeValueAsString(response.result()),
            context.ownerId()));
    }

    @Transactional
    public boolean validateSources(TaskRunWorkerContext context) {
        try { return matches(sources.resolve(context.ownerId(), context.projectId()), input(context)); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Transactional
    public void reject(TaskRunService.Claim claim, JsonNode result, String reason) {
        taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(result == null ? mapper.nullNode() : result), "1.0", reason);
    }

    @Transactional
    public void fail(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
    }

    public void publish(Long projectId, String taskRunId, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId, stage, key,
            status, key, Map.of(), code));
    }

    private boolean stale(Long ownerId, Long projectId, TechOpsAdvisoryReport report) {
        try {
            Sources value = sources.resolve(ownerId, projectId);
            return !sources.matches(value, report.getTechOpsInputSnapshotId(), report.getSourceMarketSeedSnapshotId(),
                report.getSourceMarketResearchVersionId(), report.getSourceBusinessModelVersionId(),
                report.getSourcePortfolioSelectionId(), report.getSelectedConceptId(), report.getSelectedConceptHash());
        } catch (RuntimeException unavailable) { return true; }
    }

    private boolean matches(Sources source, JsonNode input) {
        return sources.matches(source, input.path("techOpsInputSnapshotId").asText(),
            input.path("sourceMarketSeedSnapshotId").asText(),
            input.path("sourceMarketResearchVersionId").asLong(),
            input.path("sourceBusinessModelVersionId").asLong(),
            input.path("sourcePortfolioSelectionId").asLong(), input.path("selectedConceptId").asText(),
            input.path("selectedConceptHash").asText());
    }
    private JsonNode input(TaskRunWorkerContext context) {
        JsonNode value = mapper.readTree(context.inputSnapshot());
        if (!value.isObject()) throw new IllegalStateException("TechOps advisory input invalid");
        return value;
    }
    private AdvisoryView view(TechOpsAdvisoryReport report, TaskRun task, boolean stale) {
        return new AdvisoryView(report.getId(), task == null ? report.getTaskRunId() : task.getId(), task == null ? "SUCCEEDED" : task.getState().name(),
            task != null && task.isRetryable(), task == null ? null : task.getLastErrorCode(), stale,
            report.getTechOpsInputSnapshotId(), report.getSourceMarketSeedSnapshotId(),
            report.getSourceMarketResearchVersionId(), report.getSourceBusinessModelVersionId(),
            report.getSourcePortfolioSelectionId(), report.getSelectedConceptId(), report.getSelectedConceptHash(),
            report.getContractVersion(), mapper.readTree(report.getResultJson()), report.getCreatedAt());
    }
    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new com.aivle.backend.common.exception.BusinessException(
                com.aivle.backend.common.exception.ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }
    public static final class StaleSourceException extends RuntimeException {}
}
