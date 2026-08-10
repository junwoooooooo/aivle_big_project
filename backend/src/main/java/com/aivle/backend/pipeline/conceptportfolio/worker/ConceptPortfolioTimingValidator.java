package com.aivle.backend.pipeline.conceptportfolio.worker;

import com.aivle.backend.integration.ai.AiServerProperties;
import org.springframework.stereotype.Component;

@Component
public class ConceptPortfolioTimingValidator {
    public ConceptPortfolioTimingValidator(ConceptPortfolioExecutionProperties execution,
            AiServerProperties aiServer) {
        if (execution.aiDeadline().compareTo(aiServer.conceptPortfolioReadTimeout()) >= 0
                || aiServer.conceptPortfolioReadTimeout().compareTo(execution.taskTimeout()) >= 0) {
            throw new IllegalArgumentException(
                "Concept Portfolio timing must satisfy AI deadline < read timeout < task timeout");
        }
    }
}
