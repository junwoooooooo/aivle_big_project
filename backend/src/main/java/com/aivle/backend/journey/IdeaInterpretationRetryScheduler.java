package com.aivle.backend.journey;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdeaInterpretationRetryScheduler {
    private final JourneyAiService journey;

    public IdeaInterpretationRetryScheduler(JourneyAiService journey) {
        this.journey = journey;
    }

    @Scheduled(fixedDelayString = "${app.task-run.idea-interpretation-retry-poll-interval-ms:1000}")
    public void poll() {
        journey.executeNextInterpretationRetry();
    }
}
