package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MarketResearchRuntimeContractTests {

    @Test
    void freshCollectionKeepsSixtyMinuteBudgetAndLongerLease() {
        // 20 → 60 (2026-08-16 병합): 호출 수 266→470, 발췌가 추론 모델이 됐다.
        // 20분이면 절 체인 도중에 잘리고, REQUEST_DEADLINE_EXCEEDED 는 retryable 이라
        // 이미 지불한 수집을 버리고 같은 것을 한 번 더 태운다.
        assertThat(MarketResearchWorker.BUDGET).isEqualTo(Duration.ofMinutes(60));
        assertThat(MarketResearchWorker.LEASE).isEqualTo(Duration.ofMinutes(63));
        assertThat(MarketResearchWorker.LEASE).isGreaterThan(MarketResearchWorker.BUDGET);
    }

    @Test
    void marketTransportDoesNotExpireBeforeWorkerDeadline() {
        AiServerProperties properties = new AiServerProperties(null, null, null, null, null, null);

        assertThat(properties.marketResearchReadTimeout())
            .isGreaterThanOrEqualTo(MarketResearchWorker.BUDGET);
        assertThat(properties.readTimeout()).isLessThan(MarketResearchWorker.BUDGET);
    }
}
