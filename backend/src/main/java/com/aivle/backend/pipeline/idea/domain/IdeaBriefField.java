package com.aivle.backend.pipeline.idea.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_brief_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaBriefField extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private IdeaBrief brief;

    @Column(nullable = false, length = 80)
    private String fieldKey;

    @Column(columnDefinition = "TEXT")
    private String fieldValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdeaDecisionState decisionState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdeaFieldProvenance provenance;

    public static IdeaBriefField userValue(
        IdeaBrief brief,
        String fieldKey,
        String value,
        IdeaDecisionState decisionState
    ) {
        return create(brief, fieldKey, value, decisionState, IdeaFieldProvenance.USER_CONFIRMED, false);
    }

    public static IdeaBriefField sourceValue(
        IdeaBrief brief,
        String fieldKey,
        String value,
        IdeaDecisionState decisionState
    ) {
        return create(brief, fieldKey, value, decisionState, IdeaFieldProvenance.SOURCE_EXTRACTED, false);
    }

    public static IdeaBriefField aiProposal(
        IdeaBrief brief,
        String fieldKey,
        String value,
        IdeaDecisionState decisionState,
        IdeaFieldProvenance provenance
    ) {
        return create(brief, fieldKey, value, decisionState, provenance, true);
    }

    private static IdeaBriefField create(
        IdeaBrief brief,
        String fieldKey,
        String value,
        IdeaDecisionState decisionState,
        IdeaFieldProvenance provenance,
        boolean aiAuthored
    ) {
        brief.requireMutable();
        if (fieldKey == null || fieldKey.isBlank() || fieldKey.length() > 80) {
            throw new IllegalArgumentException("field key is invalid");
        }
        if (aiAuthored && (decisionState == IdeaDecisionState.LOCKED || provenance == IdeaFieldProvenance.USER_CONFIRMED)) {
            throw new IllegalArgumentException("AI cannot lock or user-confirm a field");
        }
        IdeaBriefField field = new IdeaBriefField();
        field.brief = brief;
        field.fieldKey = fieldKey;
        field.fieldValue = value;
        field.decisionState = decisionState;
        field.provenance = provenance;
        return field;
    }

    public IdeaBriefField copyTo(IdeaBrief target) {
        return create(target, fieldKey, fieldValue, decisionState, provenance, false);
    }

    public void updateByUser(String value, IdeaDecisionState decisionState) {
        brief.requireMutable();
        this.fieldValue = value;
        this.decisionState = decisionState;
        this.provenance = IdeaFieldProvenance.USER_CONFIRMED;
    }
}
