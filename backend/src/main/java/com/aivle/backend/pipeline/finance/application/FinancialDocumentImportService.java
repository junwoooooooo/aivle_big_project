package com.aivle.backend.pipeline.finance.application;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService.FinancialInputDocumentException;
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
    private final FinancialAnalysisService analysis;

    @Transactional
    public DocumentImportResponse importAndStart(Long ownerId, Long projectId, MultipartFile file,
            String idempotencyKey, String correlationId) {
        var fingerprint = artifacts.fingerprint(file);
        finance.lockImportCommand(ownerId, projectId);
        var replay = analysis.replayImport(ownerId, projectId, idempotencyKey, fingerprint.sha256());
        if (replay.isPresent()) {
            SnapshotView snapshot = replay.get().snapshot();
            return new DocumentImportResponse(
                finance.preparation(ownerId, projectId, snapshot.preparationId()),
                snapshot, replay.get().action());
        }
        SnapshotView snapshot = importDocument(ownerId, projectId, file);
        AnalysisActionResponse action = analysis.start(ownerId, projectId, idempotencyKey, correlationId);
        return new DocumentImportResponse(
            finance.preparation(ownerId, projectId, snapshot.preparationId()), snapshot, action);
    }

    @Transactional
    public SnapshotView importDocument(Long ownerId, Long projectId, MultipartFile file) {
        // 저장 계층의 확장자·크기·OOXML signature 검증을 먼저 통과시킨다.
        // 이후 parse/전체 필드 검증이 실패하면 같은 트랜잭션의 artifact도 rollback 정리된다.
        var artifact = artifacts.upload(ownerId, projectId, file);
        final tools.jackson.databind.JsonNode parsed;
        try { parsed = documents.parse(file); }
        catch (FinancialInputDocumentException exception) {
            var fieldErrors = exception.issues().stream()
                .map(issue -> new ApiResponse.FieldError(issue.field(), issue.message())).toList();
            throw new BusinessException(ErrorCode.FINANCIAL_INPUT_INVALID,
                "재무 입력 문서를 확인해 주세요.", fieldErrors);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.FINANCIAL_INPUT_INVALID,
                "재무 입력 문서를 확인해 주세요.");
        }
        return finance.importUserDocument(ownerId, projectId,
            artifact.artifactId(), artifact.sha256(), parsed);
    }
}
