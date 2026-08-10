package com.aivle.backend.jobevent;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectEventQueryService {
    static final int MAX_POLL_EVENTS = 100;
    private final ProjectRepository projects;
    private final JobEventRepository events;
    private final ObjectMapper mapper;

    public ProjectEventQueryService(ProjectRepository projects, JobEventRepository events,
            ObjectMapper mapper) {
        this.projects = projects; this.events = events; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<JobEventView> replay(Long ownerId, Long projectId, long afterEventId) {
        verifyOwnership(ownerId, projectId); validateCursor(afterEventId);
        return events.findByProjectIdAndIdGreaterThanAndDeletedAtIsNullOrderById(
                projectId, afterEventId, PageRequest.of(0, MAX_POLL_EVENTS))
            .stream().map(event -> JobEventView.from(event, mapper)).toList();
    }

    @Transactional(readOnly = true)
    public PollingPage poll(Long ownerId, Long projectId, long afterEventId) {
        List<JobEventView> page = replay(ownerId, projectId, afterEventId);
        long next = page.isEmpty() ? afterEventId
            : Long.parseLong(page.get(page.size() - 1).eventId());
        long latest = events.findTopByProjectIdAndDeletedAtIsNullOrderByIdDesc(projectId)
            .map(JobEvent::getId).orElse(afterEventId);
        return new PollingPage(page, next, latest, next < latest);
    }

    @Transactional(readOnly = true)
    public void verifyOwnership(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void validateCursor(long value) {
        if (value < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    public record PollingPage(List<JobEventView> events, long nextEventId,
                              long latestEventId, boolean hasMore) { }
}
