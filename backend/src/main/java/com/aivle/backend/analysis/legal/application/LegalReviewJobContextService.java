package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.document.repository.StructuredPlanSectionRepository;
import com.aivle.backend.integration.ai.legal.LegalReviewAiRequest;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import com.aivle.backend.job.runner.JobProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class LegalReviewJobContextService {
    private final AnalysisJobRepository jobRepository;
    private final StructuredPlanSectionRepository sectionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LegalReviewJobContext load(JobClaim claim) {
        var job = jobRepository.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> invalid("LEGAL_JOB_NOT_FOUND"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())
            || job.getSourceStructuredPlan() == null) {
            throw invalid("LEGAL_JOB_CLAIM_LOST");
        }
        var plan = job.getSourceStructuredPlan();
        if (!plan.getProject().getId().equals(job.getProject().getId())
            || plan.getStatus() != com.aivle.backend.common.entity.StructuredPlanStatus.CONFIRMED
            || plan.getCompletionRate() != 100) {
            throw invalid("LEGAL_INPUT_SNAPSHOT_INVALID");
        }
        var sections = sectionRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(plan.getId())
            .stream()
            .map(section -> new LegalReviewAiRequest.Section(
                section.getSectionCode().name(), section.getTitle(),
                section.getSourceText(), section.getEvidenceJson()))
            .toList();
        try {
            return new LegalReviewJobContext(job.getProject().getId(), plan.getId(),
                plan.getSourceDocumentVersion().getId(), sections,
                objectMapper.writeValueAsString(sections));
        } catch (JacksonException exception) {
            throw invalid("LEGAL_INPUT_SERIALIZATION_FAILED");
        }
    }

    private JobProcessingException invalid(String code) {
        return JobProcessingException.nonRetryable(
            code, "법률·규제 사전검토 입력을 확인할 수 없습니다.", null);
    }
}
