package com.aivle.backend.journey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ShortlistDecisionRepository extends JpaRepository<ShortlistDecision, Long> {
    Optional<ShortlistDecision> findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId, Long ideaVersionId);
}
