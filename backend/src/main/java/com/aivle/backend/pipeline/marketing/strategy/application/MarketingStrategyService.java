package com.aivle.backend.pipeline.marketing.strategy.application;

import static com.aivle.backend.pipeline.marketing.strategy.api.MarketingStrategyApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.marketing.strategy.domain.MarketingStrategyReport;
import com.aivle.backend.pipeline.marketing.strategy.repository.MarketingStrategyReportRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MarketingStrategyService {

    private static final String SUBJECT =
        "MARKETING_STRATEGY";

    private static final int MAX_INPUT_BYTES =
        1_800_000;

    private final MarketingStrategySourceService sources;
    private final MarketingStrategyReportRepository reports;
    private final MarketingStrategyResultContract resultContract;
    private final TaskRunService taskRuns;
    private final TaskRunRepository runRepository;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    @Transactional
    public StrategyActionResponse start(
        Long ownerId,
        Long projectId,
        String idempotencyKey,
        String correlationId
    ) {
        String key = requireKey(idempotencyKey);

        var source = sources.inspect(ownerId, projectId);

        if (!source.ready()) {
            throw new BusinessException(
                ErrorCode.MARKETING_STRATEGY_SOURCE_INCOMPLETE
            );
        }

        String reportId = source.hash()
            .substring("sha256:".length());

        String inputJson = mapper.writeValueAsString(
            source.toInput(mapper, projectId)
        );

        if (inputJson.getBytes(StandardCharsets.UTF_8).length
                > MAX_INPUT_BYTES) {
            throw new BusinessException(
                ErrorCode.MARKETING_STRATEGY_SOURCE_TOO_LARGE
            );
        }

        String hash = inputHasher.hash(
            TaskType.MARKETING_STRATEGY_GENERATION,
            "1.0",
            "ko-KR",
            inputJson
        );

        var created = taskRuns.createWithDisposition(
            ownerId,
            projectId,
            TaskType.MARKETING_STRATEGY_GENERATION,
            SUBJECT,
            reportId,
            inputJson,
            hash,
            key,
            correlationId == null
                    || correlationId.isBlank()
                ? key
                : correlationId,
            2
        );

        TaskRun task = created.taskRun();

        if (created.createdNew()) {
            publish(
                projectId,
                task.getId(),
                "QUEUED",
                "job.marketing.strategy.queued",
                JobEvent.Status.QUEUED,
                null
            );
        }

        return new StrategyActionResponse(
            reportId,
            task.getId(),
            task.getState().name(),
            source.hash()
        );
    }

    @Transactional(readOnly = true)
    public StrategyView current(
        Long ownerId,
        Long projectId
    ) {
        var source = sources.inspect(ownerId, projectId);

        MarketingStrategyReport report = reports
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId
            )
            .orElse(null);

        String expectedReportId = source.hash()
            .substring("sha256:".length());

        TaskRun task = runRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId,
                TaskType.MARKETING_STRATEGY_GENERATION
            )
            .filter(value ->
                expectedReportId.equals(value.getSubjectId())
            )
            .orElse(null);

        boolean stale = report != null
            && !source.hash().equals(
                report.getSourceManifestHash()
            );

        String status;

        if (task != null
                && !"SUCCEEDED".equals(
                    task.getState().name()
                )) {
            status = task.getState().name();
        } else if (report != null && !stale) {
            status = "SUCCEEDED";
        } else if (report != null) {
            status = "STALE";
        } else if (!source.ready()) {
            status = "NOT_READY";
        } else {
            status = "NOT_STARTED";
        }

        return new StrategyView(
            report == null ? null : report.getId(),
            task == null ? null : task.getId(),
            status,
            source.ready(),
            stale,
            source.hash(),
            source.manifest(),
            report == null
                ? null
                : mapper.readTree(report.getResultJson()),
            report == null
                ? null
                : report.getGeneratedAt(),
            source.missing(),
            source.sources().path("PROJECT").path("name").asText(null)
        );
    }

    @Transactional(readOnly = true)
    public MarketingStrategyReport requireCurrent(
        Long ownerId,
        Long projectId,
        String reportId
    ) {
        var source = sources.inspect(ownerId, projectId);

        MarketingStrategyReport report = reports
            .findByIdAndProjectIdAndDeletedAtIsNull(
                reportId,
                projectId
            )
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.MARKETING_STRATEGY_NOT_FOUND
                )
            );

        if (!source.hash().equals(
                report.getSourceManifestHash())) {
            throw new BusinessException(
                ErrorCode.MODULE_INPUT_STALE
            );
        }

        return report;
    }

    @Transactional
    public void complete(
        TaskRunService.Claim claim,
        TaskRunWorkerContext context,
        ExecutionResponse response
    ) {
        resultContract.validate(response.result());

        var taskInput =
            mapper.readTree(context.inputSnapshot());

        String sourceHash = taskInput
            .path("sourceManifestHash")
            .asText();

        var currentSource = sources.inspect(
            context.ownerId(),
            context.projectId()
        );

        if (!sourceHash.equals(currentSource.hash())) {
            throw new BusinessException(
                ErrorCode.MODULE_INPUT_STALE
            );
        }

        String resultJson =
            mapper.writeValueAsString(response.result());

        taskRuns.adopt(
            claim.taskRunId(),
            claim.taskAttemptId(),
            claim.claimToken(),
            resultJson,
            response.canonicalInputHash(),
            "1.0"
        );

        if (reports.findByTaskRunIdAndDeletedAtIsNull(
                context.taskRunId()).isEmpty()) {
            reports.save(
                MarketingStrategyReport.create(
                    context.subjectId(),
                    context.projectId(),
                    context.taskRunId(),
                    mapper.writeValueAsString(
                        taskInput.path("sourceManifest")
                    ),
                    sourceHash,
                    mapper.writeValueAsString(
                        taskInput.path("sources")
                    ),
                    resultJson,
                    context.ownerId(),
                    Instant.now()
                )
            );
        }
    }

    @Transactional
    public void fail(
        TaskRunService.Claim claim,
        String code,
        String reason,
        boolean retryable
    ) {
        taskRuns.fail(
            claim.taskRunId(),
            claim.taskAttemptId(),
            claim.claimToken(),
            code,
            reason,
            retryable
        );
    }

    public void publish(
        Long projectId,
        String taskRunId,
        String stage,
        String type,
        JobEvent.Status status,
        String code
    ) {
        events.publish(
            new JobEventPublisher.Command(
                projectId,
                taskRunId,
                taskRunId,
                stage,
                type,
                status,
                type,
                Map.of(),
                code
            )
        );
    }

    private String requireKey(String value) {
        if (value == null
                || value.isBlank()
                || value.strip().length() > 128) {
            throw new BusinessException(
                ErrorCode.IDEMPOTENCY_KEY_INVALID
            );
        }

        return value.strip();
    }
}
