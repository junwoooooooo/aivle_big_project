package com.aivle.backend.pipeline.concept.worker;

import com.aivle.backend.pipeline.concept.domain.ConceptFactoryLimits;
import java.util.function.LongConsumer;
import org.springframework.stereotype.Component;

@Component
public class ProviderRetryPolicy {
    private static final long[] FALLBACK_DELAYS_MS = {2_000, 5_000};
    private static final long MAX_DELAY_MS = 15_000;
    private final LongConsumer sleeper;

    public ProviderRetryPolicy() {
        this(ProviderRetryPolicy::sleep);
    }

    ProviderRetryPolicy(LongConsumer sleeper) {
        this.sleeper = sleeper;
    }

    public boolean canRetry(int retriesAlreadyPerformed) {
        return retriesAlreadyPerformed < ConceptFactoryLimits.MAX_PROVIDER_TRANSIENT_RETRIES_PER_CALL;
    }

    public long delayMillis(int retryNumber, Long providerRetryAfterMillis) {
        long fallback = FALLBACK_DELAYS_MS[Math.min(Math.max(retryNumber - 1, 0), FALLBACK_DELAYS_MS.length - 1)];
        if (providerRetryAfterMillis == null) return fallback;
        return Math.min(MAX_DELAY_MS, Math.max(1_000, providerRetryAfterMillis));
    }

    public void pause(int retryNumber, Long providerRetryAfterMillis) {
        sleeper.accept(delayMillis(retryNumber, providerRetryAfterMillis));
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("provider retry backoff interrupted", interrupted);
        }
    }
}
