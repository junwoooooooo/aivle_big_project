package com.aivle.backend.pipeline.idea.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.idea.application.IdeaAttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/idea-brief/attachments")
@RequiredArgsConstructor
public class IdeaAttachmentController {
    private final IdeaAttachmentService service;
    private final CurrentUserProvider currentUser;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<IdeaBriefApiModels.AttachmentView>> upload(
        @PathVariable Long projectId,
        @RequestPart("file") MultipartFile file,
        HttpServletRequest request
    ) {
        IdeaBriefApiModels.AttachmentView response = service.upload(currentUser.currentUserId(), projectId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, request.getHeader("X-Request-Id")));
    }
}
