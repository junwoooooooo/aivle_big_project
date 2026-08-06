package com.aivle.backend.document.application;

import com.aivle.backend.common.entity.DocumentStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.dto.response.DocumentSummaryResponse;
import com.aivle.backend.document.dto.response.DocumentVersionResponse;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.ProjectDocument;
import com.aivle.backend.document.repository.DocumentVersionRepository;
import com.aivle.backend.document.repository.ProjectDocumentRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public List<DocumentSummaryResponse> findProjectDocuments(Long userId, Long projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        if (userId == null || !project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        List<ProjectDocument> documents = projectDocumentRepository
            .findAllByProjectIdAndStatusAndDeletedAtIsNull(projectId, DocumentStatus.ACTIVE);
        if (documents.isEmpty()) {
            return List.of();
        }
        List<Long> documentIds = documents.stream().map(ProjectDocument::getId).toList();
        Map<Long, DocumentVersion> latestByDocument = documentVersionRepository
            .findCurrentVersions(documentIds)
            .stream()
            .collect(Collectors.toMap(
                version -> version.getDocument().getId(),
                Function.identity()
            ));
        return documents.stream()
            .map(document -> DocumentSummaryResponse.from(
                document,
                latestByDocument.containsKey(document.getId())
                    ? latestByDocument.get(document.getId()).getId()
                    : null
            ))
            .toList();
    }

    public DocumentVersionResponse findVersion(
        Long userId,
        Long documentId,
        Long versionId
    ) {
        DocumentVersion version = documentVersionRepository
            .findByIdAndDocumentIdAndDeletedAtIsNull(versionId, documentId)
            .filter(candidate -> candidate.getDocument().getDeletedAt() == null)
            .filter(candidate -> candidate.getDocument().getProject().getDeletedAt() == null)
            .filter(candidate -> userId != null
                && candidate.getDocument().getProject().getOwner().getId().equals(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
        return DocumentVersionResponse.from(version);
    }
}
