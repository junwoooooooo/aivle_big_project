package com.aivle.backend.pipeline.concept.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concepts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concept extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_id", nullable = false) private ConceptFactoryRun run;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "slot_id", nullable = false) private ConceptSlot slot;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_idea_brief_snapshot_id", nullable = false, length = 64) private String sourceIdeaBriefSnapshotId;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, length = 71) private String canonicalHash;
    @Column(nullable = false, length = 71) private String majorFieldHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ConceptLegalStatus legalStatus;
    @Column(nullable = false) private boolean published;
    @Column(nullable = false, columnDefinition = "TEXT") private String candidateJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String originTraceJson;

    public static Concept eligible(
        ConceptFactoryRun run,
        ConceptSlot slot,
        String title,
        String summary,
        String canonicalHash,
        String majorFieldHash,
        ConceptLegalStatus legalStatus, String candidateJson, String originTraceJson
    ) {
        if (!legalStatus.isPubliclyEligible()) throw new IllegalArgumentException("concept legal status is not publicly eligible");
        Concept concept = new Concept();
        concept.id = UUID.randomUUID().toString();
        concept.run = run;
        concept.slot = slot;
        concept.projectId = run.getProject().getId();
        concept.sourceIdeaBriefSnapshotId = run.getSourceIdeaBriefSnapshotId();
        concept.sourceSnapshotHash = run.getSourceSnapshotHash();
        concept.title = title;
        concept.summary = summary;
        concept.canonicalHash = ConceptCanonicalizer.requireHash(canonicalHash);
        concept.majorFieldHash = ConceptCanonicalizer.requireHash(majorFieldHash);
        concept.legalStatus = legalStatus;
        concept.candidateJson = candidateJson;
        concept.originTraceJson = originTraceJson;
        return concept;
    }

    public void publish() {
        if (run.getStatus() != ConceptFactoryRunStatus.COMPLETED || !legalStatus.isPubliclyEligible()) {
            throw new IllegalStateException("only completed eligible concepts may be published");
        }
        published = true;
    }
}
