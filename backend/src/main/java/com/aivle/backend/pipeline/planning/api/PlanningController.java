package com.aivle.backend.pipeline.planning.api;
import static com.aivle.backend.pipeline.planning.api.PlanningApiModels.*;
import com.aivle.backend.common.response.ApiResponse; import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.planning.application.PlanningService; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v3/projects/{projectId}/planning") @RequiredArgsConstructor
public class PlanningController { private final PlanningService service; private final CurrentUserProvider user;
 @GetMapping("/current") public ApiResponse<PlanningCurrentView> current(@PathVariable Long projectId,HttpServletRequest r){return ApiResponse.success(service.current(user.currentUserId(),projectId),r.getHeader("X-Request-Id"));}
 @GetMapping("/change-proposals") public ApiResponse<ChangeProposalListView> proposals(@PathVariable Long projectId,HttpServletRequest r){return ApiResponse.success(service.proposalList(user.currentUserId(),projectId),r.getHeader("X-Request-Id"));}
 @PostMapping("/change-proposals/{proposalId}/decisions") public ApiResponse<PlanningCurrentView> decide(@PathVariable Long projectId,@PathVariable String proposalId,@Valid @RequestBody DecisionRequest b,HttpServletRequest r){return ApiResponse.success(service.decide(user.currentUserId(),projectId,proposalId,b),r.getHeader("X-Request-Id"));}
 @PostMapping("/finalize") public ApiResponse<FinalizedSnapshotView> finalizePlanning(@PathVariable Long projectId,HttpServletRequest r){return ApiResponse.success(service.finalizePlanning(user.currentUserId(),projectId),r.getHeader("X-Request-Id"));}
}
