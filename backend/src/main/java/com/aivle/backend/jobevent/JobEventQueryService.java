package com.aivle.backend.jobevent;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobEventQueryService {
    static final int MAX_POLL_EVENTS = 100;
    static final int MAX_REPLAY_EVENTS = 100;
    private final JobEventRepository events;
    private final ObjectMapper mapper;

    public JobEventQueryService(JobEventRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<JobEventView> replay(Long ownerId, String jobId, long after) {
        if (after < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        JobEvent latest = requireOwned(ownerId, jobId);
        long effectiveAfter = Math.max(after, latest.getSequence() - MAX_REPLAY_EVENTS);
        return events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
                jobId, latest.getProject().getId(), effectiveAfter).stream()
            .map(event -> JobEventView.from(event, mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public PollingPage poll(Long ownerId, String jobId, long after) {
        if (after < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        JobEvent latest = requireOwned(ownerId, jobId);
        List<JobEventView> page = events
            .findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
                jobId, latest.getProject().getId(), after, PageRequest.of(0, MAX_POLL_EVENTS))
            .stream()
            .map(event -> JobEventView.from(event, mapper))
            .toList();
        long nextSequence = page.isEmpty() ? after : page.get(page.size() - 1).sequence();
        long latestSequence = latest.getSequence();
        return new PollingPage(page, nextSequence, latestSequence, nextSequence < latestSequence);
    }

    @Transactional(readOnly = true)
    public void verifyOwnership(Long ownerId, String jobId) {
        requireOwned(ownerId, jobId);
    }

    private JobEvent requireOwned(Long ownerId, String jobId) {
        if (jobId == null || jobId.isBlank()) throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        JobEvent latest = events.findTopByJobIdAndDeletedAtIsNullOrderBySequenceDesc(jobId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (!latest.getProject().getOwner().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return latest;
    }

    public record PollingPage(
        List<JobEventView> events,
        long nextSequence,
        long latestSequence,
        boolean hasMore
    ) { }
}
