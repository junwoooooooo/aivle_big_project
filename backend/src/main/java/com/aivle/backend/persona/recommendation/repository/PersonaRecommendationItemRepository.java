package com.aivle.backend.persona.recommendation.repository;

import com.aivle.backend.persona.recommendation.entity.PersonaRecommendationItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonaRecommendationItemRepository
    extends JpaRepository<PersonaRecommendationItem, Long> {
    @EntityGraph(attributePaths = {"baselinePersona"})
    List<PersonaRecommendationItem>
        findByRecommendationIdAndDeletedAtIsNullOrderByRank(Long recommendationId);
}
