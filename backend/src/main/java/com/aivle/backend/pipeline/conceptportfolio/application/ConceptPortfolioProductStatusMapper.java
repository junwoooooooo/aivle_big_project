package com.aivle.backend.pipeline.conceptportfolio.application;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ConceptPortfolioProductStatusMapper {
    private static final Set<String> TECHNICAL_FAILURES = Set.of(
        "RESULT_SCHEMA_INVALID", "PROVIDER_TRANSIENT", "PROVIDER_PERMANENT",
        "REPLAY_MISS", "NO_LEGAL_READY_CANDIDATES", "DEPENDENCY_UNAVAILABLE",
        "REQUEST_DEADLINE_EXCEEDED", "TASK_TIMEOUT"
    );

    public ConceptPortfolioRunStatus map(JsonNode result) {
        int produced = result.path("producedConceptCount").intValue();
        long candidateInputs = candidateInputCount(result.path("requiredInputs"));
        long allInputs = result.path("requiredInputs").size();
        String engineStatus = result.path("engineStatus").asText();
        JsonNode summary = result.path("runSummary");
        String failureCode = summary.isObject() && summary.hasNonNull("failureCode")
            ? summary.get("failureCode").asText() : null;
        boolean technical = failureCode != null && !failureCode.isBlank()
            && TECHNICAL_FAILURES.contains(failureCode);

        if ("NEEDS_INPUT".equals(engineStatus)) return ConceptPortfolioRunStatus.NEEDS_INPUT;
        if (produced > 0 && technical) return ConceptPortfolioRunStatus.FAILED;
        if (produced > 0) {
            return candidateInputs > 0
                ? ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT
                : ConceptPortfolioRunStatus.RESULTS_AVAILABLE;
        }
        if (allInputs > 0 && !technical) return ConceptPortfolioRunStatus.NEEDS_INPUT;
        return ConceptPortfolioRunStatus.FAILED;
    }

    private long candidateInputCount(JsonNode inputs) {
        if (!inputs.isArray()) return 0;
        long count = 0;
        for (JsonNode input : inputs) {
            if ("CANDIDATE".equals(input.path("scope").asText())) count++;
        }
        return count;
    }
}
