package com.aivle.backend.jobevent;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.security.CurrentUserProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v2/jobs/{jobId}/events")
public class JobEventSseController {
    private final JobEventQueryService queries;
    private final JobEventStreamService streams;
    private final CurrentUserProvider users;

    public JobEventSseController(JobEventQueryService queries, JobEventStreamService streams,
            CurrentUserProvider users) {
        this.queries = queries;
        this.streams = streams;
        this.users = users;
    }

    @GetMapping(params = "!after", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @PathVariable String jobId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        Long ownerId = users.currentUserId();
        long cursor = parseCursor(lastEventId);
        queries.verifyOwnership(ownerId, jobId);
        SseEmitter emitter = streams.subscribe(jobId, () -> queries.replay(ownerId, jobId, cursor));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Void> handleStreamFailure(BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus()).build();
    }

    private long parseCursor(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            long cursor = Long.parseLong(value);
            if (cursor < 0) throw new NumberFormatException();
            return cursor;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
