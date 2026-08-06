package com.aivle.backend.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarketingBannerInfo(
    @JsonProperty("banner_id")
    String bannerId,
    @JsonProperty("preview_url")
    String previewUrl,
    boolean mock
) {
}
