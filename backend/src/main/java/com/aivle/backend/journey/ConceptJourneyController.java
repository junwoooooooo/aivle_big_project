package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class ConceptJourneyController {
    private final ConceptJourneyService concepts;
    private final CurrentUserProvider currentUser;

    @PostMapping("/concept-generations")
    public ResponseEntity<ApiResponse<ConceptJourneyService.BatchView>> generate(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(concepts.generate(user(),projectId),id(request)));
    }
    @GetMapping("/concept-generations/current")
    public ApiResponse<ConceptJourneyService.BatchView> currentGeneration(@PathVariable Long projectId,HttpServletRequest request){
        return ApiResponse.success(concepts.currentBatch(user(),projectId),id(request));
    }
    @GetMapping(value = "/concepts", params = "!contract")
    public ApiResponse<List<ConceptJourneyService.ConceptView>> concepts(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(concepts.concepts(user(),projectId),id(request));
    }
    @PostMapping("/quick-assessments")
    public ApiResponse<ConceptJourneyService.QuickView> quick(@PathVariable Long projectId,HttpServletRequest request) {
        return ApiResponse.success(concepts.quick(user(),projectId),id(request));
    }
    @GetMapping("/quick-assessments/current")
    public ApiResponse<ConceptJourneyService.QuickView> currentQuick(@PathVariable Long projectId,HttpServletRequest request) {
        return ApiResponse.success(concepts.currentQuick(user(),projectId),id(request));
    }
    @PutMapping("/shortlist")
    public ApiResponse<ConceptJourneyService.ShortlistView> shortlist(@PathVariable Long projectId,
            @Valid @RequestBody ConceptJourneyService.ShortlistRequest body,HttpServletRequest request) {
        return ApiResponse.success(concepts.shortlist(user(),projectId,body),id(request));
    }
    @GetMapping("/shortlist")
    public ApiResponse<ConceptJourneyService.ShortlistView> currentShortlist(@PathVariable Long projectId,HttpServletRequest request) {
        return ApiResponse.success(concepts.currentShortlist(user(),projectId),id(request));
    }
    @PostMapping("/detailed-analyses")
    public ApiResponse<ConceptJourneyService.DetailedView> detailed(@PathVariable Long projectId,
            @Valid @RequestBody ConceptJourneyService.DetailedRequest body,HttpServletRequest request) {
        return ApiResponse.success(concepts.detailed(user(),projectId,body),id(request));
    }
    @GetMapping("/detailed-analyses/current")
    public ApiResponse<ConceptJourneyService.DetailedView> currentDetailed(@PathVariable Long projectId,HttpServletRequest request) {
        return ApiResponse.success(concepts.currentDetailed(user(),projectId),id(request));
    }
    @PutMapping("/concept-selection")
    public ApiResponse<ConceptJourneyService.SelectionView> selection(@PathVariable Long projectId,
            @Valid @RequestBody ConceptJourneyService.SelectionRequest body,HttpServletRequest request) {
        return ApiResponse.success(concepts.select(user(),projectId,body),id(request));
    }
    @GetMapping("/concept-selection")
    public ApiResponse<ConceptJourneyService.SelectionView> currentSelection(@PathVariable Long projectId,HttpServletRequest request) {
        return ApiResponse.success(concepts.currentSelection(user(),projectId),id(request));
    }
    private Long user(){return currentUser.currentUserId();}
    private String id(HttpServletRequest request){return request.getHeader("X-Request-Id");}
}
