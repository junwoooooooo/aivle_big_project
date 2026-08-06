package com.aivle.backend.admin;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AdminTaskRunService {
    private final TaskRunRepository runs;
    private final AiServerProperties aiServer;
    private final RestClient aiClient;

    public AdminTaskRunService(
        TaskRunRepository runs,
        AiServerProperties aiServer,
        @Qualifier("aiServerRestClient") RestClient aiClient
    ) {
        this.runs = runs;
        this.aiServer = aiServer;
        this.aiClient = aiClient;
    }

    public JobOverview overview() {
        List<JobItem> items = runs.findRecentForAdmin(PageRequest.of(0, 50)).stream()
            .map(this::item)
            .toList();
        boolean configured = aiServer.hasInternalApiKey() && aiServer.baseUrl() != null && !aiServer.baseUrl().isBlank();
        boolean available = configured && aiAvailable();
        return new JobOverview(
            configured ? "CONFIGURED" : "NOT_CONFIGURED",
            available ? "AVAILABLE" : "UNAVAILABLE",
            runs.countByStateAndDeletedAtIsNull(TaskRunState.QUEUED)
                + runs.countByStateAndDeletedAtIsNull(TaskRunState.READY),
            runs.countByStateAndDeletedAtIsNull(TaskRunState.RUNNING),
            runs.countByStateAndDeletedAtIsNull(TaskRunState.FAILED)
                + runs.countByStateAndDeletedAtIsNull(TaskRunState.TIMED_OUT),
            items
        );
    }

    private boolean aiAvailable() {
        try {
            return aiClient.get().uri("/health/ready").retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
        } catch (RestClientException failure) {
            return false;
        }
    }

    private JobItem item(TaskRun run) {
        return new JobItem(
            run.getId(), run.getProject().getId(), run.getProject().getTitle(),
            run.getTaskType().name(), run.getState().name(), run.getLastErrorCode(),
            run.isRetryable(), run.getAttemptCount(), run.getCreatedAt(), run.getUpdatedAt()
        );
    }

    public record JobOverview(
        String configurationStatus, String availabilityStatus,
        long pending, long running, long failed, List<JobItem> items
    ) { }

    public record JobItem(
        String id, Long projectId, String projectName, String taskType, String state,
        String lastError, boolean retryable, int attemptCount,
        LocalDateTime createdAt, LocalDateTime updatedAt
    ) { }
}
