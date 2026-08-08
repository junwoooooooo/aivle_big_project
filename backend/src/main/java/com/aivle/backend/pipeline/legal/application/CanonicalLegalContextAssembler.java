package com.aivle.backend.pipeline.legal.application;

import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CanonicalLegalContextAssembler {
    private final ObjectMapper mapper;

    public CanonicalLegalContextAssembler(ObjectMapper mapper) { this.mapper = mapper; }

    public Result assemble(List<IdeaBriefField> fields) {
        List<Map<String, String>> externalFacts = fields.stream()
            .filter(field -> "targetRegion".equals(field.getFieldKey()))
            .filter(field -> field.getFieldValue() != null && !field.getFieldValue().isBlank())
            .filter(field -> field.getDecisionState() != null && "LOCKED".equals(field.getDecisionState().name()))
            .filter(field -> field.getProvenance() != null
                && ("USER_INPUT".equals(field.getProvenance().name())
                    || "USER_CONFIRMED".equals(field.getProvenance().name())))
            .map(field -> Map.of(
                "factKey", "fixedJurisdiction",
                "value", field.getFieldValue(),
                "source", "USER_INPUT",
                "authority", "LOCKED"))
            .toList();
        return new Result(mapper.writeValueAsString(externalFacts), mapper.writeValueAsString(Map.of(
            "externalFacts", "USER_INPUT_LOCKED",
            "conceptLegalFacts", "CONCEPT_GENERATED")));
    }

    public record Result(String contextJson, String provenanceJson) {}
}
