package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

class MarketResearchRuntimeContractTests {

    @Test
    void boundedFreshCollectionKeepsTwentyMinuteBudgetAndLongerLease() {
        assertThat(MarketResearchWorker.BUDGET).isEqualTo(Duration.ofMinutes(20));
        assertThat(MarketResearchWorker.LEASE).isEqualTo(Duration.ofMinutes(22));
        assertThat(MarketResearchWorker.LEASE).isGreaterThan(MarketResearchWorker.BUDGET);
    }

    @Test
    void marketTransportDoesNotExpireBeforeWorkerDeadline() {
        AiServerProperties properties = new AiServerProperties(null, null, null, null, null, null);

        assertThat(properties.marketResearchReadTimeout())
            .isGreaterThanOrEqualTo(MarketResearchWorker.BUDGET);
        assertThat(properties.readTimeout()).isLessThan(MarketResearchWorker.BUDGET);
    }

    @Test
    void projectionRepairRepositoryBindsExactMarketTaskAndPlanRevision() {
        assertThat(new PartTree(
            "findTopByProjectIdAndKindAndSourceRunTaskRunIdAndSourceBmPlanRevisionAndDeletedAtIsNullOrderByCreatedAtDescIdDesc",
            MarketResearchRun.class).getParts()).hasSize(5);
    }
}
