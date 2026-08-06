package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.analysis.feasibility.*;
import com.aivle.backend.analysis.legal.repository.*;
import com.aivle.backend.common.entity.StructuredPlanStatus;
import com.aivle.backend.document.repository.*;
import com.aivle.backend.integration.ai.feasibility.FeasibilityAnalysisAiRequest;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class FeasibilityJobContextService {
    private final AnalysisJobRepository jobs;
    private final StructuredPlanSectionRepository sections;
    private final MissingFieldRepository missingFields;
    private final LegalFindingRepository legalFindings;
    private final LegalReviewQuestionRepository legalQuestions;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FeasibilityJobContext load(JobClaim claim) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> invalid("FEASIBILITY_JOB_NOT_FOUND"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())
            || job.getSourceStructuredPlan() == null || job.getSourceLegalReview() == null) {
            throw invalid("FEASIBILITY_JOB_CLAIM_LOST");
        }
        var plan = job.getSourceStructuredPlan();
        var legal = job.getSourceLegalReview();
        if (!plan.getProject().getId().equals(job.getProject().getId())
            || !legal.getProject().getId().equals(job.getProject().getId())
            || !legal.getStructuredPlan().getId().equals(plan.getId())
            || plan.getStatus() != StructuredPlanStatus.CONFIRMED
            || plan.getCompletionRate() != 100
            || plan.getSourceDocumentVersion() == null) {
            throw invalid("FEASIBILITY_INPUT_SNAPSHOT_INVALID");
        }
        var catalog = FeasibilityDimensionCatalog.all().stream().map(item ->
            new FeasibilityAnalysisAiRequest.CatalogDimension(
                item.code(), item.displayName(), item.displayOrder(), item.description(),
                item.sourceSections().stream().map(Enum::name).toList())).toList();
        var sectionInputs = sections
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(plan.getId()).stream()
            .map(item -> new FeasibilityAnalysisAiRequest.Section(
                item.getSectionCode().name(), item.getTitle(), item.getSourceText(),
                item.getItemStatus().name(), item.getEvidenceJson(),
                item.getSourceBlockReferencesJson())).toList();
        var completions = missingFields
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId()).stream()
            .map(item -> new FeasibilityAnalysisAiRequest.Completion(
                item.getFieldCode(), item.getSectionCode().name(), item.getStatus().name(),
                item.getUserValueJson(), item.getReason())).toList();
        var findingInputs = legalFindings
            .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(legal.getId()).stream()
            .map(item -> new FeasibilityAnalysisAiRequest.LegalFinding(
                item.getId(), item.getCategory().name(), item.getApplicability().name(),
                item.getSeverity(), item.getDescription(), item.getRationale(),
                item.getRecommendation())).toList();
        var questionInputs = legalQuestions
            .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(legal.getId()).stream()
            .map(item -> new FeasibilityAnalysisAiRequest.LegalQuestion(
                item.getId(), item.getQuestion(), item.getReason(), item.getStatus().name()))
            .toList();
        var request = new FeasibilityAnalysisAiRequest(
            job.getProject().getId(), plan.getId(), legal.getId(),
            plan.getSourceDocumentVersion().getId(), FeasibilityPolicy.PROMPT_VERSION,
            FeasibilityDimensionCatalog.VERSION, FeasibilityPolicy.PROMPT, catalog,
            sectionInputs, completions, new FeasibilityAnalysisAiRequest.LegalContext(
                legal.getId(), legal.getStatus().name(), legal.getRiskLevel(), legal.getSummary(),
                findingInputs, questionInputs));
        try {
            String snapshot = objectMapper.writeValueAsString(request);
            return new FeasibilityJobContext(request, snapshot, sha256(snapshot));
        } catch (JacksonException exception) {
            throw invalid("FEASIBILITY_INPUT_SERIALIZATION_FAILED");
        }
    }

    private JobProcessingException invalid(String code) {
        return JobProcessingException.nonRetryable(
            code, "사업 타당성 분석 입력을 안전하게 구성할 수 없습니다.", null);
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
