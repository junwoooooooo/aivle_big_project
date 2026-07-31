package com.aivle.backend.config;

import com.aivle.backend.integration.ai.legal.LegalPipelineAdapter;
import com.aivle.backend.integration.ai.legal.LegalReviewAiClient;
import com.aivle.backend.integration.ai.legal.MockLegalReviewAiClient;
import com.aivle.backend.integration.ai.legal.OpenAiLegalReviewAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 법률 검토 포트 구현체를 하나만 고른다.
 * 다른 슬라이스는 {@code app.ai.enabled} 하나로 Mock/OpenAI를 가르지만,
 * 법률은 파이프라인 어댑터까지 세 후보가 있어 단일 플래그로는 구분할 수 없다.
 */
@Configuration
public class LegalAiConfiguration {
    @Bean
    LegalReviewAiClient legalReviewAiClient(
        AiProperties aiProperties,
        LegalServiceProperties legalServiceProperties,
        ObjectMapper objectMapper
    ) {
        String provider = legalServiceProperties.provider();
        if ("pipeline".equalsIgnoreCase(provider)) {
            return new LegalPipelineAdapter(legalServiceProperties, objectMapper);
        }
        if ("openai".equalsIgnoreCase(provider)) {
            return new OpenAiLegalReviewAdapter(aiProperties, objectMapper);
        }
        if ("mock".equalsIgnoreCase(provider)) {
            return new MockLegalReviewAiClient();
        }
        // auto: 지정이 없으면 기존 app.ai.enabled 동작을 그대로 따른다.
        return aiProperties.enabled()
            ? new OpenAiLegalReviewAdapter(aiProperties, objectMapper)
            : new MockLegalReviewAiClient();
    }
}
