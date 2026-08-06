package com.aivle.backend.aitask.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.aitask.dto.AiTaskStartResponse;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.application.IdempotencyKeyPolicy;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SystemSmokeTaskCommandService {

    static final String SCHEMA_VERSION = "1.0";

    private final ProjectRepository projects;
    private final AnalysisJobRepository jobs;
    private final IdempotencyKeyPolicy idempotencyKeys;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final ServicePolicyService servicePolicy;

    @Transactional
    public AiTaskStartResponse start(
        Long userId,
        Long projectId,
        String rawIdempotencyKey,
        Long rerunOfJobId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        String idempotencyKey = idempotencyKeys.normalize(
            rawIdempotencyKey
        );
        if (idempotencyKey == null) {
            throw new BusinessException(
                ErrorCode.IDEMPOTENCY_KEY_INVALID
            );
        }

        var project = projects
            .findByIdForUpdate(projectId)
            .filter(value ->
                value.getOwner().getId().equals(userId)
            )
            .orElseThrow(() ->
                new BusinessException(ErrorCode.PROJECT_NOT_FOUND)
            );
        AnalysisJob rerunOf = resolveRerun(
            userId,
            projectId,
            rerunOfJobId
        );
        String fingerprint = fingerprint(
            projectId,
            rerunOfJobId
        );

        var existing = jobs
            .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                projectId,
                JobType.SYSTEM_SMOKE_TEST,
                idempotencyKey
            );
        if (existing.isPresent()) {
            AnalysisJob job = existing.get();
            if (!job.hasSameIdempotentRequest(fingerprint)) {
                throw new BusinessException(
                    ErrorCode.IDEMPOTENCY_CONFLICT
                );
            }
            return response(job, false);
        }

        AnalysisJob job = jobs.save(
            AnalysisJob.queuedSystemSmoke(
                project,
                requestJson(projectId, rerunOfJobId),
                idempotencyKey,
                fingerprint,
                rerunOf
            )
        );
        events.publishEvent(
            new SystemSmokeTaskRequested(job.getId())
        );
        return response(job, true);
    }

    private AnalysisJob resolveRerun(
        Long userId,
        Long projectId,
        Long rerunOfJobId
    ) {
        if (rerunOfJobId == null) {
            return null;
        }
        AnalysisJob previous = jobs
            .findByIdAndProjectIdAndProjectOwnerIdAndJobTypeAndDeletedAtIsNull(
                rerunOfJobId,
                projectId,
                userId,
                JobType.SYSTEM_SMOKE_TEST
            )
            .orElseThrow(() ->
                new BusinessException(ErrorCode.JOB_NOT_FOUND)
            );
        if (!previous.isTerminalStatus()) {
            throw new BusinessException(
                ErrorCode.JOB_RETRY_NOT_ALLOWED
            );
        }
        return previous;
    }

    private AiTaskStartResponse response(
        AnalysisJob job,
        boolean created
    ) {
        return new AiTaskStartResponse(
            job.getId(),
            job.getStatus(),
            created,
            job.getRerunOfJob() == null
                ? null
                : job.getRerunOfJob().getId()
        );
    }

    private String requestJson(
        Long projectId,
        Long rerunOfJobId
    ) {
        try {
            return objectMapper.writeValueAsString(
                new RequestSnapshot(
                    projectId,
                    SCHEMA_VERSION,
                    rerunOfJobId
                )
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "AI task request snapshot serialization failed",
                exception
            );
        }
    }

    private String fingerprint(
        Long projectId,
        Long rerunOfJobId
    ) {
        String source = (
            JobType.SYSTEM_SMOKE_TEST.name()
            + ":" + SCHEMA_VERSION
            + ":" + projectId
            + ":" + String.valueOf(rerunOfJobId)
        );
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        source.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }

    private record RequestSnapshot(
        Long projectId,
        String schemaVersion,
        Long rerunOfJobId
    ) {
    }
}
