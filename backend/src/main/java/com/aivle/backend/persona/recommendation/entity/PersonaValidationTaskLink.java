package com.aivle.backend.persona.recommendation.entity;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityValidationTask;
import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "persona_validation_task_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaValidationTaskLink extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private PersonaRecommendation recommendation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feasibility_validation_task_id", nullable = false)
    private FeasibilityValidationTask feasibilityValidationTask;

    public static PersonaValidationTaskLink create(
        PersonaRecommendation recommendation, FeasibilityValidationTask task
    ) {
        PersonaValidationTaskLink value = new PersonaValidationTaskLink();
        value.recommendation = recommendation;
        value.feasibilityValidationTask = task;
        return value;
    }
}
