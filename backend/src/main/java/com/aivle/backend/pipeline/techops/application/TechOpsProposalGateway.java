package com.aivle.backend.pipeline.techops.application;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

public interface TechOpsProposalGateway {
    JsonNode propose(String contextJson, int proposalVersion, String rejectedProposalJson);

    @Component
    @RequiredArgsConstructor
    class Internal implements TechOpsProposalGateway {
        private final InternalAiExecutionClient client;
        private final CanonicalInputHasher hasher;

        @Override
        public JsonNode propose(String contextJson, int proposalVersion, String rejectedProposalJson) {
            String id = UUID.randomUUID().toString();
            String input = "{\"contextJson\":" + quote(contextJson) + ",\"proposalVersion\":" + proposalVersion
                + ",\"rejectedProposalJson\":" + quote(rejectedProposalJson == null ? "" : rejectedProposalJson) + "}";
            TaskRunWorkerContext context = new TaskRunWorkerContext(id, 0L, 0L, TaskType.TECH_OPS_PROPOSAL,
                "TECH_OPS_PROPOSAL", id, input, hasher.hash(TaskType.TECH_OPS_PROPOSAL, "1.0", "ko-KR", input),
                id, id, "1.0", "1.0", "ko-KR", 0, 1);
            return client.executeWorker(context, id, LocalDateTime.now().plusMinutes(3)).result();
        }

        private String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
        }
    }
}
