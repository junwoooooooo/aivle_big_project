package com.aivle.backend.persona.recommendation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Entity
@Table(name = "customer_hypotheses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerHypothesis extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private PersonaRecommendation recommendation;
    @Column(nullable = false, length = 100) private String personaCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private HypothesisType hypothesisType;
    @Column(nullable = false, columnDefinition = "TEXT") private String statement;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private HypothesisSourceType sourceType;
    @Column(length = 200) private String sourceReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PersonaConfidence confidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ValidationPriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private HypothesisValidationStatus validationStatus;

    public static CustomerHypothesis open(
        PersonaRecommendation recommendation, String personaCode, HypothesisType type,
        String statement, String rationale, HypothesisSourceType sourceType,
        String sourceReference, PersonaConfidence confidence, ValidationPriority priority
    ) {
        CustomerHypothesis value = new CustomerHypothesis();
        value.recommendation = recommendation;
        value.personaCode = personaCode;
        value.hypothesisType = type;
        value.statement = statement;
        value.rationale = rationale;
        value.sourceType = sourceType;
        value.sourceReference = sourceReference;
        value.confidence = confidence;
        value.priority = priority;
        value.validationStatus = HypothesisValidationStatus.OPEN;
        return value;
    }
}
