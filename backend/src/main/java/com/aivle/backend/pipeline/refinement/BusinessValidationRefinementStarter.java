package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCompletedEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessValidationRefinementStarter {

    private final ConceptRefinementService refinement;

    /**
     * Runs after the exact Market/BM completion transaction commits. A proposal scheduling failure
     * is intentionally isolated: the completed Business Validation session remains authoritative,
     * and the existing manual start action remains the recovery path.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startRoundOne(BusinessValidationCompletedEvent event) {
        String identity = String.join("|", event.sessionId(), String.valueOf(event.marketVersionId()),
            String.valueOf(event.bmVersionId()), event.marketSeedSnapshotId(),
            String.valueOf(event.selectionId()), String.valueOf(event.selectionRevision()),
            String.valueOf(event.bmPlanRevision()));
        String key = "bv-refinement-auto-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        try {
            refinement.start(event.ownerId(), event.projectId(), key, event.sessionId());
        } catch (RuntimeException failure) {
            log.warn("Business Validation completed but automatic refinement scheduling failed: session={} project={}",
                event.sessionId(), event.projectId(), failure);
        }
    }
}
