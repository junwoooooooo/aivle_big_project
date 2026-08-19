package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewSourceResolver.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component("fullMarketInterviewInputFactory")
public class MarketInterviewInputFactory {
    private static final Set<String> BOARD_FIELDS = Set.of(
        "conceptName", "targetUsers", "problemScenario", "featureSet", "differentiators", "priceKrw");
    private final ObjectMapper mapper;
    private final com.aivle.backend.pipeline.market.MarketInterviewInputFactory mainInputs;

    public MarketInterviewInputFactory(
            ObjectMapper mapper,
            com.aivle.backend.pipeline.market.MarketInterviewInputFactory mainInputs) {
        this.mapper = mapper;
        this.mainInputs = mainInputs;
    }

    public String build(Source source, JsonNode requestedBoard, int sampleSize) {
        if (sampleSize != 20 && sampleSize != 40 && sampleSize != 80)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "표본 크기는 20, 40, 80 중 하나여야 합니다.");
        ObjectNode authoritative = board(source);
        ObjectNode stimulus = validateStimulus(requestedBoard, authoritative);
        return mainInputs.build(stimulus, sampleSize);
    }

    /** LLM-free MAIN six-cell board, derived only from the finalized document. */
    public ObjectNode board(Source source) {
        JsonNode selected = source.finalDocument().path("selectedConcept");
        JsonNode hypotheses = source.finalDocument().path("finalHypotheses");
        ObjectNode value = mapper.createObjectNode();
        value.put("conceptName", firstText(selected.path("identity").path("conceptName"),
            selected.path("identity").path("name"), selected.path("conceptName")));
        value.put("targetUsers", readable(selected.path("identity").path("targetUsers"), selected.path("targetUsers")));
        value.put("problemScenario", firstText(selected.path("solution").path("problemScenario"),
            selected.path("problemScenario"), selected.path("problem")));
        ArrayNode features = value.putArray("featureSet");
        JsonNode featureSource = selected.path("solution").path("featureSet");
        if (!featureSource.isArray()) featureSource = selected.path("featureSet");
        if (featureSource.isArray()) featureSource.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) features.add(item.asText().strip()); });
        value.put("differentiators", readable(hypotheses.path("differentiators").path("value"),
            selected.path("identity").path("differentiators"), selected.path("differentiators")));
        Long price = price(hypotheses.path("price").path("value"));
        if (price == null) value.putNull("priceKrw"); else value.put("priceKrw", price);
        if (value.path("conceptName").asText().isBlank())
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE, "최종 컨셉 이름을 읽을 수 없습니다.");
        return value;
    }

    public JsonNode preview(Source source) {
        ObjectNode value = mapper.createObjectNode(); value.set("conceptBoard", board(source)); return value;
    }

    private ObjectNode validateStimulus(JsonNode requested, ObjectNode authoritative) {
        if (requested == null || !requested.isObject())
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "응답자에게 보여줄 설명을 확인해 주세요.");
        Set<String> actual = new java.util.HashSet<>(); requested.propertyNames().forEach(actual::add);
        if (!actual.equals(BOARD_FIELDS))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "컨셉 보드 필드가 올바르지 않습니다.");
        ObjectNode value = mapper.createObjectNode();
        for (String field : List.of("conceptName", "targetUsers", "problemScenario", "differentiators")) {
            JsonNode item = requested.get(field);
            if (item == null || !item.isTextual() || item.asText().strip().isEmpty() || item.asText().strip().length() > 1200)
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 표현을 확인해 주세요.");
            value.put(field, item.asText().strip());
        }
        JsonNode features = requested.get("featureSet");
        if (features == null || !features.isArray() || features.isEmpty() || features.size() > 12)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "featureSet을 확인해 주세요.");
        ArrayNode copied = value.putArray("featureSet");
        for (JsonNode item : features) {
            if (!item.isTextual() || item.asText().strip().isEmpty() || item.asText().strip().length() > 500)
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "featureSet을 확인해 주세요.");
            copied.add(item.asText().strip());
        }
        JsonNode expectedPrice = authoritative.get("priceKrw"); JsonNode actualPrice = requested.get("priceKrw");
        boolean priceMatches = expectedPrice.isNull() ? actualPrice != null && actualPrice.isNull()
            : actualPrice != null && actualPrice.isIntegralNumber()
                && actualPrice.asLong() == expectedPrice.asLong();
        if (!priceMatches)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "가격은 최종 확정 가설과 같아야 합니다.");
        value.set("priceKrw", expectedPrice.deepCopy()); return value;
    }

    private String firstText(JsonNode... candidates) {
        for (JsonNode value : candidates) if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText().strip();
        return "";
    }
    private String readable(JsonNode... candidates) {
        for (JsonNode value : candidates) {
            if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText().strip();
            if (value != null && value.isArray()) { List<String> parts = new ArrayList<>();
                value.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) parts.add(item.asText().strip()); });
                if (!parts.isEmpty()) return String.join(" · ", parts); }
        }
        return "";
    }
    private Long price(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        if (value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0) return value.asLong();
        if (value.isObject() && value.path("amount").isIntegralNumber() && value.path("amount").asLong() >= 0) return value.path("amount").asLong();
        if (value.isTextual()) { String digits = value.asText().replaceAll("[^0-9]", "");
            if (!digits.isBlank()) try { return Long.parseLong(digits); } catch (NumberFormatException ignored) { return null; } }
        return null;
    }
}
