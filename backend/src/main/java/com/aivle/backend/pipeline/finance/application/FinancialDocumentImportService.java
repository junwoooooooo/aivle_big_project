package com.aivle.backend.pipeline.finance.application;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.SnapshotView;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** DOCX 검증·보관·정본 반영을 하나의 트랜잭션 경계에서 수행한다. */
@Service
@RequiredArgsConstructor
public class FinancialDocumentImportService {
    private final ProjectEvidenceArtifactService artifacts;
    private final FinancialInputDocumentService documents;
    private final FinancialService finance;

    @Transactional
    public SnapshotView importDocument(Long ownerId, Long projectId, MultipartFile file) {
        // 저장 계층의 확장자·크기·OOXML signature 검증을 먼저 통과시킨다.
        // 이후 parse/전체 필드 검증이 실패하면 같은 트랜잭션의 artifact도 rollback 정리된다.
        var artifact = artifacts.upload(ownerId, projectId, file);
        final tools.jackson.databind.JsonNode parsed;
        try { parsed = documents.parse(file); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.FINANCIAL_INPUT_INVALID, exception.getMessage());
        }
        return finance.importUserDocument(ownerId, projectId,
            artifact.artifactId(), artifact.sha256(), parsed);
    }
}
