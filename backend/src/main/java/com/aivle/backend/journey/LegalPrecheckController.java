package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/legal-prechecks")
@RequiredArgsConstructor
public class LegalPrecheckController {
    private final LegalPrecheckService service; private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<LegalPrecheckService.StartView>> start(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.start(currentUser.currentUserId(), projectId), id(request)));
    }
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LegalPrecheckService.StartView>> refresh(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.refreshSources(currentUser.currentUserId(), projectId), id(request)));
    }
    @GetMapping("/current")
    public ApiResponse<LegalPrecheckService.CurrentView> current(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), id(request));
    }
    @PostMapping("/answers/apply")
    public ApiResponse<IdeaOriginService.WorkspaceView> applyAnswers(@PathVariable Long projectId,
            @Valid @RequestBody ApplyAnswersRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.applyAnswers(currentUser.currentUserId(), projectId,
            body.ideaOriginVersionId()), id(request));
    }
    @PostMapping("/versions/{versionId}/revision-suggestions/{index}/accept")
    public ApiResponse<IdeaOriginService.WorkspaceView> acceptRevision(@PathVariable Long projectId,
            @PathVariable Long versionId, @PathVariable @Min(0) int index, HttpServletRequest request) {
        return ApiResponse.success(service.acceptRevision(currentUser.currentUserId(), projectId, versionId, index), id(request));
    }
    @PostMapping("/versions/{versionId}/revision-suggestions/accept")
    public ApiResponse<LegalPrecheckService.RevisionApplyView> acceptRevisions(@PathVariable Long projectId,
            @PathVariable Long versionId, @Valid @RequestBody AcceptRevisionsRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.acceptRevisionsAndRestart(currentUser.currentUserId(), projectId,
            versionId, body.indexes()), id(request));
    }
    @PostMapping("/answers/apply-and-restart")
    public ApiResponse<LegalPrecheckService.RevisionApplyView> applyAnswersAndRestart(@PathVariable Long projectId,
            @Valid @RequestBody ApplyAnswersRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.applyAnswersAndRestart(currentUser.currentUserId(), projectId,
            body.ideaOriginVersionId()), id(request));
    }
    private String id(HttpServletRequest request){return request.getHeader("X-Request-Id");}
    public record ApplyAnswersRequest(@NotNull Long ideaOriginVersionId){}
    public record AcceptRevisionsRequest(@NotNull @Size(min=1,max=50) List<@NotNull @Min(0) Integer> indexes){}
}
