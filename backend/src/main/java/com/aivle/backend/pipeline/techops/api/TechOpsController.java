package com.aivle.backend.pipeline.techops.api;

import static com.aivle.backend.pipeline.techops.api.TechOpsApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.techops.application.TechOpsService;
import com.aivle.backend.pipeline.techops.application.TechOpsAdvisoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/tech-ops")
@RequiredArgsConstructor
public class TechOpsController {
    private final TechOpsService service;
    private final TechOpsAdvisoryService advisory;
    private final CurrentUserProvider user;

    @PostMapping("/preparation/initialize")
    public ResponseEntity<ApiResponse<PreparationView>> initialize(@PathVariable Long projectId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.initialize(user.currentUserId(), projectId, idempotencyKey, request.getHeader("X-Request-Id")),
            request.getHeader("X-Request-Id")));
    }
    @GetMapping("/preparation")
    public ApiResponse<PreparationView> preparation(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }
    @PatchMapping("/preparation")
    public ApiResponse<PreparationView> patch(@PathVariable Long projectId, @Valid @RequestBody RequiredFactsPatch body,
            HttpServletRequest request) {
        return ApiResponse.success(service.patchFacts(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }
    @PostMapping("/preparation/proposals/{fieldKey}/decision")
    public ResponseEntity<ApiResponse<ProposalActionResponse>> decide(@PathVariable Long projectId,
            @PathVariable String fieldKey, @Valid @RequestBody ProposalDecisionRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        ProposalActionResponse result = service.decideProposal(user.currentUserId(), projectId, fieldKey,
            body, idempotencyKey, request.getHeader("X-Request-Id"));
        HttpStatus status = result.taskRunId() == null ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }
    @PostMapping("/preparation/proposals/retry")
    public ResponseEntity<ApiResponse<ProposalActionResponse>> retryProposals(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        ProposalActionResponse result = service.retryInitialProposals(user.currentUserId(), projectId,
            idempotencyKey, request.getHeader("X-Request-Id"));
        return ResponseEntity.accepted().body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }
    @PostMapping("/preparation/evidence")
    public ResponseEntity<ApiResponse<PreparationView>> addEvidence(@PathVariable Long projectId,
            @Valid @RequestBody EvidenceRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.addEvidence(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id")));
    }
    @DeleteMapping("/preparation/evidence/{evidenceId}")
    public ApiResponse<PreparationView> removeEvidence(@PathVariable Long projectId, @PathVariable String evidenceId,
            HttpServletRequest request) {
        return ApiResponse.success(service.removeEvidence(user.currentUserId(), projectId, evidenceId), request.getHeader("X-Request-Id"));
    }
    @PostMapping("/input-snapshots/finalize")
    public ResponseEntity<ApiResponse<SnapshotView>> finalizeSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.finalizeSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }
    @GetMapping("/input-snapshots/current")
    public ApiResponse<SnapshotView> currentSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.currentSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/advisory-runs")
    public ResponseEntity<ApiResponse<AdvisoryActionResponse>> startAdvisory(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(advisory.start(
            user.currentUserId(), projectId, idempotencyKey, request.getHeader("X-Request-Id")),
            request.getHeader("X-Request-Id")));
    }

    @GetMapping("/advisory/current")
    public ApiResponse<AdvisoryView> currentAdvisory(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(advisory.current(user.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }
}
