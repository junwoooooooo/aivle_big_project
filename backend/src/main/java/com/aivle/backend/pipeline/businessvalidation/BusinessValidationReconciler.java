package com.aivle.backend.pipeline.businessvalidation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessValidationReconciler {

    private final BusinessValidationCoordinator coordinator;

    @Scheduled(fixedDelayString = "${app.business-validation.reconcile-interval-ms:1500}")
    public void reconcile() {
        for (String sessionId : coordinator.activeSessionIds()) {
            try { coordinator.reconcile(sessionId); }
            catch (RuntimeException failure) {
                log.warn("Business Validation reconciliation deferred: session={}", sessionId, failure);
            }
        }
    }
}
