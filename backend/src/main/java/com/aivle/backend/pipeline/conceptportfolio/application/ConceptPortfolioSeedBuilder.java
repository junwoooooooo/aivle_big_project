package com.aivle.backend.pipeline.conceptportfolio.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ConceptPortfolioSeedBuilder {
    private static final List<String> REQUIRED = List.of("ideaOverview", "problem", "targetUsers");
    private final ObjectMapper mapper;

    public ConceptPortfolioSeedBuilder(ObjectMapper mapper) { this.mapper = mapper; }

    public BuiltInput build(IdeaBrief brief, List<IdeaBriefField> fields, int maxConcepts) {
        if (brief == null || !brief.isConfirmed() || maxConcepts < 1 || maxConcepts > 5) {
            throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        }
        Map<String, IdeaBriefField> byKey = new LinkedHashMap<>();
        for (IdeaBriefField field : fields) {
            if (!field.isDeleted()) byKey.put(field.getFieldKey(), field);
        }
        for (String key : REQUIRED) {
            IdeaBriefField field = byKey.get(key);
            if (field == null || field.getFieldValue() == null || field.getFieldValue().isBlank()) {
                throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
            }
        }
        JsonNode interpretation;
        try { interpretation = mapper.readTree(brief.getInterpretationJson()); }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID); }
        if (interpretation == null || !interpretation.isObject()) {
            throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        }

        ObjectNode seed = mapper.createObjectNode();
        seed.put("ideaBriefSnapshotId", brief.getId());
        seed.put("ideaOverview", byKey.get("ideaOverview").getFieldValue());
        seed.put("problem", byKey.get("problem").getFieldValue());
        seed.put("targetUsers", byKey.get("targetUsers").getFieldValue());
        ArrayNode values = seed.putArray("fields");
        for (IdeaBriefField field : fields) {
            if (field.isDeleted()) continue;
            ObjectNode value = values.addObject();
            value.put("fieldKey", field.getFieldKey());
            value.put("value", field.getFieldValue() == null ? "" : field.getFieldValue());
            value.put("source", field.getProvenance().name());
            value.put("decisionState", field.getDecisionState().name());
        }
        seed.set("interpretation", interpretation);
        ObjectNode input = mapper.createObjectNode();
        input.set("seed", seed);
        input.put("maxConcepts", maxConcepts);
        return new BuiltInput(input, mapper.writeValueAsString(input));
    }

    public record BuiltInput(JsonNode value, String json) { }
}
