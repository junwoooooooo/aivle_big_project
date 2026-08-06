package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boundary_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoundaryRule extends BaseEntity {
    public enum RuleType {
        PROHIBITED_ROLE,
        PROHIBITED_ACTIVITY,
        ALLOWED_PATTERN,
        REQUIRED_CONTROL,
        REQUIRED_PARTNER,
        REQUIRED_DISCLOSURE,
        UNRESOLVED_FACT
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "boundary_version_id", nullable = false) private RegulatoryBoundaryVersion boundaryVersion;
    @Column(nullable = false, length = 100) private String ruleKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private RuleType ruleType;
    @Column(nullable = false, columnDefinition = "TEXT") private String statement;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Column(nullable = false, columnDefinition = "TEXT") private String affectedBriefFieldsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String evidenceIdsJson;
    @Column(length = 20) private String severity;
    @Column(nullable = false, columnDefinition = "TEXT") private String userActionOptionsJson;
    @Column(nullable = false, length = 100) private String structureKey;
    @Column(nullable = false, length = 300) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String normalizedRequirement;
    @Column(nullable = false, length = 30) private String sourceStatus;
    @Column(nullable = false, columnDefinition = "TEXT") private String appliesWhenJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String userFacingReason;
    @Column(nullable = false, columnDefinition = "TEXT") private String alternativesJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String requiredQualificationsJson;
    @Column(columnDefinition = "TEXT") private String requiredPartnerRole;
    @Column(columnDefinition = "TEXT") private String requiredDisclosure;
    @Column(nullable = false) private boolean professionalReviewRecommended;

    public static BoundaryRule create(RegulatoryBoundaryVersion boundaryVersion, String ruleKey,
            RuleType ruleType, String statement, String rationale, String affectedBriefFieldsJson,
            String evidenceIdsJson, String severity, String userActionOptionsJson) {
        requireText(ruleKey, "rule key");
        requireText(statement, "statement");
        requireText(rationale, "rationale");
        requireText(affectedBriefFieldsJson, "affected brief fields");
        requireText(evidenceIdsJson, "evidence ids");
        requireText(userActionOptionsJson, "user action options");
        return create(boundaryVersion, ruleKey, ruleType, ruleKey, ruleKey, rationale, statement,
            affectedBriefFieldsJson, evidenceIdsJson, severity, "WARNING", "{}", rationale,
            "[]", "[]", null, null, false, userActionOptionsJson);
    }

    public static BoundaryRule create(RegulatoryBoundaryVersion boundaryVersion, String ruleKey,
            RuleType ruleType, String structureKey, String title, String description,
            String normalizedRequirement, String affectedBriefFieldsJson, String evidenceIdsJson,
            String severity, String sourceStatus, String appliesWhenJson, String userFacingReason,
            String alternativesJson, String requiredQualificationsJson, String requiredPartnerRole,
            String requiredDisclosure, boolean professionalReviewRecommended, String userActionOptionsJson) {
        for (String[] required : new String[][] {{ruleKey,"rule key"},{structureKey,"structure key"},
                {title,"title"},{description,"description"},{normalizedRequirement,"normalized requirement"},
                {affectedBriefFieldsJson,"affected fields"},{evidenceIdsJson,"evidence ids"},
                {sourceStatus,"source status"},{appliesWhenJson,"applies when"},
                {userFacingReason,"user facing reason"},{alternativesJson,"alternatives"},
                {requiredQualificationsJson,"qualifications"},{userActionOptionsJson,"user actions"}}) {
            requireText(required[0], required[1]);
        }
        BoundaryRule value = new BoundaryRule();
        value.project = boundaryVersion.getProject();
        value.boundaryVersion = boundaryVersion;
        value.ruleKey = ruleKey;
        value.ruleType = ruleType;
        value.statement = normalizedRequirement;
        value.rationale = description;
        value.affectedBriefFieldsJson = affectedBriefFieldsJson;
        value.evidenceIdsJson = evidenceIdsJson;
        value.severity = severity;
        value.userActionOptionsJson = userActionOptionsJson;
        value.structureKey = structureKey;
        value.title = title;
        value.description = description;
        value.normalizedRequirement = normalizedRequirement;
        value.sourceStatus = sourceStatus;
        value.appliesWhenJson = appliesWhenJson;
        value.userFacingReason = userFacingReason;
        value.alternativesJson = alternativesJson;
        value.requiredQualificationsJson = requiredQualificationsJson;
        value.requiredPartnerRole = requiredPartnerRole;
        value.requiredDisclosure = requiredDisclosure;
        value.professionalReviewRecommended = professionalReviewRecommended;
        return value;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
