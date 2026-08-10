package com.aivle.backend.pipeline.concept.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRetryPolicyTests {
    @Test
    void appliesBoundedBackoffAndHonorsSafeRetryAfter() {
        List<Long> sleeps = new ArrayList<>();
        ProviderRetryPolicy policy = new ProviderRetryPolicy(sleeps::add);

        assertThat(policy.canRetry(0)).isTrue();
        assertThat(policy.canRetry(1)).isTrue();
        assertThat(policy.canRetry(2)).isFalse();
        policy.pause(1, null);
        policy.pause(2, 12_000L);

        assertThat(sleeps).containsExactly(2_000L, 12_000L);
    }
}
