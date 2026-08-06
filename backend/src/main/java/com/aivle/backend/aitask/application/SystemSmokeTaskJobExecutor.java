package com.aivle.backend.aitask.application;

import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.AiTaskGateway;
import com.aivle.backend.integration.ai.task.AiTaskType;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.job.runner.AnalysisJobExecutor;
import com.aivle.backend.job.runner.JobClaim;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SystemSmokeTaskJobExecutor
    implements AnalysisJobExecutor {

    private final AiTaskGateway aiTaskGateway;
    private final SystemSmokeTaskPersistenceService persistence;
    private final ObjectMapper objectMapper;

    @Override
    public JobType jobType() {
        return JobType.SYSTEM_SMOKE_TEST;
    }

    @Override
    public void execute(JobClaim claim) {
        persistence.markDispatched(claim);
        String requestId = UUID.randomUUID().toString();
        AiTaskRequest request = new AiTaskRequest(
            requestId,
            claim.jobId().toString(),
            AiTaskType.SYSTEM_SMOKE_TEST,
            SystemSmokeTaskCommandService.SCHEMA_VERSION,
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode()
        );
        try {
            persistence.complete(
                claim,
                aiTaskGateway.execute(request)
            );
        } catch (AiServerException exception) {
            persistence.fail(claim, exception);
        }
    }
}
