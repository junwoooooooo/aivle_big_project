package com.aivle.backend.pipeline.finance.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class FinancialReadiness {
    public List<String> missing(JsonNode fields) {
        List<String> missing = new ArrayList<>();
        for (String key : FinancialPreparationFactory.REQUIRED_KEYS) {
            JsonNode item=fields.path(key); String decision=item.path("decision").asText();
            if (!FinancialPreparationFactory.present(item.path("value"))
                    || !List.of("LOCKED","ACCEPTED","USER_EDITED_ACCEPTED").contains(decision)) missing.add(key);
        }
        return List.copyOf(missing);
    }
}
