package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService.BriefIncompleteException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class IdeaWorkspaceController {
    private final CurrentUserProvider currentUser;
    private final IdeaWorkspaceService workspace;
    private final IdeaAttachmentService attachments;
    private final OpportunityBriefWorkspaceService briefs;

    @PostMapping("/idea-conversations")
    public ApiResponse<IdeaWorkspaceService.WorkspaceView> create(@PathVariable Long projectId,
            @RequestBody(required = false) CreateConversationRequest body, HttpServletRequest request) {
        return ApiResponse.success(workspace.create(currentUser.currentUserId(), projectId,
            body != null && body.importCurrentIdeaSource()), requestId(request));
    }

    @GetMapping("/idea-conversations/current")
    public ApiResponse<IdeaWorkspaceService.WorkspaceView> current(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(workspace.current(currentUser.currentUserId(), projectId), requestId(request));
    }

    @GetMapping("/idea-conversations/{conversationId}")
    public ApiResponse<IdeaWorkspaceService.WorkspaceView> get(@PathVariable Long projectId,
            @PathVariable Long conversationId, HttpServletRequest request) {
        return ApiResponse.success(workspace.get(currentUser.currentUserId(), projectId, conversationId), requestId(request));
    }

    @PostMapping("/idea-conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<IdeaWorkspaceService.MessageAccepted>> message(@PathVariable Long projectId,
            @PathVariable Long conversationId, @RequestBody MessageRequest body, HttpServletRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(workspace.send(currentUser.currentUserId(),
            projectId, conversationId, body.text(), body.answers()), requestId(request)));
    }

    @PostMapping(value = "/idea-conversations/{conversationId}/attachments", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<IdeaAttachmentService.AttachmentView>> attachment(@PathVariable Long projectId,
            @PathVariable Long conversationId, @RequestParam(required = false) Long messageId,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(attachments.upload(currentUser.currentUserId(),
            projectId, conversationId, messageId, file), requestId(request)));
    }

    @GetMapping("/opportunity-brief/current")
    public ApiResponse<OpportunityBriefWorkspaceService.BriefView> currentBrief(@PathVariable Long projectId,
            @RequestParam Long conversationId, HttpServletRequest request) {
        return ApiResponse.success(briefs.current(currentUser.currentUserId(), projectId, conversationId), requestId(request));
    }

    @PutMapping("/opportunity-brief/fields/{fieldKey}")
    public ApiResponse<OpportunityBriefWorkspaceService.BriefView> editField(@PathVariable Long projectId,
            @PathVariable String fieldKey, @RequestBody FieldRequest body, HttpServletRequest request) {
        return ApiResponse.success(briefs.edit(currentUser.currentUserId(), projectId, body.conversationId(),
            fieldKey, body.value(), body.decisionStatus(), body.sourceMessageId()), requestId(request));
    }

    @PostMapping("/opportunity-brief/fields/{fieldKey}/adopt")
    public ApiResponse<OpportunityBriefWorkspaceService.BriefView> adoptField(@PathVariable Long projectId,
            @PathVariable String fieldKey, @RequestBody BriefActionRequest body, HttpServletRequest request) {
        return ApiResponse.success(briefs.adopt(currentUser.currentUserId(), projectId, body.conversationId(), fieldKey), requestId(request));
    }

    @PostMapping("/opportunity-brief/fields/{fieldKey}/reject")
    public ApiResponse<OpportunityBriefWorkspaceService.BriefView> rejectField(@PathVariable Long projectId,
            @PathVariable String fieldKey, @RequestBody BriefActionRequest body, HttpServletRequest request) {
        return ApiResponse.success(briefs.reject(currentUser.currentUserId(), projectId, body.conversationId(), fieldKey), requestId(request));
    }

    @PostMapping("/opportunity-brief/confirm")
    public ApiResponse<OpportunityBriefWorkspaceService.BriefView> confirm(@PathVariable Long projectId,
            @RequestBody ConfirmRequest body, HttpServletRequest request) {
        return ApiResponse.success(workspace.confirmBrief(currentUser.currentUserId(), projectId,
            body.conversationId()), requestId(request));
    }

    @ExceptionHandler(BriefIncompleteException.class)
    public ResponseEntity<ApiResponse<Void>> incomplete(BriefIncompleteException failure, HttpServletRequest request) {
        List<ApiResponse.FieldError> fields = failure.missingFields().stream()
            .map(field -> new ApiResponse.FieldError(field, "확정 전에 필요한 정보를 입력해 주세요.")).toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.failure(
            "OPPORTUNITY_BRIEF_INCOMPLETE", "Opportunity Brief를 아직 확정할 수 없습니다.", fields,
            false, requestId(request)));
    }

    private String requestId(HttpServletRequest request) { return request.getHeader("X-Request-Id"); }
    public record CreateConversationRequest(boolean importCurrentIdeaSource) { }
    public record MessageRequest(String text, List<IdeaWorkspaceService.Answer> answers) { }
    public record FieldRequest(Long conversationId, JsonNode value, FieldDecisionStatus decisionStatus, Long sourceMessageId) { }
    public record BriefActionRequest(Long conversationId) { }
    public record ConfirmRequest(Long conversationId) { }
}
