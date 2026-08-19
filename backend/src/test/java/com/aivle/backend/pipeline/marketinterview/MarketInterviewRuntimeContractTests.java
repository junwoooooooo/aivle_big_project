package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.pipeline.market.MarketInterviewWorker;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MarketInterviewRuntimeContractTests {

    @Test
    void mainWorkerKeepsMainBudgetAndLease() throws Exception {
        Duration budget = duration("BUDGET");
        Duration lease = duration("LEASE");
        assertThat(budget).isEqualTo(Duration.ofMinutes(10));
        assertThat(lease).isEqualTo(Duration.ofMinutes(13));
        assertThat(lease).isGreaterThan(budget);
    }

    @Test
    void profileBankTransportOutlivesWorkerDeadline() throws Exception {
        AiServerProperties properties = new AiServerProperties(null, null, null, null, null, null);
        assertThat(properties.twinSurveyReadTimeout())
            .isGreaterThan(duration("BUDGET"));
    }

    private Duration duration(String name) throws Exception {
        Field field = MarketInterviewWorker.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Duration) field.get(null);
    }
}
