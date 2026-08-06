package com.aivle.backend.document.controller;

import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.document.application.DocumentCommandService;
import com.aivle.backend.document.application.DocumentQueryService;
import com.aivle.backend.document.application.DocumentUploadCommand;
import com.aivle.backend.document.dto.response.DocumentSummaryResponse;
import com.aivle.backend.document.dto.response.DocumentUploadResponse;
import com.aivle.backend.document.dto.response.DocumentVersionResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentCommandService commandService;
    private final DocumentQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(
        value = "/api/v1/projects/{projectId}/documents",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DocumentUploadResponse> upload(
        @PathVariable Long projectId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "BUSINESS_PLAN") DocumentType documentType,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        DocumentUploadCommand command = new DocumentUploadCommand(
            projectId,
            currentUserProvider.currentUserId(),
            documentType,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize(),
            file::getInputStream,
            idempotencyKey
        );
        return ApiResponse.success(
            DocumentUploadResponse.from(commandService.upload(command)),
            request.getHeader("X-Request-Id")
        );
    }

    @GetMapping("/api/v1/projects/{projectId}/documents")
    public ApiResponse<List<DocumentSummaryResponse>> findProjectDocuments(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            queryService.findProjectDocuments(currentUserProvider.currentUserId(), projectId),
            request.getHeader("X-Request-Id")
        );
    }

    @GetMapping("/api/v1/documents/{documentId}/versions/{versionId}")
    public ApiResponse<DocumentVersionResponse> findVersion(
        @PathVariable Long documentId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            queryService.findVersion(
                currentUserProvider.currentUserId(),
                documentId,
                versionId
            ),
            request.getHeader("X-Request-Id")
        );
    }
}
