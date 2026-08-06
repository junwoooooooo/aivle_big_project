package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.analysis.feasibility.*;
import com.aivle.backend.analysis.feasibility.entity.*;
import com.aivle.backend.analysis.feasibility.repository.*;
import com.aivle.backend.audit.*;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.integration.ai.feasibility.FeasibilityAnalysisAiResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;

@Service
@RequiredArgsConstructor
public class FeasibilityPersistenceService {
    private final AnalysisJobRepository jobs;
    private final FeasibilityAssessmentRepository assessments;
    private final FeasibilityDimensionResultRepository dimensions;
    private final FeasibilityValidationTaskRepository validationTasks;
    private final FeasibilityScorePolicy scorePolicy;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;
    private final DomainAuditService audit;

    @Transactional
    public Long complete(
        JobClaim claim, FeasibilityJobContext context, FeasibilityAnalysisAiResponse result
    ) {
        validate(result);
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        var plan = job.getSourceStructuredPlan();
        var legal = job.getSourceLegalReview();
        var existing = assessments
            .findByStructuredPlanIdAndLegalReviewIdAndPromptVersionAndCatalogVersionAndDeletedAtIsNull(
                plan.getId(), legal.getId(), FeasibilityPolicy.PROMPT_VERSION,
                FeasibilityDimensionCatalog.VERSION);
        FeasibilityAssessment assessment;
        if (existing.isPresent()) {
            assessment = existing.get();
        } else {
            var outcome = scorePolicy.evaluate(
                result.dimensions(), result.validationTasks(), legal.getRiskLevel());
            String canonical = json(result);
            LocalDateTime now = LocalDateTime.now(jobClock);
            assessment = assessments.save(FeasibilityAssessment.completed(
                job.getProject(), job, plan, legal, outcome.status(), outcome.verdict(),
                outcome.overallScore(), outcome.confidence(), result.summary(),
                json(result.keyStrengths()), json(result.keyRisks()),
                json(result.validationTasks().stream().map(
                    FeasibilityAnalysisAiResponse.ValidationTask::code).toList()),
                FeasibilityPolicy.DISCLAIMER, result.provider(), result.model(),
                FeasibilityPolicy.PROMPT_VERSION, FeasibilityDimensionCatalog.VERSION,
                sha256(FeasibilityPolicy.PROMPT), context.inputHash(), sha256(canonical),
                context.snapshotJson(), now));
            for (var item : result.dimensions()) {
                var definition = FeasibilityDimensionCatalog.get(item.code());
                dimensions.save(FeasibilityDimensionResult.create(
                    assessment, item.code(), definition.displayOrder(), item.score(),
                    item.confidence(), item.status(), item.finding(), item.rationale(),
                    json(item.strengths()), json(item.risks()), json(item.assumptions()),
                    json(item.evidence()), json(item.sourceSectionCodes()),
                    json(item.legalFindingIds()), json(item.recommendedActions())));
            }
            int order = 1;
            for (var task : result.validationTasks()) {
                validationTasks.save(FeasibilityValidationTask.open(
                    assessment, task.code(), task.dimensionCode(), task.title(),
                    task.description(), task.reason(), task.priority(),
                    task.validationMethod(), task.expectedEvidence(), order++));
            }
        }
        job.complete(claim.claimToken(), claim.attempt(), JobStatus.SUCCEEDED,
            "FEASIBILITY_ASSESSMENT", assessment.getId(), LocalDateTime.now(jobClock));
        if (assessment.getStatus() == AssessmentStatus.COMPLETED) {
            job.getProject().enterFinancial();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("assessmentId", assessment.getId().toString());
        metadata.put("structuredPlanId", plan.getId().toString());
        metadata.put("legalReviewId", legal.getId().toString());
        metadata.put("jobId", job.getId().toString());
        metadata.put("resultStatus", assessment.getStatus().name());
        metadata.put("verdict", assessment.getVerdict().name());
        audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
            AuditEventType.FEASIBILITY_ANALYSIS_COMPLETED, "FeasibilityAssessment",
            assessment.getId(), null, metadata);
        return assessment.getId();
    }

    private void validate(FeasibilityAnalysisAiResponse result) {
        if (result == null || result.summary() == null || result.keyStrengths() == null
            || result.keyRisks() == null || result.validationTasks() == null
            || result.dimensions() == null
            || result.dimensions().size() != DimensionCode.values().length) {
            throw new IllegalArgumentException("feasibility result contract is invalid");
        }
        EnumSet<DimensionCode> seen = EnumSet.noneOf(DimensionCode.class);
        Set<String> taskCodes = new HashSet<>();
        for (var item : result.dimensions()) {
            if (item == null || item.code() == null || item.confidence() == null
                || item.status() == null || !seen.add(item.code())
                || (item.score() != null && (item.score() < 0 || item.score() > 100))) {
                throw new IllegalArgumentException("feasibility dimensions are invalid");
            }
        }
        for (var task : result.validationTasks()) {
            if (task == null || task.code() == null || task.dimensionCode() == null
                || task.priority() == null || !taskCodes.add(task.code())) {
                throw new IllegalArgumentException("feasibility validation tasks are invalid");
            }
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) {
            throw new IllegalStateException("feasibility result serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
