package com.aivle.backend.persona.recommendation.repository;

import com.aivle.backend.persona.recommendation.entity.CustomerHypothesis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerHypothesisRepository extends JpaRepository<CustomerHypothesis, Long> {
    List<CustomerHypothesis>
        findByRecommendationIdAndDeletedAtIsNullOrderById(Long recommendationId);
}
