package com.aivle.backend.jobevent;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/jobs/{jobId}/events")
public class JobEventController {
    private final JobEventQueryService queries;
    private final CurrentUserProvider users;

    public JobEventController(JobEventQueryService queries, CurrentUserProvider users) {
        this.queries = queries;
        this.users = users;
    }

    @GetMapping(params = "after", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PollingResult> poll(
            @PathVariable String jobId,
            @RequestParam long after,
            HttpServletRequest request) {
        JobEventQueryService.PollingPage page = queries.poll(users.currentUserId(), jobId, after);
        return ApiResponse.success(new PollingResult(
            page.events(), page.nextSequence(), page.latestSequence(), page.hasMore()),
            request.getHeader("X-Request-Id"));
    }

    public record PollingResult(
        java.util.List<JobEventView> events,
        long nextSequence,
        long latestSequence,
        boolean hasMore
    ) { }
}
