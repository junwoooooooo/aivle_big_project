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
        return create(brief, fieldKey, value, decisionState, IdeaFieldProvenance.USER_INPUT, false);
    }

    public static IdeaBriefField sourceValue(
        IdeaBrief brief,
        String fieldKey,
        String value,
        IdeaDecisionState decisionState
    ) {
        return create(brief, fieldKey, value, decisionState, IdeaFieldProvenance.AI_DERIVED, false);
    }

    public static IdeaBriefField userAnswer(IdeaBrief brief, String fieldKey, String value,
            IdeaDecisionState decisionState, boolean undecided) {
        return create(brief, fieldKey, value, decisionState,
            undecided ? IdeaFieldProvenance.MISSING : IdeaFieldProvenance.USER_INPUT, false);
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
        IdeaBriefFieldCatalog.require(fieldKey);
        if (decisionState == null || provenance == null) throw new IllegalArgumentException("field metadata is required");
        if (aiAuthored && (decisionState == IdeaDecisionState.LOCKED || isUserSource(provenance))) {
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
        if (decisionState == null) throw new IllegalArgumentException("decision state is required");
        this.fieldValue = value;
        this.decisionState = decisionState;
        this.provenance = IdeaFieldProvenance.USER_INPUT;
    }

    public static IdeaBriefField confirmedCommitment(IdeaBrief brief, String fieldKey, String value) {
        return create(brief, fieldKey, value, IdeaDecisionState.LOCKED,
            IdeaFieldProvenance.USER_CONFIRMED, false);
    }

    public void confirmCommitment(String value) {
        brief.requireMutable();
        if (value == null || value.isBlank()) throw new IllegalArgumentException("confirmed commitment value is required");
        if (provenance == IdeaFieldProvenance.USER_INPUT && decisionState == IdeaDecisionState.LOCKED) return;
        this.fieldValue = value.trim();
        this.decisionState = IdeaDecisionState.LOCKED;
        this.provenance = IdeaFieldProvenance.USER_CONFIRMED;
    }

    public void returnCommitmentToOpen() {
        brief.requireMutable();
        if (provenance != IdeaFieldProvenance.USER_CONFIRMED) return;
        this.fieldValue = "";
        this.decisionState = IdeaDecisionState.OPEN;
        this.provenance = IdeaFieldProvenance.MISSING;
    }

    public void updateFromAnswer(String value, IdeaDecisionState decisionState, boolean undecided) {
        brief.requireMutable();
        if (decisionState == null) throw new IllegalArgumentException("decision state is required");
        this.fieldValue = value;
        this.decisionState = decisionState;
        this.provenance = undecided ? IdeaFieldProvenance.MISSING : IdeaFieldProvenance.USER_INPUT;
    }

    public void applyAi(String value, IdeaDecisionState decisionState, IdeaFieldProvenance provenance) {
        brief.requireMutable();
        if (decisionState == IdeaDecisionState.LOCKED || isUserSource(provenance)) {
            throw new IllegalArgumentException("AI cannot lock or user-confirm a field");
        }
        if (this.decisionState == IdeaDecisionState.LOCKED || isUserSource(this.provenance)) {
            return;
        }
        this.fieldValue = value;
        this.decisionState = decisionState;
        this.provenance = provenance;
    }

    private static boolean isUserSource(IdeaFieldProvenance provenance) {
        return provenance == IdeaFieldProvenance.USER_INPUT
            || provenance == IdeaFieldProvenance.USER_CONFIRMED;
    }
}
