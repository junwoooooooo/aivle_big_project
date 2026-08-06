package com.aivle.backend.persona.recommendation.repository;

import com.aivle.backend.persona.recommendation.entity.PersonaValidationTaskLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonaValidationTaskLinkRepository
    extends JpaRepository<PersonaValidationTaskLink, Long> {
    @EntityGraph(attributePaths = {"feasibilityValidationTask"})
    List<PersonaValidationTaskLink>
        findByRecommendationIdAndDeletedAtIsNullOrderById(Long recommendationId);
}
