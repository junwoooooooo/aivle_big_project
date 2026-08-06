package com.aivle.backend.integration.ai;

import com.aivle.backend.integration.ai.dto.MarketingBannerResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AiServerMarketingClient {

    private static final MediaType UTF8_TEXT = new MediaType(
        "text",
        "plain",
        StandardCharsets.UTF_8
    );

    private final RestClient restClient;
    private final AiServerClientSupport support;

    public AiServerMarketingClient(
        @Qualifier("aiServerRestClient")
        RestClient restClient,
        AiServerClientSupport support
    ) {
        this.restClient = restClient;
        this.support = support;
    }

    public MarketingBannerResult generateBanner(
        String promotionName,
        String mainBanner,
        String supportingCopy,
        String mood,
        String bannerFormat,
        String emphasisKeywords,
        MultipartFile image
    ) throws IOException {
        return generateBanner(
            promotionName,
            mainBanner,
            supportingCopy,
            mood,
            bannerFormat,
            emphasisKeywords,
            image,
            null
        );
    }

    public MarketingBannerResult generateBanner(
        String promotionName,
        String mainBanner,
        String supportingCopy,
        String mood,
        String bannerFormat,
        String emphasisKeywords,
        MultipartFile image,
        String candidateRequestId
    ) throws IOException {
        MultiValueMap<String, Object> multipartBody =
            new LinkedMultiValueMap<>();
        multipartBody.add(
            "promotion_name",
            createTextPart(promotionName)
        );
        multipartBody.add(
            "main_banner",
            createTextPart(mainBanner)
        );
        multipartBody.add(
            "supporting_copy",
            createTextPart(supportingCopy)
        );
        multipartBody.add(
            "mood",
            createTextPart(mood)
        );
        multipartBody.add(
            "banner_format",
            createTextPart(bannerFormat)
        );
        multipartBody.add(
            "emphasis_keywords",
            createTextPart(emphasisKeywords)
        );
        multipartBody.add(
            "image",
            createImagePart(image)
        );

        String requestId = support.resolveRequestId(
            candidateRequestId
        );
        return support.execute(
            requestId,
            () -> restClient.post()
                .uri("/api/v1/marketing/banners/generate")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers ->
                    support.addHeaders(headers, requestId)
                )
                .body(multipartBody)
                .retrieve()
                .body(MarketingBannerResult.class)
        );
    }

    private HttpEntity<String> createTextPart(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(UTF8_TEXT);
        return new HttpEntity<>(value, headers);
    }

    private HttpEntity<ByteArrayResource> createImagePart(
        MultipartFile image
    ) throws IOException {
        String filename = image.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "uploaded-image.jpg";
        }

        FilenameByteArrayResource resource =
            new FilenameByteArrayResource(
                image.getBytes(),
                filename
            );
        HttpHeaders headers = new HttpHeaders();
        if (
            image.getContentType() != null
            && !image.getContentType().isBlank()
        ) {
            headers.setContentType(
                MediaType.parseMediaType(
                    image.getContentType()
                )
            );
        }
        return new HttpEntity<>(resource, headers);
    }

    private static class FilenameByteArrayResource
        extends ByteArrayResource {

        private final String filename;

        private FilenameByteArrayResource(
            byte[] imageBytes,
            String filename
        ) {
            super(imageBytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
