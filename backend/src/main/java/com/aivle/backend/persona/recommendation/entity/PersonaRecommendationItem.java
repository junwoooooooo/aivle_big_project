package com.aivle.backend.persona.recommendation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Entity
@Table(name = "persona_recommendation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaRecommendationItem extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private PersonaRecommendation recommendation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_persona_id", nullable = false)
    private BaselinePersona baselinePersona;
    @Column(name = "recommendation_rank", nullable = false) private Integer rank;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private RecommendationLevel recommendationLevel;
    private Integer fitScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PersonaConfidence confidence;
    @Column(nullable = false, columnDefinition = "TEXT") private String matchReasonsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String mismatchRisksJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String evidenceJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String verificationQuestionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String interpretation;

    public static PersonaRecommendationItem create(
        PersonaRecommendation recommendation, BaselinePersona persona, int rank,
        RecommendationLevel level, Integer fitScore, PersonaConfidence confidence,
        String matchReasonsJson, String mismatchRisksJson, String assumptionsJson,
        String evidenceJson, String verificationQuestionsJson, String interpretation
    ) {
        PersonaRecommendationItem value = new PersonaRecommendationItem();
        value.recommendation = recommendation;
        value.baselinePersona = persona;
        value.rank = rank;
        value.recommendationLevel = level;
        value.fitScore = fitScore;
        value.confidence = confidence;
        value.matchReasonsJson = matchReasonsJson;
        value.mismatchRisksJson = mismatchRisksJson;
        value.assumptionsJson = assumptionsJson;
        value.evidenceJson = evidenceJson;
        value.verificationQuestionsJson = verificationQuestionsJson;
        value.interpretation = interpretation;
        return value;
    }
}
