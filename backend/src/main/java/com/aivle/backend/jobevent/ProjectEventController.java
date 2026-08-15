package com.aivle.backend.jobevent;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/events")
public class ProjectEventController {
    private final ProjectEventQueryService queries;
    private final ProjectEventStreamService streams;
    private final CurrentUserProvider users;
    public ProjectEventController(ProjectEventQueryService queries, ProjectEventStreamService streams,
            CurrentUserProvider users) { this.queries=queries; this.streams=streams; this.users=users; }

    @GetMapping(params="!after",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable Long projectId,
            @RequestHeader(name="Last-Event-ID",required=false) String lastEventId) {
        Long ownerId=users.currentUserId(); long cursor=parseCursor(lastEventId);
        queries.verifyOwnership(ownerId,projectId);
        SseEmitter emitter=streams.subscribe(projectId,()->queries.replay(ownerId,projectId,cursor));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).header("X-Accel-Buffering","no")
            .contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    @GetMapping(params="after",produces=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PollingResult> poll(@PathVariable Long projectId,@RequestParam long after,
            HttpServletRequest request){var page=queries.poll(users.currentUserId(),projectId,after);
        return ApiResponse.success(new PollingResult(page.events(),page.nextEventId(),page.latestEventId(),page.hasMore()),request.getHeader("X-Request-Id"));}

    private long parseCursor(String value){if(value==null||value.isBlank())return 0;try{long cursor=Long.parseLong(value);if(cursor<0)throw new NumberFormatException();return cursor;}catch(NumberFormatException exception){throw new com.aivle.backend.common.exception.BusinessException(com.aivle.backend.common.exception.ErrorCode.INVALID_REQUEST);}}
    public record PollingResult(java.util.List<JobEventView> events,long nextEventId,long latestEventId,boolean hasMore){}
}
