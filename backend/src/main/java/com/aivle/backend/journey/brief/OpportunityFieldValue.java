package com.aivle.backend.journey.brief;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.journey.conversation.IdeaAttachment;
import com.aivle.backend.journey.conversation.IdeaMessage;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "opportunity_field_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityFieldValue extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "brief_version_id", nullable = false) private OpportunityBriefVersion briefVersion;
    @Column(nullable = false, length = 100) private String fieldKey;
    @Column(columnDefinition = "TEXT") private String valueJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FieldDecisionStatus decisionStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private FieldSourceType sourceType;
    @Column(length = 500) private String sourceReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_message_id") private IdeaMessage sourceMessage;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_attachment_id") private IdeaAttachment sourceAttachment;
    @Column(precision = 5, scale = 4) private BigDecimal confidence;
    @Column(nullable = false) private boolean userConfirmed;
    private LocalDateTime confirmedAt;

    public static OpportunityFieldValue create(OpportunityBriefVersion briefVersion, String fieldKey,
            String valueJson, FieldDecisionStatus decisionStatus, FieldSourceType sourceType,
            String sourceReference) {
        return create(briefVersion, fieldKey, valueJson, decisionStatus, sourceType,
            sourceReference, null, null, null, false, null);
    }

    public static OpportunityFieldValue create(OpportunityBriefVersion briefVersion, String fieldKey,
            String valueJson, FieldDecisionStatus decisionStatus, FieldSourceType sourceType,
            String sourceReference, IdeaMessage sourceMessage, IdeaAttachment sourceAttachment,
            Double confidence, boolean userConfirmed, LocalDateTime confirmedAt) {
        if (fieldKey == null || fieldKey.isBlank()) throw new IllegalArgumentException("field key is required");
        if (decisionStatus == null || sourceType == null) {
            throw new IllegalArgumentException("decision status and source type are required");
        }
        if (sourceType == FieldSourceType.MISSING && valueJson != null) {
            throw new IllegalArgumentException("missing field cannot contain a value");
        }
        if (sourceType != FieldSourceType.MISSING && (valueJson == null || valueJson.isBlank())) {
            throw new IllegalArgumentException("non-missing field value is required");
        }
        if (confidence != null && (confidence < 0 || confidence > 1)) throw new IllegalArgumentException("confidence is out of range");
        if (userConfirmed != (confirmedAt != null)) throw new IllegalArgumentException("confirmation time must match confirmation state");
        if (sourceMessage != null && !sourceMessage.getProject().getId().equals(briefVersion.getProject().getId())) throw new IllegalArgumentException("source message project mismatch");
        if (sourceAttachment != null && !sourceAttachment.getProject().getId().equals(briefVersion.getProject().getId())) throw new IllegalArgumentException("source attachment project mismatch");
        OpportunityFieldValue value = new OpportunityFieldValue();
        value.project = briefVersion.getProject();
        value.briefVersion = briefVersion;
        value.fieldKey = fieldKey;
        value.valueJson = valueJson;
        value.decisionStatus = decisionStatus;
        value.sourceType = sourceType;
        value.sourceReference = sourceReference;
        value.sourceMessage = sourceMessage;
        value.sourceAttachment = sourceAttachment;
        value.confidence = confidence == null ? null : BigDecimal.valueOf(confidence);
        value.userConfirmed = userConfirmed;
        value.confirmedAt = confirmedAt;
        return value;
    }
}
