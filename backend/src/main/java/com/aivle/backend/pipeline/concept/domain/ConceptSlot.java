package com.aivle.backend.pipeline.concept.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptSlot extends BaseEntity {
    private static final Map<ConceptSlotStatus, Set<ConceptSlotStatus>> TRANSITIONS = Map.ofEntries(
        Map.entry(ConceptSlotStatus.QUEUED, EnumSet.of(ConceptSlotStatus.GENERATING, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.GENERATING, EnumSet.of(ConceptSlotStatus.GENERATED, ConceptSlotStatus.SCHEMA_INVALID, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.GENERATED, EnumSet.of(ConceptSlotStatus.VALIDATING_ORIGIN, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.SCHEMA_INVALID, EnumSet.of(ConceptSlotStatus.GENERATING, ConceptSlotStatus.REPLACING, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.VALIDATING_ORIGIN, EnumSet.of(ConceptSlotStatus.VALIDATING_LEGAL, ConceptSlotStatus.REPLACING, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.VALIDATING_LEGAL, EnumSet.of(ConceptSlotStatus.ELIGIBLE, ConceptSlotStatus.REDESIGNING, ConceptSlotStatus.REJECTED, ConceptSlotStatus.NEEDS_INPUT, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.REDESIGNING, EnumSet.of(ConceptSlotStatus.GENERATING, ConceptSlotStatus.REPLACING, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.REPLACING, EnumSet.of(ConceptSlotStatus.GENERATING, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.REJECTED, EnumSet.of(ConceptSlotStatus.REPLACING, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.NEEDS_INPUT, EnumSet.of(ConceptSlotStatus.QUEUED, ConceptSlotStatus.FAILED, ConceptSlotStatus.STALE)),
        Map.entry(ConceptSlotStatus.FAILED, EnumSet.of(ConceptSlotStatus.QUEUED, ConceptSlotStatus.STALE))
    );

    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_id", nullable = false) private ConceptFactoryRun run;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false) private int slotNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private VariationFocus variationFocus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ConceptSlotStatus status;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private int legalRedesignCount;

    public static ConceptSlot create(ConceptFactoryRun run, int slotNumber, VariationFocus focus) {
        if (slotNumber < 1 || slotNumber > ConceptFactoryLimits.SLOT_COUNT) throw new IllegalArgumentException("slot number must be 1..5");
        ConceptSlot slot = new ConceptSlot();
        slot.id = UUID.randomUUID().toString();
        slot.run = run;
        slot.projectId = run.getProject().getId();
        slot.slotNumber = slotNumber;
        slot.variationFocus = focus;
        slot.status = ConceptSlotStatus.QUEUED;
        return slot;
    }

    public void transitionTo(ConceptSlotStatus next) {
        if (next == status) return;
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(next)) throw new IllegalStateException("invalid slot transition: " + status + " -> " + next);
        status = next;
    }

    public int beginAttempt(ConceptAttemptPhase phase) {
        if (phase == ConceptAttemptPhase.REDESIGN) {
            if (legalRedesignCount >= ConceptFactoryLimits.MAX_LEGAL_REDESIGNS_PER_SLOT) throw new IllegalStateException("legal redesign limit exceeded");
            legalRedesignCount++;
        }
        attemptCount++;
        return attemptCount;
    }
}
