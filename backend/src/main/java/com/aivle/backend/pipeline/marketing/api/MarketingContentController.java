package com.aivle.backend.pipeline.marketing.api;

import static com.aivle.backend.pipeline.marketing.api.MarketingApiModels.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.marketing.application.MarketingContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v3/projects/{projectId}/marketing-contents") @RequiredArgsConstructor
public class MarketingContentController {
    private final MarketingContentService service; private final CurrentUserProvider user;
    @PostMapping public ApiResponse<ContentView> create(@PathVariable Long projectId,@Valid @RequestBody CreateRequest body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){return ApiResponse.success(service.create(user.currentUserId(),projectId,body,key,r.getHeader("X-Correlation-Id")),r.getHeader("X-Request-Id"));}
    @GetMapping public ApiResponse<ContentListView> list(@PathVariable Long projectId,HttpServletRequest r){return ApiResponse.success(service.list(user.currentUserId(),projectId),r.getHeader("X-Request-Id"));}
    @GetMapping("/{contentId}") public ApiResponse<ContentView> get(@PathVariable Long projectId,@PathVariable String contentId,HttpServletRequest r){return ApiResponse.success(service.get(user.currentUserId(),projectId,contentId),r.getHeader("X-Request-Id"));}
    @PatchMapping("/{contentId}") public ApiResponse<ContentView> edit(@PathVariable Long projectId,@PathVariable String contentId,@Valid @RequestBody EditRequest body,HttpServletRequest r){return ApiResponse.success(service.edit(user.currentUserId(),projectId,contentId,body),r.getHeader("X-Request-Id"));}
    @PostMapping("/{contentId}/regenerate") public ApiResponse<ContentView> regenerate(@PathVariable Long projectId,@PathVariable String contentId,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){return ApiResponse.success(service.regenerate(user.currentUserId(),projectId,contentId,key,r.getHeader("X-Correlation-Id")),r.getHeader("X-Request-Id"));}
    @PostMapping("/{contentId}/finalize") public ApiResponse<ContentView> finalizeContent(@PathVariable Long projectId,@PathVariable String contentId,HttpServletRequest r){return ApiResponse.success(service.finalizeContent(user.currentUserId(),projectId,contentId),r.getHeader("X-Request-Id"));}
}
