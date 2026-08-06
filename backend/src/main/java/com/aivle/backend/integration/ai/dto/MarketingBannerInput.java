package com.aivle.backend.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MarketingBannerInput(
    @JsonProperty("promotion_name")
    String promotionName,
    @JsonProperty("main_banner")
    String mainBanner,
    @JsonProperty("supporting_copy")
    String supportingCopy,
    String mood,
    @JsonProperty("banner_format")
    String bannerFormat,
    @JsonProperty("emphasis_keywords")
    List<String> emphasisKeywords
) {
}
