package com.aivle.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aivle.backend.integration.ai.dto.MarketingBannerResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AiServerMarketingClientTests {

    private static final String URL =
        "http://ai.test/api/v1/marketing/banners/generate";

    private MockRestServiceServer server;
    private AiServerMarketingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AiServerClientSupport support = new AiServerClientSupport(
            new AiServerProperties(
                "http://ai.test",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                ""
            ),
            new ObjectMapper()
        );
        client = new AiServerMarketingClient(
            builder.build(),
            support
        );
    }

    @Test
    void sendsMultipartRequestIdAndReturnsTypedResult()
        throws Exception {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                assertEquals(
                    "marketing-request",
                    request.getHeaders().getFirst("X-Request-Id")
                );
                assertFalse(
                    request.getHeaders()
                        .containsHeader("X-Internal-Api-Key")
                );
                String body = ((MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
                assertTrue(body.contains("name=\"promotion_name\""));
                assertTrue(body.contains("name=\"main_banner\""));
                assertTrue(body.contains("name=\"supporting_copy\""));
                assertTrue(body.contains("name=\"mood\""));
                assertTrue(body.contains("name=\"banner_format\""));
                assertTrue(body.contains("name=\"emphasis_keywords\""));
                assertTrue(body.contains("여름 프로모션"));
                assertTrue(body.contains("filename=\"한글 상품.png\""));
            })
            .andRespond(withSuccess(
                successBody(),
                MediaType.APPLICATION_JSON
            ));

        MarketingBannerResult response = client.generateBanner(
            "여름 프로모션",
            "지금 시작하세요",
            "특별 혜택",
            "신뢰감 있는",
            "가로형 배너",
            "혜택,신규",
            image(),
            "marketing-request"
        );

        assertEquals("completed", response.status());
        assertEquals(
            "여름 프로모션",
            response.data().promotionName()
        );
        assertEquals(
            "banner-1",
            response.banner().bannerId()
        );
        assertEquals(
            "한글 상품.png",
            response.image().originalFilename()
        );
        assertEquals("marketing-request", response.requestId());
        server.verify();
    }

    @Test
    void converts4xxEnvelopeWithoutRetry() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(
                    "UNSUPPORTED_IMAGE_TYPE",
                    false
                )));

        AiServerException exception = assertThrows(
            AiServerException.class,
            () -> generate("client-request")
        );

        assertEquals(415, exception.getStatusCode());
        assertEquals(
            "UNSUPPORTED_IMAGE_TYPE",
            exception.getErrorCode()
        );
        assertEquals("fastapi-request", exception.getRequestId());
        assertFalse(exception.isRetryable());
    }

    @Test
    void converts5xxEnvelopeAsRetryableWithoutRetrying() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(
                    "AI_SERVER_INTERNAL_ERROR",
                    true
                )));

        AiServerException exception = assertThrows(
            AiServerException.class,
            () -> generate("server-error-request")
        );

        assertEquals(500, exception.getStatusCode());
        assertEquals(
            "AI_SERVER_INTERNAL_ERROR",
            exception.getErrorCode()
        );
        assertEquals(
            "AI 서버에서 요청을 처리하지 못했습니다.",
            exception.getSafeMessage()
        );
        assertTrue(exception.isRetryable());
        server.verify();
    }

    @Test
    void missingBodyBecomesInvalidResponse() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess());

        AiServerException exception = assertThrows(
            AiServerException.class,
            () -> generate("empty-response")
        );

        assertEquals(
            "AI_SERVER_INVALID_RESPONSE",
            exception.getErrorCode()
        );
    }

    private MarketingBannerResult generate(String requestId)
        throws Exception {
        return client.generateBanner(
            "프로모션",
            "메인",
            "보조",
            "신뢰감 있는",
            "가로형 배너",
            "",
            image(),
            requestId
        );
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
            "image",
            "한글 상품.png",
            "image/png",
            "mock-image".getBytes(StandardCharsets.UTF_8)
        );
    }

    private String successBody() {
        return """
            {
              "status":"completed",
              "message":"Mock banner",
              "data":{
                "promotion_name":"여름 프로모션",
                "main_banner":"지금 시작하세요",
                "supporting_copy":"특별 혜택",
                "mood":"신뢰감 있는",
                "banner_format":"가로형 배너",
                "emphasis_keywords":["혜택","신규"]
              },
              "prompt_preview":"prompt",
              "banner":{
                "banner_id":"banner-1",
                "preview_url":"http://ai.test/outputs/banner-1.png",
                "mock":true
              },
              "image":{
                "original_filename":"한글 상품.png",
                "content_type":"image/png",
                "size":10
              },
              "request_id":"marketing-request"
            }
            """;
    }

    private String errorBody(String code, boolean retryable) {
        return """
            {
              "request_id":"fastapi-request",
              "error":{
                "code":"%s",
                "message":"safe message",
                "retryable":%s
              }
            }
            """.formatted(code, retryable);
    }
}
