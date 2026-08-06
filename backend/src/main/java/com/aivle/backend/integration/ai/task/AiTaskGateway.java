package com.aivle.backend.integration.ai.task;

import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;

public interface AiTaskGateway {
    AiTaskResponse execute(AiTaskRequest request);
}
