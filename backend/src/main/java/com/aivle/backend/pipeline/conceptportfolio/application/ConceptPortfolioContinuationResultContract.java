package com.aivle.backend.pipeline.conceptportfolio.application;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioContinuationOutcome;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ConceptPortfolioContinuationResultContract {
    public Validated validate(JsonNode result) {
        require(result != null && result.isObject());
        require("concept-portfolio-v2-continuation-result-v1".equals(text(result, "contract")));
        require("1.0".equals(text(result, "contractVersion")));
        require("1.0".equals(text(result, "schemaVersion")));
        String inputRequestId = text(result, "inputRequestId");
        String candidateId = text(result, "candidateId");
        String lineageId = text(result, "lineageId");
        ConceptPortfolioContinuationOutcome outcome;
        try { outcome = ConceptPortfolioContinuationOutcome.valueOf(text(result, "outcome")); }
        catch (IllegalArgumentException invalid) { throw new ContractViolation(); }
        JsonNode candidate = result.get("candidate");
        JsonNode legal = result.get("legalReview");
        if (outcome == ConceptPortfolioContinuationOutcome.ACCEPTED) {
            require(candidate != null && candidate.isObject() && legal != null && legal.isObject());
            require(candidateId.equals(text(candidate, "candidateId")));
            require(lineageId.equals(text(candidate, "lineageId")));
            require(candidateId.equals(text(legal, "candidateId")));
            require("ACCEPT".equals(text(legal, "route")));
        } else if (outcome == ConceptPortfolioContinuationOutcome.NEEDS_INPUT) {
            JsonNode required = result.get("requiredInput");
            JsonNode artifact = result.get("continuationArtifact");
            require(required != null && required.isObject() && artifact != null && artifact.isObject());
            require(candidateId.equals(text(required, "candidateId")));
            require(candidateId.equals(text(artifact, "candidateId")));
            require(lineageId.equals(text(artifact, "lineageId")));
        } else if (outcome == ConceptPortfolioContinuationOutcome.EXCLUDED) {
            text(result, "exclusionReason");
        } else {
            text(result, "failureCode");
        }
        return new Validated(result, inputRequestId, candidateId, lineageId, outcome);
    }

    static String text(JsonNode value, String field) {
        JsonNode child = value == null ? null : value.get(field);
        require(child != null && child.isTextual() && !child.asText().isBlank());
        return child.asText();
    }

    static void require(boolean condition) {
        if (!condition) throw new ContractViolation();
    }

    public record Validated(JsonNode result, String inputRequestId, String candidateId,
                            String lineageId, ConceptPortfolioContinuationOutcome outcome) { }
    public static final class ContractViolation extends RuntimeException { }
}
