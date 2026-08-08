package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MarketingLegalGuard {
    private final ObjectMapper mapper;
    public MarketingLegalGuard(ObjectMapper mapper) { this.mapper = mapper; }

    public void validate(String sourceJson, JsonNode result) {
        JsonNode source = mapper.readTree(sourceJson);
        String rendered = (result.path("title").asText() + "\n" + result.path("body").asText() + "\n"
            + result.path("callToAction").asText() + "\n" + result.path("imageBrief").asText()).toLowerCase(Locale.ROOT);
        for (JsonNode claim : source.path("prohibitedClaims")) {
            String phrase = claim.asText("").strip();
            if (!phrase.isBlank() && rendered.contains(phrase.toLowerCase(Locale.ROOT)))
                throw new BusinessException(ErrorCode.MARKETING_PROHIBITED_CLAIM);
        }
        Set<String> applied = new HashSet<>();
        for (JsonNode value : result.path("legalReview").path("requiredDisclosuresApplied")) applied.add(value.asText().strip());
        for (JsonNode notice : source.path("requiredDisclosures")) {
            String required = notice.asText("").strip();
            if (!required.isBlank() && !applied.contains(required) && !rendered.contains(required.toLowerCase(Locale.ROOT)))
                throw new BusinessException(ErrorCode.MARKETING_PROHIBITED_CLAIM,
                    "필수 고지 문구를 적용한 뒤 저장해 주세요: " + required);
        }
    }
}
