package com.aivle.backend.pipeline.concept.worker;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public interface ConceptFactoryAiGateway {
    JsonNode execute(TaskType type, String inputJson, String correlationId, String attemptId);

    @Component
    @RequiredArgsConstructor
    class Internal implements ConceptFactoryAiGateway {
        private final InternalAiExecutionClient client;
        private final CanonicalInputHasher hasher;
        private final ObjectMapper mapper;

        @Override
        public JsonNode execute(TaskType type, String inputJson, String correlationId, String attemptId) {
            String childRunId = UUID.randomUUID().toString();
            TaskRunWorkerContext context = new TaskRunWorkerContext(childRunId, 0L, 0L, type,
                "CONCEPT_ATTEMPT", attemptId, inputJson, hasher.hash(type, "1.0", "ko-KR", inputJson),
                childRunId, correlationId, "1.0", "1.0", "ko-KR", 0, 1);
            return client.executeWorker(context, attemptId, LocalDateTime.now().plusMinutes(3)).result();
        }
    }
}
