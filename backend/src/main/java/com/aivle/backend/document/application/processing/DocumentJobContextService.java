package com.aivle.backend.document.application.processing;

import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import com.aivle.backend.job.runner.JobProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentJobContextService {
    private final AnalysisJobRepository jobRepository;

    @Transactional(readOnly = true)
    public DocumentJobContext load(JobClaim claim) {
        AnalysisJob job = jobRepository.findById(claim.jobId())
            .orElseThrow(() -> JobProcessingException.nonRetryable(
                "JOB_NOT_FOUND",
                "문서 처리 작업을 찾을 수 없습니다.",
                null
            ));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())
            || job.getSourceDocumentVersion() == null) {
            throw JobProcessingException.nonRetryable(
                "JOB_CLAIM_LOST",
                "문서 처리 작업의 실행 권한이 만료되었습니다.",
                null
            );
        }
        var version = job.getSourceDocumentVersion();
        var file = version.getStoredFile();
        if (!version.getDocument().getProject().getId().equals(job.getProject().getId())) {
            throw JobProcessingException.nonRetryable(
                "JOB_SOURCE_MISMATCH",
                "문서 처리 입력이 프로젝트와 일치하지 않습니다.",
                null
            );
        }
        return new DocumentJobContext(
            job.getId(),
            job.getProject().getId(),
            version.getDocument().getId(),
            version.getId(),
            file.getStorageType(),
            file.getStorageKey(),
            file.getOriginalFilename(),
            file.getMimeType(),
            file.getSizeBytes(),
            file.getChecksumSha256(),
            file.getStatus(),
            file.getEncrypted()
        );
    }
}
