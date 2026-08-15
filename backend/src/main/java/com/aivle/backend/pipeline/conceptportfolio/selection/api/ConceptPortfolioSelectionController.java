package com.aivle.backend.pipeline.conceptportfolio.selection.api;

import static com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/concept-portfolio-selections")
@RequiredArgsConstructor
public class ConceptPortfolioSelectionController {
    private final ConceptPortfolioSelectionService service; private final CurrentUserProvider currentUser;
    @PostMapping public ResponseEntity<ApiResponse<SelectionView>> select(@PathVariable Long projectId,@Valid @RequestBody CreateSelectionRequest body,HttpServletRequest request){return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(service.select(currentUser.currentUserId(),projectId,body),request.getHeader("X-Request-Id")));}
    @GetMapping("/current") public ApiResponse<SelectionView> current(@PathVariable Long projectId,HttpServletRequest request){return ApiResponse.success(service.current(currentUser.currentUserId(),projectId),request.getHeader("X-Request-Id"));}
    @GetMapping("/{selectionId}/hypotheses") public ApiResponse<List<HypothesisView>> hypotheses(@PathVariable Long projectId,@PathVariable Long selectionId,HttpServletRequest request){return ApiResponse.success(service.hypotheses(currentUser.currentUserId(),projectId,selectionId),request.getHeader("X-Request-Id"));}
    @PostMapping("/{selectionId}/hypotheses/confirm") public ResponseEntity<ApiResponse<ActionAccepted>> confirm(@PathVariable Long projectId,@PathVariable Long selectionId,@Valid @RequestBody ConfirmHypothesesRequest body,HttpServletRequest request){return ResponseEntity.accepted().body(ApiResponse.success(service.confirm(currentUser.currentUserId(),projectId,selectionId,body),request.getHeader("X-Request-Id")));}
    @PostMapping("/{selectionId}/hypotheses/{hypothesisType}/alternative") public ResponseEntity<ApiResponse<ActionAccepted>> alternative(@PathVariable Long projectId,@PathVariable Long selectionId,@PathVariable String hypothesisType,@Valid @RequestBody ActionRequest body,HttpServletRequest request){return ResponseEntity.accepted().body(ApiResponse.success(service.alternative(currentUser.currentUserId(),projectId,selectionId,hypothesisType,body),request.getHeader("X-Request-Id")));}
    @PostMapping("/{selectionId}/delta-legal/retry") public ResponseEntity<ApiResponse<ActionAccepted>> retryDelta(@PathVariable Long projectId,@PathVariable Long selectionId,@Valid @RequestBody ActionRequest body,HttpServletRequest request){return ResponseEntity.accepted().body(ApiResponse.success(service.retryDelta(currentUser.currentUserId(),projectId,selectionId,body),request.getHeader("X-Request-Id")));}
    @GetMapping("/{selectionId}/delta-legal") public ApiResponse<SelectionView> delta(@PathVariable Long projectId,@PathVariable Long selectionId,HttpServletRequest request){return ApiResponse.success(service.get(currentUser.currentUserId(),projectId,selectionId),request.getHeader("X-Request-Id"));}
    @PostMapping("/{selectionId}/legal-regulatory-report/finalize") public ApiResponse<LegalReportView> finalizeReport(@PathVariable Long projectId,@PathVariable Long selectionId,HttpServletRequest request){return ApiResponse.success(service.finalizeReport(currentUser.currentUserId(),projectId,selectionId),request.getHeader("X-Request-Id"));}
    @GetMapping("/{selectionId}/legal-regulatory-report/current") public ApiResponse<LegalReportView> report(@PathVariable Long projectId,@PathVariable Long selectionId,HttpServletRequest request){return ApiResponse.success(service.currentReport(currentUser.currentUserId(),projectId,selectionId),request.getHeader("X-Request-Id"));}
    @PostMapping("/{selectionId}/market-seed/finalize") public ResponseEntity<ApiResponse<ActionAccepted>> market(@PathVariable Long projectId,@PathVariable Long selectionId,@Valid @RequestBody ActionRequest body,HttpServletRequest request){return ResponseEntity.accepted().body(ApiResponse.success(service.finalizeMarketSeed(currentUser.currentUserId(),projectId,selectionId,body),request.getHeader("X-Request-Id")));}
    @GetMapping("/{selectionId}/market-seed/current") public ApiResponse<MarketSeedView> marketCurrent(@PathVariable Long projectId,@PathVariable Long selectionId,HttpServletRequest request){return ApiResponse.success(service.currentMarketSeed(currentUser.currentUserId(),projectId,selectionId),request.getHeader("X-Request-Id"));}
}
