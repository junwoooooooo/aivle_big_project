package com.aivle.backend.pipeline.finance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FinancialCalculator {
    private final ObjectMapper mapper;
    public FinancialCalculator(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode calculateCac(JsonNode fields) {
        JsonNode marketing = fields.path("totalMarketingCost").path("value");
        JsonNode sales = fields.path("totalSalesCost").path("value");
        JsonNode customers = fields.path("newCustomerCount").path("value");
        if (!validMoney(marketing) || !validMoney(sales) || !customers.isNumber()
                || customers.decimalValue().compareTo(BigDecimal.ZERO) <= 0
                || !marketing.path("currency").asText().equals(sales.path("currency").asText())) return null;
        BigDecimal amount = marketing.path("amount").decimalValue().add(sales.path("amount").decimalValue())
            .divide(customers.decimalValue(), 2, RoundingMode.HALF_UP);
        ObjectNode result = mapper.createObjectNode();
        result.put("amount", amount);
        result.put("currency", marketing.path("currency").asText());
        result.put("formula", "(totalMarketingCost + totalSalesCost) / newCustomerCount");
        result.put("source", "SYSTEM_CALCULATION");
        return result;
    }

    private boolean validMoney(JsonNode value) {
        return value.isObject() && value.path("amount").isNumber() && value.path("amount").asDouble() >= 0
            && !value.path("currency").asText("").isBlank();
    }
}
