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
        String model = fields.path("revenueModel").path("value").asText();
        if (!List.of("ONE_TIME", "SUBSCRIPTION", "HYBRID").contains(model)) missing.add("revenueModel");
        if ("ONE_TIME".equals(model) || "HYBRID".equals(model)) required(missing, fields, "unitPrice");
        if ("SUBSCRIPTION".equals(model) || "HYBRID".equals(model)) {
            required(missing, fields, "monthlySubscriptionPrice");
            required(missing, fields, "monthlyChurnRate");
        }
        return List.copyOf(missing);
    }

    private void required(List<String> missing, JsonNode fields, String key) {
        JsonNode item = fields.path(key);
        if (!FinancialPreparationFactory.present(item.path("value"))
                || !List.of("LOCKED", "ACCEPTED", "USER_EDITED_ACCEPTED").contains(item.path("decision").asText())) {
            if (!missing.contains(key)) missing.add(key);
        }
    }
}
