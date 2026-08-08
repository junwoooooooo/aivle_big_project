package com.aivle.backend.pipeline.finance.application;

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

public interface FinanceEstimateGateway {
    JsonNode estimate(JsonNode input);

    @Component
    @RequiredArgsConstructor
    class Internal implements FinanceEstimateGateway {
        private final InternalAiExecutionClient client;
        private final CanonicalInputHasher hasher;
        private final ObjectMapper mapper;
        public JsonNode estimate(JsonNode input) {
            String id=UUID.randomUUID().toString(), json=mapper.writeValueAsString(input);
            TaskRunWorkerContext context=new TaskRunWorkerContext(id,0L,0L,TaskType.FINANCE_ESTIMATE,
                "FINANCE_ESTIMATE",id,json,hasher.hash(TaskType.FINANCE_ESTIMATE,"1.0","ko-KR",json),
                id,id,"1.0","1.0","ko-KR",0,1);
            return client.executeWorker(context,id,LocalDateTime.now().plusMinutes(3)).result();
        }
    }
}
