package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/business-validation/refinement")
@RequiredArgsConstructor
public class ConceptRefinementController {
    private final ConceptRefinementService refinement;
    private final ConceptRefinementDecisionService decisions;
    private final ConceptRefinementApplicationService applications;
    private final ConceptRefinementFinalizationService finalization;
    private final CurrentUserProvider currentUser;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> start(
            @PathVariable Long projectId, HttpServletRequest request) {
        return accepted(refinement.start(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), id(request)), request);
    }

    @GetMapping("/current")
    public ApiResponse<ConceptRefinementService.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(refinement.current(currentUser.currentUserId(), projectId), id(request));
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> retry(
            @PathVariable Long projectId, HttpServletRequest request) {
        return accepted(refinement.retry(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), id(request)), request);
    }

    @PostMapping("/next")
    public ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> next(
            @PathVariable Long projectId, @RequestBody NextRequest body, HttpServletRequest request) {
        return accepted(refinement.next(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), id(request), body.expectedRound(),
            body.expectedProposalSetHash(), body.expectedDecisionHash()), request);
    }

    @PostMapping("/decision")
    public ApiResponse<ConceptRefinementService.CurrentView> decision(
            @PathVariable Long projectId, @RequestBody DecisionRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(decisions.decide(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), body.expectedRound(), body.proposalSetHash(),
            body.selectedProposalKeys(), body.keepCurrent()), id(request));
    }

    @PostMapping("/apply")
    public ApiResponse<ConceptRefinementService.CurrentView> apply(
            @PathVariable Long projectId, @RequestBody ApplyRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(applications.apply(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), body.expectedRound(), body.expectedDecisionHash()), id(request));
    }

    @PostMapping("/apply/retry-legal")
    public ApiResponse<ConceptRefinementService.CurrentView> retryLegal(
            @PathVariable Long projectId, @RequestBody ApplyRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(applications.retryLegal(currentUser.currentUserId(), projectId,
            request.getHeader("Idempotency-Key"), body.expectedRound(), body.expectedDecisionHash()), id(request));
    }

    @PostMapping("/finalize")
    public ApiResponse<ConceptRefinementFinalizationService.FinalView> finalizeRound(
            @PathVariable Long projectId,@RequestBody FinalizeRequest body,HttpServletRequest request){
        return ApiResponse.success(finalization.finalizeRound(currentUser.currentUserId(),projectId,
            request.getHeader("Idempotency-Key"),body.expectedRound(),body.expectedDecisionHash()),id(request));
    }

    @GetMapping("/final")
    public ApiResponse<ConceptRefinementFinalizationService.FinalView> currentFinal(
            @PathVariable Long projectId,HttpServletRequest request){
        return ApiResponse.success(finalization.current(currentUser.currentUserId(),projectId),id(request));
    }

    private ResponseEntity<ApiResponse<ConceptRefinementService.CurrentView>> accepted(
            ConceptRefinementService.CurrentView value, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(value, id(request)));
    }

    private String id(HttpServletRequest request) { return RequestIds.resolve(request); }

    public record DecisionRequest(Integer expectedRound, String proposalSetHash,
                                  List<String> selectedProposalKeys, boolean keepCurrent) { }
    public record ApplyRequest(Integer expectedRound, String expectedDecisionHash) { }
    public record FinalizeRequest(Integer expectedRound,String expectedDecisionHash) { }
    public record NextRequest(Integer expectedRound, String expectedProposalSetHash,
                              String expectedDecisionHash) { }
}
