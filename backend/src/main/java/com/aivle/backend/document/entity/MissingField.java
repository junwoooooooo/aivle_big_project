package com.aivle.backend.document.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.document.structure.MissingFieldDraft;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "missing_fields")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissingField extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structured_plan_id", nullable = false) private StructuredPlan structuredPlan;
    @Column(nullable = false, length = 100) private String fieldCode;
    @Column(nullable = false, length = 150) private String label;
    @Column(nullable = false) private Boolean required;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MissingFieldStatus status;
    @Column(columnDefinition = "TEXT") private String userValueJson;
    @Enumerated(EnumType.STRING) @Column(length = 80) private BusinessPlanSectionCode sectionCode;
    @Column(columnDefinition = "TEXT") private String reason;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Priority priority;

    private MissingField(StructuredPlan structuredPlan, MissingFieldDraft draft) {
        this.structuredPlan = structuredPlan;
        this.fieldCode = draft.fieldCode();
        this.label = draft.label();
        this.required = draft.required();
        this.status = draft.status();
        this.sectionCode = draft.sectionCode();
        this.reason = draft.reason();
        this.priority = draft.priority();
    }

    public static MissingField create(StructuredPlan structuredPlan, MissingFieldDraft draft) {
        return new MissingField(structuredPlan, draft);
    }

    public void fill(String value) {
        this.status = MissingFieldStatus.FILLED;
        this.userValueJson = value;
    }

    public void waive(String waiverReason) {
        this.status = MissingFieldStatus.WAIVED;
        this.userValueJson = null;
        this.reason = waiverReason;
    }

    public boolean isResolved() {
        return status == MissingFieldStatus.FILLED
            || status == MissingFieldStatus.WAIVED;
    }
}
