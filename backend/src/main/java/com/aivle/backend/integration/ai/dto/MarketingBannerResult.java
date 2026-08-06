package com.aivle.backend.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarketingBannerResult(
    String status,
    String message,
    MarketingBannerInput data,
    @JsonProperty("prompt_preview")
    String promptPreview,
    MarketingBannerInfo banner,
    UploadedImageInfo image,
    @JsonProperty("request_id")
    String requestId
) {
}
