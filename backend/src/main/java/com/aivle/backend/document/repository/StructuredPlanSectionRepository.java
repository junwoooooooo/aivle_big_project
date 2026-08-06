package com.aivle.backend.document.repository;

import com.aivle.backend.document.entity.StructuredPlanSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StructuredPlanSectionRepository
    extends JpaRepository<StructuredPlanSection, Long> {
    List<StructuredPlanSection> findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(
        Long structuredPlanId
    );
}
