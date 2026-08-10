package com.aivle.backend.pipeline.idea.repository;

import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaBriefFieldRepository extends JpaRepository<IdeaBriefField, Long> {
    List<IdeaBriefField> findAllByBriefIdOrderById(String briefId);
    Optional<IdeaBriefField> findByBriefIdAndFieldKey(String briefId, String fieldKey);
}
