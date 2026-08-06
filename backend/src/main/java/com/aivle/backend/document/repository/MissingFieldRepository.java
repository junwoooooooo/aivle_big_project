package com.aivle.backend.document.repository;

import com.aivle.backend.document.entity.MissingField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MissingFieldRepository extends JpaRepository<MissingField, Long> {
    List<MissingField> findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(
        Long structuredPlanId
    );

    Optional<MissingField> findByIdAndStructuredPlanIdAndDeletedAtIsNull(
        Long id,
        Long structuredPlanId
    );
}
