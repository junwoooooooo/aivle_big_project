package com.aivle.backend.integration.ai;

import java.io.IOException;

import com.aivle.backend.integration.ai.dto.AiServerHealthResponse;
import com.aivle.backend.integration.ai.dto.MarketingBannerResult;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local connection probe only; this is not a production marketing API.
 */
@RestController
@Profile({"local", "dev-header-auth"})
@RequestMapping("/api/v1/test/ai-server")
public class AiServerTestController {

    private final AiServerHealthClient aiServerHealthClient;
    private final AiServerMarketingClient aiServerMarketingClient;

    public AiServerTestController(
        AiServerHealthClient aiServerHealthClient,
        AiServerMarketingClient aiServerMarketingClient
    ) {
        this.aiServerHealthClient = aiServerHealthClient;
        this.aiServerMarketingClient = aiServerMarketingClient;
    }

    @GetMapping("/health")
    public AiServerHealthResponse checkHealth(
        @RequestHeader(
            value = AiServerClientSupport.REQUEST_ID_HEADER,
            required = false
        )
        String requestId
    ) {
        return aiServerHealthClient.checkReady(requestId);
    }

    @PostMapping(
        value = "/marketing/banners/generate",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public MarketingBannerResult generateBanner(
        @RequestParam("promotion_name")
        String promotionName,
        @RequestParam("main_banner")
        String mainBanner,
        @RequestParam("supporting_copy")
        String supportingCopy,
        @RequestParam("mood")
        String mood,
        @RequestParam("banner_format")
        String bannerFormat,
        @RequestParam(
            value = "emphasis_keywords",
            defaultValue = ""
        )
        String emphasisKeywords,
        @RequestPart("image")
        MultipartFile image,

        @RequestHeader(
            value = AiServerClientSupport.REQUEST_ID_HEADER,
            required = false
        )
        String requestId
    ) throws IOException {
        return aiServerMarketingClient.generateBanner(
            promotionName,
            mainBanner,
            supportingCopy,
            mood,
            bannerFormat,
            emphasisKeywords,
            image,
            requestId
        );
    }
}
