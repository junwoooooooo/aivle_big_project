package com.aivle.backend.config;

import com.aivle.backend.integration.ai.legal.LegalPipelineAdapter;
import com.aivle.backend.integration.ai.legal.LegalReviewAiClient;
import com.aivle.backend.integration.ai.legal.MockLegalReviewAiClient;
import com.aivle.backend.integration.ai.legal.OpenAiLegalReviewAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 법률 포트는 후보가 셋이라 단일 플래그로 가를 수 없다.
 * 어느 설정에서도 구현체가 정확히 하나만 뜨는지 확인한다.
 */
class LegalAiConfigurationTests {

    @Test
    void autoFollowsAiEnabledFlag() {
        runner("auto", false).run(context -> assertThat(context)
            .getBean(LegalReviewAiClient.class).isInstanceOf(MockLegalReviewAiClient.class));

        runner("auto", true).run(context -> assertThat(context)
            .getBean(LegalReviewAiClient.class).isInstanceOf(OpenAiLegalReviewAdapter.class));
    }

    @Test
    void explicitProviderWinsOverAiEnabledFlag() {
        runner("pipeline", true).run(context -> assertThat(context)
            .getBean(LegalReviewAiClient.class).isInstanceOf(LegalPipelineAdapter.class));

        runner("mock", true).run(context -> assertThat(context)
            .getBean(LegalReviewAiClient.class).isInstanceOf(MockLegalReviewAiClient.class));

        runner("openai", false).run(context -> assertThat(context)
            .getBean(LegalReviewAiClient.class).isInstanceOf(OpenAiLegalReviewAdapter.class));
    }

    @Test
    void exactlyOneImplementationIsRegistered() {
        for (String provider : new String[] {"auto", "mock", "openai", "pipeline"}) {
            runner(provider, true).run(context -> assertThat(
                context.getBeanNamesForType(LegalReviewAiClient.class)).hasSize(1));
        }
    }

    private ApplicationContextRunner runner(String provider, boolean aiEnabled) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(LegalAiConfiguration.class)
            .withBean(LegalServiceProperties.class, () -> new LegalServiceProperties(
                provider, "http://127.0.0.1:8001/legal-review",
                Duration.ofSeconds(5), Duration.ofSeconds(300), 200000, 1048576))
            .withBean(AiProperties.class, () -> new AiProperties(
                aiEnabled, "http://127.0.0.1:9/v1", "test-model", "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0, 200000, 1048576));
    }
}
