package com.aivle.backend.journey;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdeaClarificationQuestionRepository extends JpaRepository<IdeaClarificationQuestion, Long> {
    @EntityGraph(attributePaths = {"project", "originDraftVersion", "originDraftVersion.source"})
    List<IdeaClarificationQuestion> findByOriginDraftVersionIdAndDeletedAtIsNullOrderById(Long originDraftVersionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project", "originDraftVersion", "originDraftVersion.source"})
    @Query("select q from IdeaClarificationQuestion q where q.id=:id and q.deletedAt is null")
    Optional<IdeaClarificationQuestion> findLockedById(@Param("id") Long id);
}
