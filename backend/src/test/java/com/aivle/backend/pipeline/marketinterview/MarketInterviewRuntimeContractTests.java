package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MarketInterviewRuntimeContractTests {

    @Test
    void deepEngineKeepsMainBudgetAndLongerLease() {
        assertThat(MarketInterviewWorker.BUDGET).isEqualTo(Duration.ofMinutes(10));
        assertThat(MarketInterviewWorker.LEASE).isEqualTo(Duration.ofMinutes(13));
        assertThat(MarketInterviewWorker.LEASE).isGreaterThan(MarketInterviewWorker.BUDGET);
    }

    @Test
    void profileBankTransportOutlivesWorkerDeadline() {
        AiServerProperties properties = new AiServerProperties(null, null, null, null, null, null);
        assertThat(properties.twinSurveyReadTimeout())
            .isGreaterThan(MarketInterviewWorker.BUDGET);
    }
}
