package com.aivle.backend.persona.recommendation.repository;

import com.aivle.backend.persona.recommendation.entity.CustomerValidationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerValidationPlanRepository
    extends JpaRepository<CustomerValidationPlan, Long> {
    List<CustomerValidationPlan>
        findByRecommendationIdAndDeletedAtIsNullOrderById(Long recommendationId);
}
