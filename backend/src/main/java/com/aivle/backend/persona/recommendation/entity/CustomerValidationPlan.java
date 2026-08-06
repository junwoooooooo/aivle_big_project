package com.aivle.backend.persona.recommendation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Entity
@Table(name = "customer_validation_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerValidationPlan extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private PersonaRecommendation recommendation;
    @Column(nullable = false, length = 100) private String personaCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ValidationMethod method;
    @Column(nullable = false, columnDefinition = "TEXT") private String objective;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String targetParticipantDescription;
    private Integer suggestedSampleSize;
    @Column(nullable = false, columnDefinition = "TEXT") private String recruitmentChannel;
    @Column(nullable = false, columnDefinition = "TEXT") private String successCriteriaJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String expectedEvidenceJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String interviewQuestionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String surveyQuestionsJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String linkedFeasibilityTaskIdsJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ValidationPriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ValidationPlanStatus status;
    @Column(nullable = false, columnDefinition = "TEXT") private String disclaimer;

    public static CustomerValidationPlan draft(
        PersonaRecommendation recommendation, String personaCode, ValidationMethod method,
        String objective, String targetParticipantDescription, Integer suggestedSampleSize,
        String recruitmentChannel, String successCriteriaJson, String expectedEvidenceJson,
        String interviewQuestionsJson, String surveyQuestionsJson,
        String linkedTaskIdsJson, ValidationPriority priority, String disclaimer
    ) {
        CustomerValidationPlan value = new CustomerValidationPlan();
        value.recommendation = recommendation;
        value.personaCode = personaCode;
        value.method = method;
        value.objective = objective;
        value.targetParticipantDescription = targetParticipantDescription;
        value.suggestedSampleSize = suggestedSampleSize;
        value.recruitmentChannel = recruitmentChannel;
        value.successCriteriaJson = successCriteriaJson;
        value.expectedEvidenceJson = expectedEvidenceJson;
        value.interviewQuestionsJson = interviewQuestionsJson;
        value.surveyQuestionsJson = surveyQuestionsJson;
        value.linkedFeasibilityTaskIdsJson = linkedTaskIdsJson;
        value.priority = priority;
        value.status = ValidationPlanStatus.DRAFT;
        value.disclaimer = disclaimer;
        return value;
    }
}
