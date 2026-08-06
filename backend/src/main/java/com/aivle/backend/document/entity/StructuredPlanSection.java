package com.aivle.backend.document.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.document.structure.StructuredItemStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import com.aivle.backend.document.structure.StructuredPlanSectionDraft;

@Entity @Table(name = "structured_plan_sections")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StructuredPlanSection extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structured_plan_id", nullable = false) private StructuredPlan structuredPlan;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private PlanSectionType sectionType;
    @Column(nullable = false, length = 150) private String title;
    @Column(columnDefinition = "TEXT") private String contentJson;
    @Column(columnDefinition = "TEXT") private String sourceText;
    @Column(precision = 5, scale = 4) private BigDecimal confidence;
    @Enumerated(EnumType.STRING) @Column(length = 80) private BusinessPlanSectionCode sectionCode;
    @Enumerated(EnumType.STRING) @Column(length = 20) private StructuredItemStatus itemStatus;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(columnDefinition = "TEXT") private String evidenceJson;
    @Column(columnDefinition = "TEXT") private String sourceBlockReferencesJson;
    @Column(name = "display_order") private Integer sequence;

    private StructuredPlanSection(
        StructuredPlan structuredPlan,
        PlanSectionType sectionType,
        StructuredPlanSectionDraft draft,
        int sequence,
        String evidenceJson,
        String sourceBlockReferencesJson
    ) {
        this.structuredPlan = structuredPlan;
        this.sectionType = sectionType;
        this.title = draft.title();
        this.sourceText = draft.extractedContent();
        this.confidence = draft.confidence();
        this.sectionCode = draft.sectionCode();
        this.itemStatus = draft.status();
        this.reason = draft.reason();
        this.evidenceJson = evidenceJson;
        this.sourceBlockReferencesJson = sourceBlockReferencesJson;
        this.sequence = sequence;
    }

    public static StructuredPlanSection create(
        StructuredPlan structuredPlan,
        PlanSectionType sectionType,
        StructuredPlanSectionDraft draft,
        int sequence,
        String evidenceJson,
        String sourceBlockReferencesJson
    ) {
        return new StructuredPlanSection(
            structuredPlan,
            sectionType,
            draft,
            sequence,
            evidenceJson,
            sourceBlockReferencesJson
        );
    }
}
