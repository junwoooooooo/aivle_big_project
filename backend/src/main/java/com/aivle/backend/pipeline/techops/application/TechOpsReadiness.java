package com.aivle.backend.pipeline.techops.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TechOpsReadiness {
    public List<String> missing(JsonNode facts, JsonNode decisions) {
        List<String> missing = new ArrayList<>();
        for (String key : TechOpsPreparationFactory.REQUIRED_FACT_KEYS) {
            JsonNode item = facts.path(key);
            if (!TechOpsPreparationFactory.present(item.path("value"))
                    || !"LOCKED".equals(item.path("decision").asText())) missing.add(key);
        }
        for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) {
            JsonNode item = decisions.path(key); String decision = item.path("decision").asText();
            if (!("ACCEPTED".equals(decision) || "USER_EDITED_ACCEPTED".equals(decision))
                    || !TechOpsPreparationFactory.present(item.path("finalValue"))) missing.add(key);
        }
        return List.copyOf(missing);
    }
}
